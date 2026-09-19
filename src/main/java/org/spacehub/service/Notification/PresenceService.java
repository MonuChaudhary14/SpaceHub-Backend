package org.spacehub.service.Notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spacehub.DTO.presence.OnlineUsersDTO;
import org.spacehub.DTO.presence.UserPresenceDTO;
import org.spacehub.service.Interface.IPresenceService;
import org.spacehub.service.WebSocket.WsRedisPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class PresenceService implements IPresenceService {

  private static final Logger logger = LoggerFactory.getLogger(PresenceService.class);

  public static final String PRESENCE_GLOBAL_KEY = "presence:active:global";
  public static final String PRESENCE_COMMUNITY_KEY_PREFIX = "presence:community:";
  public static final String PRESENCE_STATUS_KEY_PREFIX = "presence:status:";

  public static final long HEARTBEAT_TIMEOUT_MS = 60_000L;
  public static final long IDLE_TIMEOUT_MS = 300_000L;

  private final RedisTemplate<String, Object> redisTemplate;
  private final SimpMessagingTemplate messagingTemplate;
  private final WsRedisPublisher wsRedisPublisher;
  private final ObjectMapper objectMapper;

  private final Map<String, PresenceSession> localSessions = new ConcurrentHashMap<>();
  private final Map<String, Long> localFallbackHeartbeats = new ConcurrentHashMap<>();

  public record PresenceSession(Long communityId, String email) {
  }

  @Override
  public void userConnected(String sessionId, Long communityId, String email) {
    if (email == null || email.isBlank()) {
      return;
    }
    String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
    localSessions.put(sessionId, new PresenceSession(communityId, normalizedEmail));
    recordHeartbeat(normalizedEmail, communityId);
    broadcastOnlineUsers(communityId);
  }

  @Override
  public void userDisconnected(String sessionId) {
    PresenceSession ps = localSessions.remove(sessionId);
    if (ps == null) {
      return;
    }

    Long communityId = ps.communityId();
    String email = ps.email();

    boolean hasOtherSessions = localSessions.values().stream()
      .anyMatch(s -> Objects.equals(s.email(), email));

    if (!hasOtherSessions) {
      setUserOffline(email, communityId);
    }
    if (communityId != null) {
      broadcastOnlineUsers(communityId);
    }
  }

  @Override
  public void userLeft(String sessionId) {
    userDisconnected(sessionId);
  }

  @Override
  public void recordHeartbeat(String email, Long communityId) {
    if (email == null || email.isBlank()) {
      return;
    }
    String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
    long now = System.currentTimeMillis();

    try {
      redisTemplate.opsForZSet().add(PRESENCE_GLOBAL_KEY, normalizedEmail, now);
      if (communityId != null) {
        redisTemplate.opsForZSet().add(PRESENCE_COMMUNITY_KEY_PREFIX + communityId, normalizedEmail, now);
      }
      UserPresenceDTO dto = UserPresenceDTO.builder()
        .email(normalizedEmail)
        .status("ONLINE")
        .lastActiveEpochMs(now)
        .communityId(communityId)
        .build();
      redisTemplate.opsForValue().set(PRESENCE_STATUS_KEY_PREFIX + normalizedEmail, objectMapper.writeValueAsString(dto));
    } catch (Exception e) {
      logger.warn("Redis unavailable for heartbeat, updating local fallback: {}", e.getMessage());
      localFallbackHeartbeats.put(normalizedEmail, now);
    }
  }

  @Override
  public boolean isUserOnline(String email) {
    if (email == null || email.isBlank()) {
      return false;
    }
    String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
    long now = System.currentTimeMillis();

    try {
      Double score = redisTemplate.opsForZSet().score(PRESENCE_GLOBAL_KEY, normalizedEmail);
      if (score != null) {
        return (now - score.longValue()) < HEARTBEAT_TIMEOUT_MS;
      }
    } catch (Exception e) {
      logger.warn("Redis unavailable checking online status: {}", e.getMessage());
    }

    Long localLastActive = localFallbackHeartbeats.get(normalizedEmail);
    return localLastActive != null && (now - localLastActive) < HEARTBEAT_TIMEOUT_MS;
  }

  @Override
  public List<String> getCommunityOnlineUsers(Long communityId) {
    if (communityId == null) {
      return Collections.emptyList();
    }
    long threshold = System.currentTimeMillis() - HEARTBEAT_TIMEOUT_MS;

    try {
      Set<Object> members = redisTemplate.opsForZSet().rangeByScore(
        PRESENCE_COMMUNITY_KEY_PREFIX + communityId,
        threshold,
        Double.POSITIVE_INFINITY
      );
      if (members != null && !members.isEmpty()) {
        List<String> list = new ArrayList<>(members.size());
        for (Object m : members) {
          list.add(String.valueOf(m));
        }
        return list;
      }
    } catch (Exception e) {
      logger.warn("Redis unavailable fetching community online users: {}", e.getMessage());
    }

    List<String> fallbackList = new ArrayList<>();
    localSessions.values().stream()
      .filter(s -> Objects.equals(s.communityId(), communityId))
      .forEach(s -> {
        if (!fallbackList.contains(s.email())) {
          fallbackList.add(s.email());
        }
      });
    return fallbackList;
  }

  @Override
  public Map<String, String> getUsersPresence(List<String> emails) {
    if (emails == null || emails.isEmpty()) {
      return Collections.emptyMap();
    }
    Map<String, String> result = new HashMap<>();
    long now = System.currentTimeMillis();

    for (String email : emails) {
      if (email != null && !email.isBlank()) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        result.put(normalizedEmail, resolveUserStatus(normalizedEmail, now));
      }
    }
    return result;
  }

  private String resolveUserStatus(String normalizedEmail, long now) {
    try {
      Double score = redisTemplate.opsForZSet().score(PRESENCE_GLOBAL_KEY, normalizedEmail);
      if (score != null) {
        long diff = now - score.longValue();
        if (diff < HEARTBEAT_TIMEOUT_MS) {
          return "ONLINE";
        }
        if (diff < IDLE_TIMEOUT_MS) {
          return "IDLE";
        }
        return "OFFLINE";
      }
    } catch (Exception ignored) {
    }

    Long localLast = localFallbackHeartbeats.get(normalizedEmail);
    if (localLast != null && (now - localLast) < HEARTBEAT_TIMEOUT_MS) {
      return "ONLINE";
    }
    return "OFFLINE";
  }

  @Override
  public UserPresenceDTO getUserPresence(String email) {
    if (email == null || email.isBlank()) {
      return UserPresenceDTO.builder().status("OFFLINE").build();
    }
    String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

    try {
      Object raw = redisTemplate.opsForValue().get(PRESENCE_STATUS_KEY_PREFIX + normalizedEmail);
      if (raw != null) {
        return objectMapper.readValue(String.valueOf(raw), UserPresenceDTO.class);
      }
    } catch (Exception ignored) {
    }

    boolean online = isUserOnline(normalizedEmail);
    return UserPresenceDTO.builder()
      .email(normalizedEmail)
      .status(online ? "ONLINE" : "OFFLINE")
      .lastActiveEpochMs(localFallbackHeartbeats.getOrDefault(normalizedEmail, 0L))
      .build();
  }

  @Override
  public void setUserOffline(String email, Long communityId) {
    if (email == null || email.isBlank()) {
      return;
    }
    String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
    long now = System.currentTimeMillis();

    try {
      redisTemplate.opsForZSet().remove(PRESENCE_GLOBAL_KEY, normalizedEmail);
      if (communityId != null) {
        redisTemplate.opsForZSet().remove(PRESENCE_COMMUNITY_KEY_PREFIX + communityId, normalizedEmail);
      }
      UserPresenceDTO dto = UserPresenceDTO.builder()
        .email(normalizedEmail)
        .status("OFFLINE")
        .lastActiveEpochMs(now)
        .communityId(communityId)
        .build();
      redisTemplate.opsForValue().set(PRESENCE_STATUS_KEY_PREFIX + normalizedEmail, objectMapper.writeValueAsString(dto));
      wsRedisPublisher.publishPresenceDelta(normalizedEmail, "OFFLINE", communityId, dto);
    } catch (Exception e) {
      logger.warn("Redis error marking user offline: {}", e.getMessage());
    }

    localFallbackHeartbeats.remove(normalizedEmail);
  }

  @Scheduled(fixedRate = 10000)
  public void sweepExpiredPresence() {
    long now = System.currentTimeMillis();
    long expirationThreshold = now - HEARTBEAT_TIMEOUT_MS;

    try {
      Set<Object> expiredUsers = redisTemplate.opsForZSet().rangeByScore(
        PRESENCE_GLOBAL_KEY,
        0,
        expirationThreshold
      );

      if (expiredUsers != null && !expiredUsers.isEmpty()) {
        logger.debug("Sweeper detected {} expired presence sessions", expiredUsers.size());
        redisTemplate.opsForZSet().removeRangeByScore(PRESENCE_GLOBAL_KEY, 0, expirationThreshold);

        for (Object userObj : expiredUsers) {
          String userEmail = String.valueOf(userObj);
          UserPresenceDTO dto = UserPresenceDTO.builder()
            .email(userEmail)
            .status("OFFLINE")
            .lastActiveEpochMs(now)
            .build();
          redisTemplate.opsForValue().set(PRESENCE_STATUS_KEY_PREFIX + userEmail, objectMapper.writeValueAsString(dto));
          wsRedisPublisher.publishPresenceDelta(userEmail, "OFFLINE", null, dto);
        }
      }
    } catch (Exception e) {
      logger.debug("Error during presence sweep: {}", e.getMessage());
    }

    localFallbackHeartbeats.entrySet().removeIf(entry -> (now - entry.getValue()) > HEARTBEAT_TIMEOUT_MS);
  }

  private void broadcastOnlineUsers(Long communityId) {
    if (communityId == null) {
      return;
    }
    List<String> emails = getCommunityOnlineUsers(communityId);
    OnlineUsersDTO dto = new OnlineUsersDTO(communityId, emails);
    messagingTemplate.convertAndSend("/topic/community." + communityId + ".online", dto);
  }

}

package org.spacehub.service.Notification;

import lombok.RequiredArgsConstructor;
import org.spacehub.service.Interface.IPresenceService;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.spacehub.DTO.presence.OnlineUsersDTO;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
@RequiredArgsConstructor
public class PresenceService implements IPresenceService {

  private final SimpMessagingTemplate messagingTemplate;
  private final ConcurrentMap<Long, ConcurrentMap<String, Integer>> online = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, PresenceSession> sessions = new ConcurrentHashMap<>();

  public record PresenceSession(Long communityId, String email) {}

  public void userConnected(String sessionId, Long communityId, String email) {
    sessions.put(sessionId, new PresenceSession(communityId, email));

    ConcurrentHashMap<String, Integer> emailMap =
      (ConcurrentHashMap<String, Integer>) online.computeIfAbsent(communityId, k -> new ConcurrentHashMap<>());
    if (emailMap.containsKey(email)) {
      emailMap.put(email, emailMap.get(email) + 1);
    } else {
      emailMap.put(email, 1);
    }

    broadcastOnlineUsers(communityId);
  }

  public void userDisconnected(String sessionId) {
    PresenceSession ps = sessions.remove(sessionId);
    if (ps == null) {
      return;
    }

    Long communityId = ps.communityId();
    String email = ps.email();

    ConcurrentMap<String, Integer> communityMap = online.get(communityId);
    if (communityMap == null) {
      return;
    }

    communityMap.computeIfPresent(email, (k, v) -> {
      if (v <= 1) {
        return null;
      }
      return v - 1;
    });

    if (communityMap.isEmpty()) online.remove(communityId);

    broadcastOnlineUsers(communityId);
  }

  public void userLeft(String sessionId) {
    userDisconnected(sessionId);
  }

  private void broadcastOnlineUsers(Long communityId) {
    ConcurrentMap<String, Integer> communityMap = online.get(communityId);
    List<String> emails;
    if (communityMap == null) {
      emails = Collections.emptyList();
    } else {
      emails = new ArrayList<>(communityMap.keySet());
    }
    OnlineUsersDTO dto = new OnlineUsersDTO(communityId, emails);
    messagingTemplate.convertAndSend("/topic/community." + communityId + ".online", dto);
  }

}

package org.spacehub.service.VoiceRoom;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class LiveKitTokenService {

  @Getter
  @Value("${livekit.url:ws://localhost:7880}")
  private String livekitUrl;

  @Value("${livekit.api-key:devkey}")
  private String apiKey;

  @Value("${livekit.api-secret:secret123456789012345678901234567890}")
  private String apiSecret;

  @Value("${livekit.token-ttl-seconds:86400}")
  private long tokenTtlSeconds;

  public String createToken(String roomName, String identity, String displayName) {
    long nowMillis = System.currentTimeMillis();
    Date now = new Date(nowMillis);
    Date expiry = new Date(nowMillis + (tokenTtlSeconds * 1000));

    boolean isVideo = roomName != null && roomName.startsWith("vid_");

    Map<String, Object> videoGrant = new HashMap<>();
    videoGrant.put("room", roomName);
    videoGrant.put("roomJoin", true);
    videoGrant.put("canPublish", true);
    videoGrant.put("canSubscribe", true);
    videoGrant.put("canPublishData", true);
    if (!isVideo) {
      videoGrant.put("canPublishSources", java.util.List.of("microphone", "screen_share", "screen_share_audio"));
    } else {
      videoGrant.put("canPublishSources", java.util.List.of("camera", "microphone", "screen_share", "screen_share_audio"));
    }

    byte[] secretBytes = apiSecret.getBytes(StandardCharsets.UTF_8);
    SecretKey key = Keys.hmacShaKeyFor(secretBytes);

    return Jwts.builder()
      .issuer(apiKey)
      .subject(identity)
      .issuedAt(now)
      .notBefore(now)
      .expiration(expiry)
      .claim("name", displayName != null && !displayName.isBlank() ? displayName : identity)
      .claim("video", videoGrant)
      .signWith(key)
      .compact();
  }

}

package org.spacehub.service.serviceAuth;

import lombok.Getter;
import org.spacehub.service.serviceAuth.authInterfaces.IRedisService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Getter
@Service
public class RedisService implements IRedisService {

  private final StringRedisTemplate redisTemplate;

  public RedisService(StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  public void saveValue(String key, String value, long ttlSeconds) {
    redisTemplate.opsForValue().set(key, value, Duration.ofSeconds(ttlSeconds));
  }

  public String getValue(String key) {
    return redisTemplate.opsForValue().get(key);
  }

  public void deleteValue(String key) {
    redisTemplate.delete(key);
  }

  public boolean exists(String key) {
    return redisTemplate.hasKey(key);
  }

  public Long getLiveTime(String key) {
    Long liveTime = redisTemplate.getExpire(key, TimeUnit.SECONDS);
    if (liveTime < 0) {
      return 0L;
    }
    return liveTime;
  }

  public Long incrementValue(String key) {
    return redisTemplate.opsForValue().increment(key);
  }

  public void setExpiry(String key, long seconds) {
    redisTemplate.expire(key, Duration.ofSeconds(seconds));
  }

}

package org.spacehub.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.redisson.Bucket4jRedisson;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.spacehub.service.Interface.IRateLimitService;
import org.springframework.stereotype.Service;
import java.time.Duration;

@Service
public class RateLimitService implements IRateLimitService {

  private final ProxyManager<String> proxyManager;

  public RateLimitService(RedissonClient redissonClient) {
    this.proxyManager = Bucket4jRedisson
      .casBasedBuilder(((Redisson) redissonClient).getCommandExecutor())
      .build();
  }

  public boolean tryConsume(String key) {
    Bucket bucket = proxyManager.getProxy(key, this::createNewBucketConfig);
    return bucket.tryConsume(1);
  }

  private BucketConfiguration createNewBucketConfig() {
    Bandwidth limit = Bandwidth.builder()
      .capacity(120)
      .refillGreedy(120, Duration.ofMinutes(1))
      .build();

    return BucketConfiguration.builder()
      .addLimit(limit)
      .build();
  }
}

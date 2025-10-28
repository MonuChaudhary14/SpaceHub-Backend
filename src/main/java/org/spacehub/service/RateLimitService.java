package org.spacehub.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.Refill;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.redisson.cas.RedissonBasedProxyManager;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.command.CommandAsyncExecutor;
import org.springframework.stereotype.Service;
import java.time.Duration;

@Service
public class RateLimitService {

  private final ProxyManager<String> proxyManager;

  public RateLimitService(RedissonClient redissonClient) {
    CommandAsyncExecutor commandExecutor = ((Redisson) redissonClient).getCommandExecutor();
    this.proxyManager = RedissonBasedProxyManager.builderFor(commandExecutor).build();
  }

  public boolean tryConsume(String key) {
    Bucket bucket = proxyManager.getProxy(key, this::createNewBucketConfig);
    return bucket.tryConsume(1);
  }

  private BucketConfiguration createNewBucketConfig() {
    Refill refill = Refill.intervally(10, Duration.ofMinutes(1));
    Bandwidth limit = Bandwidth.classic(10, refill);

    return BucketConfiguration.builder()
      .addLimit(limit)
      .build();
  }
}

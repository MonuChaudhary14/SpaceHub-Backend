package org.spacehub.service.Interface;

public interface IRateLimitService {

  boolean tryConsume(String key);

}

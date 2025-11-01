package org.spacehub.service.service_auth.authInterfaces;

public interface IRedisService {

  void saveValue(String key, String value, long ttlSeconds);

  String getValue(String key);

  void deleteValue(String key);

  boolean exists(String key);

  Long getLiveTime(String key);

  Long incrementValue(String key);

  void setExpiry(String key, long seconds);

}

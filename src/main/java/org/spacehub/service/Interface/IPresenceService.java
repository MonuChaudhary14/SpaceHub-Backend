package org.spacehub.service.Interface;

public interface IPresenceService {

  void userConnected(String sessionId, Long communityId, String email);

  void userDisconnected(String sessionId);

  void userLeft(String sessionId);

}


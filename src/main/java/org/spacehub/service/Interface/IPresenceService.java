package org.spacehub.service.Interface;

import org.spacehub.DTO.presence.UserPresenceDTO;

import java.util.List;
import java.util.Map;

public interface IPresenceService {

  void userConnected(String sessionId, Long communityId, String email);

  void userDisconnected(String sessionId);

  void userLeft(String sessionId);

  void recordHeartbeat(String email, Long communityId);

  boolean isUserOnline(String email);

  List<String> getCommunityOnlineUsers(Long communityId);

  Map<String, String> getUsersPresence(List<String> emails);

  UserPresenceDTO getUserPresence(String email);

  void setUserOffline(String email, Long communityId);

}

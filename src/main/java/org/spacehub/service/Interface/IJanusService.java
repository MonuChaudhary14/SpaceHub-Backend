package org.spacehub.service.Interface;

import com.fasterxml.jackson.databind.JsonNode;

public interface IJanusService {

  String createSession();

  String attachAudioBridgePlugin(String sessionId);

  void createAudioRoom(String sessionId, String handleId, int roomId);

  JsonNode joinAudioRoom(String sessionId, String handleId, int roomId, String displayName);

  JsonNode sendOffer(String sessionId, String handleId, String sdpOffer);

  void sendIce(String sessionId, String handleId, Object candidate);

  void setMute(String sessionId, String handleId, boolean mute);

  JsonNode fetchSessionEvents(String sessionId);
}


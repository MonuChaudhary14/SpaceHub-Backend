package org.spacehub.service.chatRoom.chatroomInterfaces;

import org.spacehub.entities.ChatRoom.ChatPoll;

import java.util.List;
import java.util.Map;

public interface IChatPollService {

  ChatPoll createPoll(String roomCode, String userId, Map<String, Object> body);

  List<ChatPoll> getPollsForRoom(String roomCode);

  void vote(String roomCode, String userId, Map<String, Object> body);

}


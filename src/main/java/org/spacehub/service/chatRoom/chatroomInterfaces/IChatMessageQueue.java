package org.spacehub.service.chatRoom.chatroomInterfaces;

import org.spacehub.entities.ChatRoom.ChatMessage;
import org.spacehub.entities.ChatRoom.ChatRoom;
import java.util.List;

public interface IChatMessageQueue {

  void enqueue(ChatMessage message);

  @SuppressWarnings("unused")
  void sendEveryInterval();

  List<ChatMessage> getMessagesForRoom(ChatRoom room);
}

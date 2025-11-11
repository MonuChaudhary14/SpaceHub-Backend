package org.spacehub.service.chatRoom.chatroomInterfaces;

import org.spacehub.entities.ChatRoom.ChatMessage;
import org.spacehub.entities.ChatRoom.ChatRoom;
import org.spacehub.entities.ChatRoom.NewChatRoom;

import java.util.List;

public interface IChatMessageQueue {

  void enqueue(ChatMessage message);

  void flushQueue();

  boolean deleteMessage(Long messageId);

  List<ChatMessage> getMessagesForRoom(ChatRoom room);

  List<ChatMessage> getMessagesForNewChatRoom(NewChatRoom newChatRoom);
}

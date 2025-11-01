package org.spacehub.service.chatRoom.chatroomInterfaces;

import org.spacehub.entities.ChatRoom.ChatMessage;
import org.spacehub.entities.ChatRoom.ChatRoom;
import java.util.List;

public interface IChatMessageService {

  void saveAll(List<ChatMessage> messages);

  List<ChatMessage> getMessagesForRoom(ChatRoom room);
}


package org.spacehub.service.chatRoom.chatroomInterfaces;

import org.spacehub.entities.ChatRoom.ChatMessage;
import org.spacehub.entities.ChatRoom.ChatRoom;
import org.spacehub.entities.ChatRoom.NewChatRoom;

import java.util.List;
import java.util.Optional;

public interface IChatMessageService {

  List<ChatMessage> saveAll(List<ChatMessage> messages);

  List<ChatMessage> getMessagesForRoom(ChatRoom room);

  List<ChatMessage> getMessagesForNewChatRoom(NewChatRoom newChatRoom);

  Optional<ChatMessage> findById(Long id);

  Optional<ChatMessage> findByUuid(String messageUuid);

  boolean deleteMessageByUuid(String messageUuid);
}


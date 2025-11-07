package org.spacehub.service.chatRoom;

import org.spacehub.entities.ChatRoom.ChatMessage;
import org.spacehub.entities.ChatRoom.ChatRoom;
import org.spacehub.entities.ChatRoom.NewChatRoom;
import org.spacehub.repository.ChatRoom.ChatMessageRepository;
import org.spacehub.service.chatRoom.chatroomInterfaces.IChatMessageService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ChatMessageService implements IChatMessageService {

  private final ChatMessageRepository chatMessageRepository;

  public ChatMessageService(ChatMessageRepository chatMessageRepository) {
    this.chatMessageRepository = chatMessageRepository;
  }

  public void saveAll(List<ChatMessage> messages) {
    chatMessageRepository.saveAll(messages);
  }

  public List<ChatMessage> getMessagesForRoom(ChatRoom room) {
    return chatMessageRepository.findByRoomOrderByTimestampAsc(room);
  }

  public List<ChatMessage> getMessagesForNewChatRoom(NewChatRoom newChatRoom) {
    return chatMessageRepository.findByNewChatRoomOrderByTimestampAsc(newChatRoom);
  }

}

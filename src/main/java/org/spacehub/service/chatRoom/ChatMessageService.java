package org.spacehub.service.chatRoom;

import org.spacehub.entities.ChatRoom.ChatMessage;
import org.spacehub.entities.ChatRoom.ChatRoom;
import org.spacehub.entities.ChatRoom.NewChatRoom;
import org.spacehub.repository.ChatRoom.ChatMessageRepository;
import org.spacehub.service.chatRoom.chatroomInterfaces.IChatMessageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class ChatMessageService implements IChatMessageService {

  private final ChatMessageRepository chatMessageRepository;

  public ChatMessageService(ChatMessageRepository chatMessageRepository) {
    this.chatMessageRepository = chatMessageRepository;
  }

  @Transactional
  public List<ChatMessage> saveAll(List<ChatMessage> messages) {
    return chatMessageRepository.saveAll(messages);
  }

  @Transactional(readOnly = true)
  public List<ChatMessage> getMessagesForRoom(ChatRoom room) {
    return chatMessageRepository.findByRoomOrderByTimestampAsc(room);
  }

  @Transactional(readOnly = true)
  public List<ChatMessage> getMessagesForNewChatRoom(NewChatRoom newChatRoom) {
    return chatMessageRepository.findByNewChatRoomOrderByTimestampAsc(newChatRoom);
  }

  @Transactional(readOnly = true)
  public Optional<ChatMessage> findById(Long id) {
    return chatMessageRepository.findById(id);
  }

  @Transactional
  public boolean deleteMessageById(Long messageId) {
    if (chatMessageRepository.existsById(messageId)) {
      chatMessageRepository.deleteById(messageId);
      return true;
    }
    return false;
  }

}

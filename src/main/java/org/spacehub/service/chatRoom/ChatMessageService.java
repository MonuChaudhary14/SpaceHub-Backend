package org.spacehub.service.chatRoom;

import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatMessageService implements IChatMessageService {

  private final ChatMessageRepository chatMessageRepository;

  @Transactional
  public List<ChatMessage> saveAll(List<ChatMessage> messages) {
    return chatMessageRepository.saveAll(messages);
  }

  public List<ChatMessage> getMessagesForRoom(ChatRoom room) {
    return chatMessageRepository.findByRoomOrderByTimestampAsc(room);
  }

  public List<ChatMessage> getMessagesForNewChatRoom(NewChatRoom newChatRoom) {
    return chatMessageRepository.findByNewChatRoomOrderByTimestampAsc(newChatRoom);
  }

  public Optional<ChatMessage> findById(Long id) {
    return chatMessageRepository.findById(id);
  }

  public Optional<ChatMessage> findByUuid(String messageUuid) {
    return chatMessageRepository.findByMessageUuid(messageUuid);
  }

  @Transactional
  public boolean deleteMessageByUuid(String messageUuid) {
    return chatMessageRepository.findByMessageUuid(messageUuid)
            .map(m -> {
              chatMessageRepository.deleteByMessageUuid(messageUuid);
              return true;}).orElse(false);
  }
}

package org.spacehub.service.Message;

import lombok.RequiredArgsConstructor;
import org.spacehub.entities.ChatRoom.ChatMessage;
import org.spacehub.entities.ChatRoom.ChatRoom;
import org.spacehub.entities.ScheduledMessage.ScheduledMessage;
import org.spacehub.repository.ChatRoom.ScheduledMessageRepository;
import org.spacehub.service.chatRoom.ChatRoomService;
import org.spacehub.service.Interface.IScheduledMessageService;
import org.spacehub.service.chatRoom.ChatMessageQueue;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ScheduledMessageService implements IScheduledMessageService {

  private final ScheduledMessageRepository scheduledMessageRepository;
  private final ChatMessageQueue chatMessageQueue;
  private final ChatRoomService chatRoomService;

  public ScheduledMessage addScheduledMessage(ScheduledMessage message) {
    message.setSent(false);
    return scheduledMessageRepository.save(message);
  }

  @Scheduled(cron = "0 * * * * *")
  public void sendScheduledMessage() {
    List<ScheduledMessage> scheduled = scheduledMessageRepository.findBySentFalseAndScheduledTimeBefore(LocalDateTime.now());

    for (ScheduledMessage message : scheduled) {
      Optional<ChatRoom> optionalRoom = chatRoomService.findByRoomCode(UUID.fromString(message.getRoomCode()));
      if (optionalRoom.isEmpty()) continue;

      ChatRoom room = optionalRoom.get();

      ChatMessage chatMessage = ChatMessage.builder()
              .room(room)
              .roomCode(String.valueOf(room.getRoomCode()))
              .senderEmail(message.getSenderEmail())
              .message(message.getMessage())
              .timestamp(System.currentTimeMillis())
              .build();

      chatMessageQueue.enqueue(chatMessage);

      message.setSent(true);
      scheduledMessageRepository.save(message);
    }
  }
}

package org.spacehub.service.Message;

import lombok.RequiredArgsConstructor;
import org.spacehub.entities.DirectMessaging.Message;
import org.spacehub.handler.ChatWebSocketHandlerMessaging;
import org.spacehub.service.Interface.IMessageQueueService;
import org.spacehub.service.Interface.IMessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class MessageQueueService implements IMessageQueueService {

  private final List<Message> queue = new ArrayList<>();
  private final IMessageService messageService;
  private ChatWebSocketHandlerMessaging messagingHandler;

  @Autowired
  @Lazy
  public void setMessagingHandler(ChatWebSocketHandlerMessaging handler) {
    this.messagingHandler = handler;
  }

  public synchronized void enqueue(Message message) {
    queue.add(message);
    if (queue.size() >= 10)
      flushQueue();
  }

  @Scheduled(fixedRate = 10000)
  public synchronized void flushQueue() {
    if (queue.isEmpty()) return;
    List<Message> batch = new ArrayList<>(queue);
    queue.clear();

    List<Message> saved = messageService.saveMessageBatch(batch);
    if (messagingHandler != null) {
      for (Message message : saved) {
        try {
          messagingHandler.broadcastMessageToUsers(message);
        } catch (Exception ignored) {}
      }
    }
  }

  public synchronized boolean deleteMessageByUuid(String messageUuid) {
    queue.removeIf(m -> Objects.equals(m.getMessageUuid(), messageUuid));
    return messageService.deleteMessageByUuid(messageUuid);
  }

}

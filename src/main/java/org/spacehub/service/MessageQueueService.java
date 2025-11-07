package org.spacehub.service;

import org.spacehub.entities.DirectMessaging.Message;
import org.spacehub.service.Interface.IMessageService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.List;

@Service
public class MessageQueueService {

  private final List<Message> queue = new ArrayList<>();
  private final IMessageService messageService;
  private static final Logger logger = LoggerFactory.getLogger(MessageQueueService.class);


  public MessageQueueService(IMessageService messageService) {
    this.messageService = messageService;
  }

  public synchronized void enqueue(Message message) {
    queue.add(message);
    sendBatchIfSizeReached();
  }

  private synchronized void sendBatchIfSizeReached() {
    int BATCH_SIZE = 10;
    if (queue.size() >= BATCH_SIZE) flushQueue();
  }

  @Scheduled(cron = "0 * * * * *")
  public synchronized void sendEveryInterval() {
    flushQueue();
  }

  private synchronized void flushQueue() {
    if (queue.isEmpty()) {
      return;
    }

    List<Message> batch = new ArrayList<>(queue);
    queue.clear();

    try {
      messageService.saveMessageBatch(batch);
      System.out.println("Flushed " + batch.size() + " direct messages to DB.");
    }
    catch (Exception e) {
      logger.error("Error while flushing message batch: {}", e.getMessage(), e);
      queue.addAll(batch);
    }
  }

}

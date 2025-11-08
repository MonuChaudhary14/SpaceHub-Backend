package org.spacehub.service.Interface;

import org.spacehub.entities.DirectMessaging.Message;

public interface IMessageQueueService {

  void enqueue(Message message);

  void flushQueue();

}

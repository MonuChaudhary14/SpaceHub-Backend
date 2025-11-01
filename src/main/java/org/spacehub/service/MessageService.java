package org.spacehub.service;

import org.spacehub.entities.DirectMessaging.Message;
import org.spacehub.repository.MessageRepository;
import org.spacehub.service.Interface.IMessageService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class MessageService implements IMessageService {
  private final MessageRepository repo;

  public MessageService(MessageRepository repo) {
    this.repo = repo;
  }

  @CacheEvict(value = "chatCache", allEntries = true)
  public Message saveMessage(Message message) {
    return repo.save(message);
  }

  @Cacheable(value = "chatCache", key = "#user1 + '_' + #user2")
  public List<Message> getChat(String user1, String user2) {
    return repo.findBySenderIdAndReceiverIdOrReceiverIdAndSenderId(user1, user2, user1, user2);
  }
}


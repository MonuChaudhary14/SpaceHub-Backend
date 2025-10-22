package org.spacehub.service;

import org.spacehub.entities.DirectMessaging.Message;
import org.spacehub.repository.MessageRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class MessageService {
  private final MessageRepository repo;

  public MessageService(MessageRepository repo) {
    this.repo = repo;
  }

  public Message saveMessage(Message message) {
    return repo.save(message);
  }

  public List<Message> getChat(String user1, String user2) {
    return repo.findBySenderIdAndReceiverIdOrReceiverIdAndSenderId(user1, user2, user1, user2);
  }
}


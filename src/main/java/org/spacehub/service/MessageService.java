package org.spacehub.service;

import org.spacehub.entities.DirectMessaging.Message;
import org.spacehub.repository.MessageRepository;
import org.spacehub.service.Interface.IMessageService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MessageService implements IMessageService {
  private final MessageRepository repo;

  public MessageService(MessageRepository repo) {
    this.repo = repo;
  }

  public Message saveMessage(Message message) {
    return repo.save(message);
  }

  public void saveMessageBatch(List<Message> messages) {
    repo.saveAll(messages);
  }

  public List<Message> getChat(String user1, String user2) {
    return repo.findBySenderEmailAndReceiverEmailOrReceiverEmailAndSenderEmail(user1, user2, user1, user2);
  }

}


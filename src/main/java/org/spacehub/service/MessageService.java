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

  @Override
  public Message saveMessage(Message message) {
    return repo.save(message);
  }

  @Override
  public void saveMessageBatch(List<Message> messages) {
    repo.saveAll(messages);
  }

  @Override
  public List<Message> getChat(String user1, String user2) {
    return repo.findBySenderEmailAndReceiverEmailOrReceiverEmailAndSenderEmailOrderByTimestampAsc(user1, user2, user1, user2);
  }

  @Override
  public List<Message> getAllMessagesForUser(String email) {
    return repo.findBySenderEmailOrReceiverEmailOrderByTimestampDesc(email, email);
  }

  @Override
  public List<String> getAllChatPartners(String email) {
    return repo.findDistinctChatPartners(email);
  }
}

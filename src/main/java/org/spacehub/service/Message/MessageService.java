package org.spacehub.service.Message;

import org.spacehub.entities.DirectMessaging.Message;
import org.spacehub.repository.ChatRoom.MessageRepository;
import org.spacehub.service.Interface.IMessageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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
  @Transactional(readOnly = true)
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

  @Override
  @Transactional
  public Message deleteMessageForUser(Long messageId, String requesterEmail) {
    Optional<Message> optionalMessage = repo.findById(messageId);
    if (optionalMessage.isEmpty()) return null;

    Message message = optionalMessage.get();

    boolean changed = false;
    if (requesterEmail.equals(message.getSenderEmail())) {
      if (message.getSenderDeleted() == null || !message.getSenderDeleted()) {
        message.setSenderDeleted(true);
        changed = true;
      }
    }
    else if (requesterEmail.equals(message.getReceiverEmail())) {
      if (message.getReceiverDeleted() == null || !message.getReceiverDeleted()) {
        message.setReceiverDeleted(true);
        changed = true;
      }
    }
    else {
      throw new SecurityException("Not allowed to delete this message");
    }

    if (changed) {
      if (Boolean.TRUE.equals(message.getSenderDeleted()) && Boolean.TRUE.equals(message.getReceiverDeleted())) {
        message.setDeletedAt(LocalDateTime.now());
      }
      message = repo.save(message);
    }

    return message;
  }

  @Override
  @Transactional
  public void deleteMessageHard(Long messageId) {
    repo.deleteById(messageId);
  }

  @Override
  public Message getMessageById(Long id) {
    return repo.findById(id).orElse(null);
  }

}

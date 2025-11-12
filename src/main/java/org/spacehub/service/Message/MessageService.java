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
  public List<Message> saveMessageBatch(List<Message> messages) {
    return repo.saveAll(messages);
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
    return applySoftDelete(optionalMessage.get(), requesterEmail);
  }

  @Transactional
  public Message deleteMessageForUserByUuid(String messageUuid, String requesterEmail) {
    Optional<Message> optionalMessage = repo.findByMessageUuid(messageUuid);
    if (optionalMessage.isEmpty()) return null;
    return applySoftDelete(optionalMessage.get(), requesterEmail);
  }

  private Message applySoftDelete(Message message, String requesterEmail) {
    boolean changed = false;

    if (requesterEmail.equals(message.getSenderEmail())) {
      if (!Boolean.TRUE.equals(message.getSenderDeleted())) {
        message.setSenderDeleted(true);
        changed = true;
      }
    }
    else if (requesterEmail.equals(message.getReceiverEmail())) {
      if (!Boolean.TRUE.equals(message.getReceiverDeleted())) {
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

  @Transactional
  public void deleteMessageHardByUuid(String messageUuid) {
    repo.deleteByMessageUuid(messageUuid);
  }

  @Override
  public Message getMessageById(Long id) {
    return repo.findById(id).orElse(null);
  }

  public Message getMessageByUuid(String messageUuid) {
    return repo.findByMessageUuid(messageUuid).orElse(null);
  }

  @Override
  public List<Message> getUnreadMessages(String receiverEmail) {
    return repo.findByReceiverEmailAndReadStatusFalse(receiverEmail);
  }

  @Override
  @Transactional
  public Message markAsRead(Long messageId) {
    Optional<Message> optionalMessage = repo.findById(messageId);
    if (optionalMessage.isEmpty()) return null;
    Message mess = optionalMessage.get();
    if (!Boolean.TRUE.equals(mess.getReadStatus())) {
      mess.setReadStatus(true);
      repo.save(mess);
    }
    return mess;
  }

  @Transactional
  public Message markAsReadByUuid(String messageUuid) {
    Optional<Message> optionalMessage = repo.findByMessageUuid(messageUuid);
    if (optionalMessage.isEmpty()) return null;
    Message mess = optionalMessage.get();
    if (!Boolean.TRUE.equals(mess.getReadStatus())) {
      mess.setReadStatus(true);
      repo.save(mess);
    }
    return mess;
  }

  @Override
  public void markAllAsRead(String receiverEmail, String senderEmail) {
    repo.markAllAsReadBetweenUsers(receiverEmail, senderEmail);
  }

  @Override
  public long countUnreadMessages(String receiverEmail) {
    return repo.countUnreadMessages(receiverEmail);
  }

  @Override
  public long countUnreadMessagesInChat(String userEmail, String chatPartner) {
    return repo.countUnreadMessagesInChat(userEmail, chatPartner);
  }

  @Transactional
  public boolean deleteMessageByUuid(String messageUuid) {
    Optional<Message> message = repo.findByMessageUuid(messageUuid);
    if (message.isPresent()) {
      repo.deleteByMessageUuid(messageUuid);
      return true;
    }
    return false;
  }

}

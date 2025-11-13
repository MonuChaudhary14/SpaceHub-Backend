package org.spacehub.service.Message;

import org.spacehub.entities.DirectMessaging.Message;
import org.spacehub.repository.ChatRoom.MessageRepository;
import org.spacehub.service.Interface.IMessageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
  public void saveMessage(Message message) {
    repo.save(message);
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
    return optionalMessage.map(message -> applySoftDelete(message, requesterEmail)).orElse(null);
  }

  @Transactional
  public Message deleteMessageForUserByUuid(String messageUuid, String requesterEmail) {
    Optional<Message> optionalMessage = repo.findByMessageUuid(messageUuid);
    return optionalMessage.map(message -> applySoftDelete(message, requesterEmail)).orElse(null);
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
  public void markAsReadByUuid(String messageUuid) {
    Optional<Message> optionalMessage = repo.findByMessageUuid(messageUuid);
    if (optionalMessage.isEmpty()) return;
    Message mess = optionalMessage.get();
    if (!Boolean.TRUE.equals(mess.getReadStatus())) {
      mess.setReadStatus(true);
      repo.save(mess);
    }
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

  @Override
  @Transactional
  public ResponseEntity<?> handleDeleteRequest(Long id, String requesterEmail, boolean forEveryone) {
    try {
      if (forEveryone) {
        Message msg = getMessageById(id);
        if (msg == null) return ResponseEntity.notFound().build();

        if (!requesterEmail.equals(msg.getSenderEmail())) {
          return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body("Only sender can delete for everyone");
        }

        msg.setSenderDeleted(true);
        msg.setReceiverDeleted(true);
        saveMessage(msg);
        return ResponseEntity.ok(msg);
      } else {
        Message updated = deleteMessageForUser(id, requesterEmail);
        if (updated == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(updated);
      }
    } catch (SecurityException se) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body(se.getMessage());
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
    }
  }

  @Override
  @Transactional
  public ResponseEntity<?> handleHardDeleteRequest(Long id) {
    deleteMessageHard(id);
    return ResponseEntity.noContent().build();
  }

}

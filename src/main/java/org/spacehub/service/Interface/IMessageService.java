package org.spacehub.service.Interface;

import org.spacehub.entities.DirectMessaging.Message;
import java.util.List;

public interface IMessageService {

  Message saveMessage(Message message);

  List<Message> saveMessageBatch(List<Message> messages);

  List<Message> getChat(String user1, String user2);

  List<Message> getAllMessagesForUser(String email);

  List<String> getAllChatPartners(String email);

  Message deleteMessageForUser(Long messageId, String requesterEmail);

  Message deleteMessageForUserByUuid(String messageUuid, String requesterEmail);

  void deleteMessageHard(Long messageId);

  void deleteMessageHardByUuid(String messageUuid);

  Message getMessageById(Long id);

  Message getMessageByUuid(String messageUuid);

  List<Message> getUnreadMessages(String receiverEmail);

  Message markAsRead(Long messageId);

  Message markAsReadByUuid(String messageUuid);

  void markAllAsRead(String receiverEmail, String senderEmail);

  long countUnreadMessages(String receiverEmail);

  long countUnreadMessagesInChat(String userEmail, String chatPartner);

  boolean deleteMessageByUuid(String messageUuid);
}


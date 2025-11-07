package org.spacehub.service.Interface;

import org.spacehub.entities.DirectMessaging.Message;
import java.util.List;

public interface IMessageService {

  Message saveMessage(Message message);

  void saveMessageBatch(List<Message> messages);

  List<Message> getChat(String user1, String user2);

  List<Message> getAllMessagesForUser(String email);

  List<String> getAllChatPartners(String email);
}


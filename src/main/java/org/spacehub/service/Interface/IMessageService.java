package org.spacehub.service.Interface;

import org.spacehub.entities.DirectMessaging.Message;
import java.util.List;

public interface IMessageService {

  Message saveMessage(Message message);

  List<Message> getChat(String user1, String user2);

}


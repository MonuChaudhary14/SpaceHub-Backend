package org.spacehub.controller;

import org.spacehub.entities.DirectMessaging.Message;
import org.spacehub.service.MessageService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/messages")
public class MessageController {

  private final MessageService service;
  private final SimpMessagingTemplate messagingTemplate;

  public MessageController(MessageService service, SimpMessagingTemplate messagingTemplate) {
    this.service = service;
    this.messagingTemplate = messagingTemplate;
  }

  @GetMapping("/chat")
  public List<Message> getChat(@RequestParam String user1, @RequestParam String user2) {
    return service.getChat(user1, user2);
  }

  @MessageMapping("/chat")
  public void sendMessage(Message message) {
    Message savedMessage = service.saveMessage(message);
    messagingTemplate.convertAndSendToUser(
      message.getReceiverId(),
      "/queue/messages",
      savedMessage
    );

    messagingTemplate.convertAndSendToUser(
      message.getSenderId(),
      "/queue/messages",
      savedMessage
    );
  }
}

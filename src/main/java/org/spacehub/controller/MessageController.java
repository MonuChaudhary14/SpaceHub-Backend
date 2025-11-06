package org.spacehub.controller;

import org.spacehub.entities.DirectMessaging.Message;
import org.spacehub.service.Interface.IMessageService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/messages")
public class MessageController {

  private final IMessageService service;
  private final SimpMessagingTemplate messagingTemplate;

  public MessageController(IMessageService service, SimpMessagingTemplate messagingTemplate) {
    this.service = service;
    this.messagingTemplate = messagingTemplate;
  }

  @GetMapping("/chat")
  public List<Message> getChat(@RequestParam String user1, @RequestParam String user2) {
    return service.getChat(user1, user2);
  }

  @MessageMapping("/chat.send")
  public void sendMessage(Message message, Principal principal) {
    message.setSenderEmail(principal.getName());

    Message saved = service.saveMessage(message);

    messagingTemplate.convertAndSendToUser(saved.getReceiverEmail(), "/queue/messages", saved);

    messagingTemplate.convertAndSendToUser(saved.getSenderEmail(), "/queue/messages", saved);
  }

}

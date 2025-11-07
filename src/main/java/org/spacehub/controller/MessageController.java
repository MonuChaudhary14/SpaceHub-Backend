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

  private final IMessageService messageService;

  public MessageController(IMessageService messageService) {
    this.messageService = messageService;
  }

  @GetMapping("/chat")
  public List<Message> getChat(@RequestParam String user1, @RequestParam String user2) {
    return messageService.getChat(user1, user2);
  }

  @GetMapping("/user")
  public List<Message> getAllMessagesForUser(@RequestParam String email) {
    return messageService.getAllMessagesForUser(email);
  }

  @GetMapping("/partners")
  public List<String> getAllChatPartners(@RequestParam String email) {
    return messageService.getAllChatPartners(email);
  }

}

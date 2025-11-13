package org.spacehub.controller.ChatRoom;

import org.spacehub.entities.DirectMessaging.Message;
import org.spacehub.service.Interface.IMessageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
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

  @DeleteMapping("/{id}")
  public ResponseEntity<?> deleteMessage(
    @PathVariable Long id,
    @RequestParam String requesterEmail,
    @RequestParam(value = "forEveryone", required = false, defaultValue = "false") boolean forEveryone
  ) {
    return messageService.handleDeleteRequest(id, requesterEmail, forEveryone);
  }

  @DeleteMapping("/{id}/hard")
  public ResponseEntity<?> hardDelete(@PathVariable Long id) {
    return messageService.handleHardDeleteRequest(id);
  }
}

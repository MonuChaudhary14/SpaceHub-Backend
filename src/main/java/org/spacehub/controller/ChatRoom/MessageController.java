package org.spacehub.controller.ChatRoom;

import lombok.RequiredArgsConstructor;
import org.spacehub.entities.DirectMessaging.Message;
import org.spacehub.service.Interface.IMessageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/messages")
@RequiredArgsConstructor
public class MessageController {

  private final IMessageService messageService;

  @GetMapping("/chat")
  public List<Message> getChat(@RequestParam String user1, @RequestParam String user2) {
    return messageService.getChat(user1, user2);
  }

  @GetMapping("/user")
  public List<Message> getAllMessagesForUser() {
    return messageService.getAllMessagesForUser();
  }

  @GetMapping("/partners")
  public List<String> getAllChatPartners() {
    return messageService.getAllChatPartners();
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<?> deleteMessage(
          @PathVariable Long id,
          @RequestParam(value = "forEveryone", required = false, defaultValue = "false") boolean forEveryone
  ) {
    return messageService.handleDeleteRequest(id, forEveryone);
  }

  @DeleteMapping("/{id}/hard")
  public ResponseEntity<?> hardDelete(@PathVariable Long id) {
    return messageService.handleHardDeleteRequest(id);
  }
}

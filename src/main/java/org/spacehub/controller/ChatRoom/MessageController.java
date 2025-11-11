package org.spacehub.controller.ChatRoom;

import org.spacehub.entities.DirectMessaging.Message;
import org.spacehub.service.Interface.IMessageService;
import org.springframework.http.HttpStatus;
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
  public ResponseEntity<?> deleteMessage(@PathVariable("id") Long id,
                                         @RequestParam("requesterEmail") String requesterEmail,
                                         @RequestParam(value = "forEveryone", required = false, defaultValue = "false") boolean forEveryone) {
    try {
      if (forEveryone) {
        Message msg = messageService.getMessageById(id);
        if (msg == null) return ResponseEntity.notFound().build();
        if (!requesterEmail.equals(msg.getSenderEmail())) {
          return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only sender can delete for everyone");
        }
        msg.setSenderDeleted(true);
        msg.setReceiverDeleted(true);
        messageService.saveMessage(msg);
        return ResponseEntity.ok().body(msg);
      } else {
        Message updated = messageService.deleteMessageForUser(id, requesterEmail);
        if (updated == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(updated);
      }
    } catch (SecurityException se) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body(se.getMessage());
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
    }
  }

  @DeleteMapping("/{id}/hard")
  public ResponseEntity<?> hardDelete(@PathVariable("id") Long id) {
    messageService.deleteMessageHard(id);
    return ResponseEntity.noContent().build();
  }

}

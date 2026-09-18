package org.spacehub.controller.ChatRoom;

import lombok.RequiredArgsConstructor;
import org.spacehub.entities.ScheduledMessage.ScheduledMessage;
import org.spacehub.service.Message.ScheduledMessageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/v1/schedule")
@RequiredArgsConstructor
public class ScheduledMessageController {

  private final ScheduledMessageService scheduledMessageService;

  @PostMapping("/message")
  public ResponseEntity<ScheduledMessage> scheduleMessage(@RequestBody ScheduledMessage message) {
    ScheduledMessage saved = scheduledMessageService.addScheduledMessage(message);
    return ResponseEntity.ok(saved);
  }

}

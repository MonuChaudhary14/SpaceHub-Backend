package org.spacehub.controller;

import org.spacehub.entities.ScheduledMessage;
import org.spacehub.service.ScheduledMessageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/v1/schedule")
public class ScheduledMessageController {

    private final ScheduledMessageService scheduledMessageService;

    public ScheduledMessageController(ScheduledMessageService scheduledMessageService) {
        this.scheduledMessageService = scheduledMessageService;
    }

    @PostMapping("/message")
    public ResponseEntity<ScheduledMessage> scheduleMessage(@RequestBody ScheduledMessage message) {
        ScheduledMessage saved = scheduledMessageService.addScheduledMessage(message);
        return ResponseEntity.ok(saved);
    }

}

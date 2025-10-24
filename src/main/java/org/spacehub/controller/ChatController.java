package org.spacehub.controller;

import org.spacehub.DTO.ChatMessage;
import org.spacehub.service.AiService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

  private final AiService aiService;

  public ChatController(AiService aiService) {
    this.aiService = aiService;
  }

  @PostMapping
  public ChatMessage chat(@RequestBody ChatMessage request) {
    String reply;
    try {
      reply = aiService.ask(request.getMessage()).block();
    } catch (Exception e) {
      reply = "Sorry, something went wrong.";
    }
    return new ChatMessage("Assistant", reply);
  }
}


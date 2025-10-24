package org.spacehub.controller;

import org.spacehub.DTO.ChatMessage;
import org.spacehub.service.AiService;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

  private final AiService aiService;

  public ChatController(AiService aiService) {
    this.aiService = aiService;
  }

  @PostMapping
  public Mono<ChatMessage> chat(@RequestBody ChatMessage request) {
    return aiService.ask(request.getMessage())
      .map(reply -> new ChatMessage("Assistant", reply))
      .onErrorResume(e -> Mono.just(new ChatMessage("Assistant",
        "Sorry, something went wrong.")));
  }
}


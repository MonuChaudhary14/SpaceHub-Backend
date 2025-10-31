//package org.spacehub.service;
//
//import org.spacehub.DTO.AIChatResponse;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.http.HttpStatusCode;
//import org.springframework.http.MediaType;
//import org.springframework.stereotype.Service;
//import org.springframework.web.reactive.function.client.WebClient;
//import reactor.core.publisher.Mono;
//
//import java.time.Duration;
//import java.util.List;
//import java.util.Map;
//
//@Service
//public class AiService {
//
//  private final WebClient webClient;
//
//  public AiService(
//    @Value("${ai.chat.url}") String baseUrl,
//    String apiKey
//  ) {
//    this.webClient = WebClient.builder()
//      .baseUrl(baseUrl)
//      .defaultHeader("Authorization", "Bearer " + apiKey)
//      .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
//      .build();
//  }
//
//  public Mono<String> ask(String userMessage) {
//    var payload = Map.of(
//      "contents", List.of(
//        Map.of("parts", List.of(
//          Map.of("text", userMessage)
//        ))
//      )
//    );
//
//    return webClient.post()
//      .bodyValue(payload)
//      .retrieve()
//      .onStatus(HttpStatusCode::isError, response ->
//        response.bodyToMono(String.class)
//          .map(body -> new RuntimeException("AI API error: " + body))
//      )
//      .bodyToMono(AIChatResponse.class)
//      .timeout(Duration.ofSeconds(20))
//      .map(resp -> {
//        if (resp == null || resp.getChoices() == null || resp.getChoices().isEmpty()) {
//          return "Sorry, I couldn't generate a response.";
//        }
//        var msg = resp.getChoices().get(0).getMessage();
//        if (msg == null || msg.getContent() == null) {
//          return "Sorry, I couldn't generate a response.";
//        }
//        return msg.getContent();
//      });
//  }
//}

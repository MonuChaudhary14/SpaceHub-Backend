package org.spacehub.controller.Dashboard;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.spacehub.DTO.DashBoard.EmailRequest;
import org.spacehub.DTO.DashBoard.UsernameRequest;
import org.spacehub.entities.ApiResponse.ApiResponse;
import org.spacehub.service.Interface.IDashBoardService;
import org.spacehub.service.serviceAuth.RedisService;
import org.spacehub.service.serviceAuth.authInterfaces.IEmailService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

  private static final long CUSTOM_EMAIL_COOLDOWN_SECONDS = 24 * 60 * 60;
  private static final String CUSTOM_EMAIL_COOLDOWN_PREFIX = "CUSTOM_EMAIL_COOLDOWN_";
  private final IDashBoardService dashboardService;
  private final IEmailService emailService;
  private final RedisService redisService;

  @PostMapping("/set-username")
  public ResponseEntity<ApiResponse<String>> setUsername(@Valid @RequestBody UsernameRequest request) {
    ApiResponse<String> resp = dashboardService.saveUsernameByEmail(
            request.getEmail(),
            request.getUsername()
    );
    return ResponseEntity.status(resp.getStatus()).body(resp);
  }

  @PostMapping(value = "/upload-profile-image")
  public ResponseEntity<ApiResponse<String>> uploadProfileImage(@RequestParam("email") String email,
                                                                @RequestParam("image") MultipartFile image) {
    ApiResponse<String> response = dashboardService.uploadProfileImage(email, image);
    return ResponseEntity.status(response.getStatus()).body(response);
  }

  @GetMapping("/profile-summary")
  public ResponseEntity<ApiResponse<?>> getUserProfileSummary(@RequestParam("email") String email) {
    ApiResponse<?> response = dashboardService.getUserProfileSummary(email);
    return ResponseEntity.status(response.getStatus()).body(response);
  }

  @PostMapping(value = "/save-changes")
  public ResponseEntity<ApiResponse<Map<String, Object>>> saveChanges(
    @RequestParam("email") String email,
    @RequestParam(value = "newEmail", required = false) String newEmail,
    @RequestParam(value = "oldPassword", required = false) String oldPassword,
    @RequestParam(value = "newPassword", required = false) String newPassword,
    @RequestParam(value = "newUsername", required = false) String newUsername,
    @RequestParam(value = "image", required = false) MultipartFile image
  ) {
    ApiResponse<Map<String, Object>> resp = dashboardService.saveProfileChanges(
      email, newEmail, oldPassword, newPassword, newUsername, image
    );
    return ResponseEntity.status(resp.getStatus()).body(resp);
  }

  @PostMapping("/send-email")
  public ResponseEntity<ApiResponse<String>> sendCustomEmail(@Valid @RequestBody EmailRequest request) {
    String to = request.getTo();
    if (to == null || to.isBlank()) {
      ApiResponse<String> response = new ApiResponse<>(400, "Recipient email (to) is required", null);
      return ResponseEntity.badRequest().body(response);
    }

    String key = CUSTOM_EMAIL_COOLDOWN_PREFIX + to.trim().toLowerCase();

    try {
      String existing = redisService.getValue(key);
      if (existing != null) {
        ApiResponse<String> response = new ApiResponse<>(429,
                "You may only send a custom email to this recipient once every 24 hours. Try again later.", null);
        return ResponseEntity.status(429).body(response);
      }

      String fixedMessage = """
Hey there! 👋 

Welcome to SpaceHub — we're excited to have you here!

You're now part of a growing community of creators, learners, and explorers.  
Stay tuned for upcoming updates, community stories, new features, and exciting product enhancements! 🚀
""";

      emailService.sendCustomEmail(to, request.getSubject(), fixedMessage);

      redisService.saveValue(key, "1", CUSTOM_EMAIL_COOLDOWN_SECONDS);

      ApiResponse<String> response = new ApiResponse<>(200, "Email sent successfully!", null);
      return ResponseEntity.ok(response);
    }
    catch (Exception e) {
      ApiResponse<String> response = new ApiResponse<>(500,
              "Failed to send email: " + e.getMessage(), null);
      return ResponseEntity.status(500).body(response);
    }
  }

}

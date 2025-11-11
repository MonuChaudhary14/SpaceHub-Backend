package org.spacehub.controller;

import jakarta.validation.Valid;
import org.spacehub.DTO.DashBoard.EmailRequest;
import org.spacehub.DTO.DashBoard.UsernameRequest;
import org.spacehub.entities.ApiResponse.ApiResponse;
import org.spacehub.service.Interface.IDashBoardService;
import org.spacehub.service.serviceAuth.EmailService;
import org.spacehub.service.serviceAuth.authInterfaces.IEmailService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

  private final IDashBoardService dashboardService;
  private final IEmailService emailService;

  public DashboardController(IDashBoardService dashboardService, IEmailService emailService) {
    this.dashboardService = dashboardService;
    this.emailService = emailService;
  }

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
    try {
      emailService.sendCustomEmail(request.getTo(), request.getSubject(), request.getMessage());
      ApiResponse<String> response = new ApiResponse<>(200, "Email sent successfully!", null);
      return ResponseEntity.ok(response);
    } catch (Exception e) {
      ApiResponse<String> response = new ApiResponse<>(500, "Failed to send email: " + e.getMessage(),
        null);
      return ResponseEntity.status(500).body(response);
    }
  }

}

package org.spacehub.controller;

import jakarta.validation.Valid;
import org.spacehub.DTO.DashBoard.UsernameRequest;
import org.spacehub.entities.ApiResponse.ApiResponse;
import org.spacehub.service.Interface.IDashBoardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

  private final IDashBoardService dashboardService;

  public DashboardController(IDashBoardService dashboardService) {
    this.dashboardService = dashboardService;
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

}

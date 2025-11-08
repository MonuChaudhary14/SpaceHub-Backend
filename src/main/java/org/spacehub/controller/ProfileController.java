package org.spacehub.controller;

import org.spacehub.DTO.User.UserProfileDTO;
import org.spacehub.DTO.User.UserProfileResponse;
import org.spacehub.service.Interface.IProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/profile")
public class ProfileController {

  private final IProfileService profileService;

  public ProfileController(IProfileService profileService) {
    this.profileService = profileService;
  }

  @GetMapping("/getProfile")
  public ResponseEntity<?> getProfile(@RequestParam("email") String email) {
    try {
      UserProfileResponse resp = profileService.getProfileByEmail(email);
      return ResponseEntity.ok(Map.of(
              "status", 200,
              "message", "Profile fetched successfully",
              "data", resp));
    }
    catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(Map.of("status", 400, "message", e.getMessage()));
    }
    catch (Exception e) {
      return ResponseEntity.internalServerError().body(Map.of("status", 500, "message", "Error fetching profile"));
    }
  }

  @PutMapping("/updateProfile")
  public ResponseEntity<?> updateProfile(@RequestParam("email") String email, @RequestBody UserProfileDTO dto) {
    try {
      UserProfileResponse updated = profileService.updateProfileByEmail(email, dto);
      return ResponseEntity.ok(Map.of(
              "status", 200,
              "message", "Profile updated successfully",
              "data", updated));
    }
    catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(Map.of("status", 400, "message", e.getMessage()));
    }
    catch (Exception e) {
      return ResponseEntity.internalServerError().body(Map.of("status", 500, "message", "Error updating profile"));
    }
  }

  @PostMapping("/avatar")
  public ResponseEntity<?> uploadAvatar(@RequestParam("email") String email, @RequestParam("file") MultipartFile file) {
    try {
      UserProfileResponse updated = profileService.uploadAvatarByEmail(email, file);
      return ResponseEntity.ok(Map.of(
              "status", 200,
              "message", "Avatar uploaded successfully",
              "data", updated
      ));
    }
    catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(Map.of("status", 400, "message", e.getMessage()));
    }
    catch (Exception e) {
      return ResponseEntity.internalServerError().body(Map.of("status", 500, "message", "Error uploading avatar"));
    }
  }

  @PostMapping("/cover")
  public ResponseEntity<?> uploadCover(@RequestParam("email") String email, @RequestParam("file") MultipartFile file) {
    try {
      UserProfileResponse updated = profileService.uploadCoverPhotoByEmail(email, file);
      return ResponseEntity.ok(Map.of(
              "status", 200,
              "message", "Cover photo uploaded successfully",
              "data", updated));
    }
    catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(Map.of("status", 400, "message", e.getMessage()));
    }
    catch (Exception e) {
      return ResponseEntity.internalServerError().body(Map.of("status", 500, "message", "Error uploading cover photo"));
    }
  }

  @DeleteMapping("/delete")
  public ResponseEntity<?> deleteAccount(@RequestBody Map<String, String> payload,
                                         Authentication authentication) {
    try {
      if (authentication == null || !authentication.isAuthenticated()) {
        return ResponseEntity.status(401).body(Map.of("status", 401, "message", "Unauthorized access"));
      }

      String authenticatedEmail = authentication.getName();
      String currentPassword = payload.get("currentPassword");

      if (currentPassword == null || currentPassword.isBlank()) {
        return ResponseEntity.badRequest().body(Map.of("status", 400, "message", "Current password is required"));
      }

      profileService.deleteAccount(authenticatedEmail, currentPassword);
      return ResponseEntity.ok(Map.of("status", 200, "message", "Account deleted successfully"));

    }
    catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(Map.of("status", 400, "message", e.getMessage()));
    }
    catch (SecurityException e) {
      return ResponseEntity.status(403).body(Map.of("status", 403, "message", e.getMessage()));
    }
    catch (Exception e) {
      return ResponseEntity.internalServerError().body(Map.of("status", 500, "message", "Error deleting account"));
    }
  }

}

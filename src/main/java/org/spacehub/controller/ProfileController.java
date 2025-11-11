package org.spacehub.controller;

import org.spacehub.DTO.User.DeleteAccount;
import org.spacehub.DTO.User.UserProfileDTO;
import org.spacehub.DTO.User.UserProfileResponse;
import org.spacehub.entities.ApiResponse.ApiResponse;
import org.spacehub.service.Interface.IProfileService;
import org.springframework.http.ResponseEntity;
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
      return ResponseEntity.internalServerError().body(Map.of("status", 500, "message",
        "Error fetching profile"));
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
      return ResponseEntity.internalServerError().body(Map.of("status", 500, "message",
        "Error updating profile"));
    }
  }

  @PostMapping("/avatar")
  public ResponseEntity<?> uploadAvatar(@RequestParam("email") String email,
                                        @RequestParam("file") MultipartFile file) {
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
      return ResponseEntity.internalServerError().body(Map.of("status", 500, "message",
        "Error uploading avatar"));
    }
  }

  @PostMapping("/cover")
  public ResponseEntity<?> uploadCover(@RequestParam("email") String email,
                                       @RequestParam("file") MultipartFile file) {
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
      return ResponseEntity.internalServerError().body(Map.of("status", 500, "message",
        "Error uploading cover photo"));
    }
  }

  @DeleteMapping("/delete")
  public ResponseEntity<ApiResponse<String>> deleteAccount(@RequestBody DeleteAccount request) {
    try {
      profileService.deleteAccount(request);
      return ResponseEntity.ok(new ApiResponse<>(200, "Account deleted successfully", null));
    }
    catch (SecurityException | IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(new ApiResponse<>(400, e.getMessage(), null));
    }
    catch (Exception e) {
      return ResponseEntity.internalServerError().body(new ApiResponse<>(500, "Something went wrong",
        null));
    }
  }

}

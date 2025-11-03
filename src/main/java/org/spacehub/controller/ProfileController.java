package org.spacehub.controller;

import org.spacehub.DTO.User.UserProfileDTO;
import org.spacehub.DTO.User.UserProfileResponse;
import org.spacehub.entities.User.User;
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
  public ResponseEntity<UserProfileResponse> getProfile(@RequestParam("email") String email) {
    UserProfileResponse resp = profileService.getProfileByEmail(email);
    return ResponseEntity.ok(resp);
  }

  @PutMapping("/updateProfile")
  public ResponseEntity<User> updateProfile(
    @RequestParam("email") String email,
    @RequestBody UserProfileDTO dto) {
    User updatedUser = profileService.updateProfileByEmail(email, dto);
    return ResponseEntity.ok(updatedUser);
  }

  @PostMapping("/avatar")
  public ResponseEntity<User> uploadAvatar(
    @RequestParam("email") String email,
    @RequestParam("file") MultipartFile file) throws Exception {
    User user = profileService.uploadAvatarByEmail(email, file);
    return ResponseEntity.ok(user);
  }

  @PostMapping("/cover")
  public ResponseEntity<User> uploadCover(
    @RequestParam("email") String email,
    @RequestParam("file") MultipartFile file) throws Exception {
    User user = profileService.uploadCoverPhotoByEmail(email, file);
    return ResponseEntity.ok(user);
  }

  @PostMapping("/update-all")
  public ResponseEntity<UserProfileResponse> updateAccount(
    @RequestParam("email") String email,
    @RequestParam(value = "file", required = false) MultipartFile file,
    @RequestParam(value = "username", required = false) String username,
    @RequestParam(value = "newEmail", required = false) String newEmail,
    @RequestParam(value = "currentPassword", required = false) String currentPassword,
    @RequestParam(value = "newPassword", required = false) String newPassword
  ) throws Exception {
    User updated = profileService.updateAccount(email, file, username, newEmail, currentPassword, newPassword);
    UserProfileResponse resp = profileService.getProfileByEmail(
      updated.getEmail() == null ? email : updated.getEmail()
    );
    return ResponseEntity.ok(resp);
  }

  @DeleteMapping("/delete")
  public ResponseEntity<?> deleteAccount(
    @RequestParam("email") String email,
    @RequestParam(value = "currentPassword", required = false) String currentPassword
  ) {
    try {
      profileService.deleteAccount(email, currentPassword);
      return ResponseEntity.ok(Map.of("status", 200, "message", "Account deleted successfully"));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(Map.of("status", 400, "message", e.getMessage()));
    } catch (SecurityException e) {
      return ResponseEntity.status(403).body(Map.of("status", 403, "message", e.getMessage()));
    } catch (Exception e) {
      return ResponseEntity.internalServerError().body(Map.of("status", 500, "message", e.getMessage()));
    }
  }



}

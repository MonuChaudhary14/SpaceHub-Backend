package org.spacehub.controller;

import org.spacehub.DTO.UserProfileDTO;
import org.spacehub.DTO.UserProfileResponse;
import org.spacehub.entities.User.User;
import org.spacehub.service.ProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/profile")
public class ProfileController {

  private final ProfileService profileService;

  public ProfileController(ProfileService profileService) {
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

}

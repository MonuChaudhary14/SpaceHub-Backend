package org.spacehub.controller;

import org.spacehub.DTO.UserProfileDTO;
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

    @GetMapping("/getProfile/{userId}")
    public ResponseEntity<User> getProfile(@PathVariable Long userId) {
        User user = profileService.getProfile(userId);
        return ResponseEntity.ok(user);
    }

    @PutMapping("/updateProfile/{userId}")
    public ResponseEntity<User> updateProfile(@PathVariable Long userId, @RequestBody UserProfileDTO dto) {
        User updatedUser = profileService.updateProfile(userId, dto);
        return ResponseEntity.ok(updatedUser);
    }

    @PostMapping("/{userId}/avatar")
    public ResponseEntity<User> uploadAvatar(@PathVariable Long userId, @RequestParam("file") MultipartFile file) throws Exception {
        User user = profileService.uploadAvatar(userId, file);
        return ResponseEntity.ok(user);
    }

    @PostMapping("/{userId}/cover")
    public ResponseEntity<User> uploadCover(@PathVariable Long userId, @RequestParam("file") MultipartFile file) throws Exception {
        User user = profileService.uploadCoverPhoto(userId, file);
        return ResponseEntity.ok(user);
    }

}

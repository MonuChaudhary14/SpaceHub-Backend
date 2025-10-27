package org.spacehub.controller;

import org.spacehub.DTO.UserProfileDTO;
import org.spacehub.entities.ApiResponse.ApiResponse;
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
    public ResponseEntity<ApiResponse<User>> getProfile(@PathVariable Long userId) {
        User user = profileService.getProfile(userId);
        return ResponseEntity.ok(new ApiResponse<>(200, "Profile fetched successfully", user));
    }

    @PutMapping("/updateProfile/{userId}")
    public ResponseEntity<ApiResponse<User>> updateProfile(@PathVariable Long userId, @RequestBody UserProfileDTO dto) {
        User updatedUser = profileService.updateProfile(userId, dto);
        return ResponseEntity.ok(new ApiResponse<>(200, "Profile updated successfully", updatedUser));
    }

    @PostMapping("/{userId}/avatar")
    public ResponseEntity<ApiResponse<User>> uploadAvatar(@PathVariable Long userId, @RequestParam("file") MultipartFile file) throws Exception {
        User user = profileService.uploadAvatar(userId, file);
        return ResponseEntity.ok(new ApiResponse<>(200, "Avatar uploaded successfully", user));
    }

    @PostMapping("/{userId}/cover")
    public ResponseEntity<ApiResponse<User>> uploadCover(@PathVariable Long userId, @RequestParam("file") MultipartFile file) throws Exception {
        User user = profileService.uploadCoverPhoto(userId, file);
        return ResponseEntity.ok(new ApiResponse<>(200, "Cover photo uploaded successfully", user));
    }

}

package org.spacehub.service;

import org.spacehub.entities.ApiResponse.ApiResponse;
import org.spacehub.entities.Community.Community;
import org.spacehub.entities.Community.CommunityUser;
import org.spacehub.entities.User.User;
import org.spacehub.repository.UserRepository;
import org.spacehub.repository.commnunity.CommunityUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private final UserRepository userRepository;
    private final S3Service s3Service;

    @Autowired
    private CommunityUserRepository communityUserRepository;

    public DashboardService(UserRepository userRepository,  S3Service s3Service) {
        this.userRepository = userRepository;
        this.s3Service = s3Service;
    }

    public ApiResponse<String> saveUsernameByEmail(String email, String username) {

        if (email == null || email.isBlank() || username == null || username.isBlank()) {
            return new ApiResponse<>(400, "Email and username are required", null);
        }

        try {
            User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found with email: " + email));

            user.setUsername(username);
            userRepository.save(user);

            return new ApiResponse<>(200, "Username updated successfully", username);
        }
        catch (RuntimeException e) {
            return new ApiResponse<>(400, e.getMessage(), null);
        }
        catch (Exception e) {
            return new ApiResponse<>(500, "An unexpected error occurred: " + e.getMessage(), null);
        }
    }

    public ApiResponse<String> uploadProfileImage(String email, MultipartFile image) {

        if (image == null || image.isEmpty()) {
            return new ApiResponse<>(400, "No image provided", null);
        }

        try {
            User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));

            validateImage(image);

            String fileName = System.currentTimeMillis() + "_" + image.getOriginalFilename();
            String key = "avatars/" + email.replaceAll("[^a-zA-Z0-9]", "_") + "/" + fileName;

            s3Service.uploadFile(key, image.getInputStream(), image.getSize());

            user.setAvatarUrl(key);
            userRepository.save(user);

            String previewUrl = s3Service.generatePresignedDownloadUrl(key, Duration.ofHours(2));

            return new ApiResponse<>(HttpStatus.OK.value(),"Profile image uploaded successfully", previewUrl);

        }
        catch (IOException e) {
            return new ApiResponse<>(HttpStatus.INTERNAL_SERVER_ERROR.value(),"Error uploading image: " + e.getMessage(), null);
        }
        catch (RuntimeException e) {
            return new ApiResponse<>(400, e.getMessage(), null);
        }
        catch (Exception e) {
            return new ApiResponse<>(500, "An unexpected error occurred: " + e.getMessage(), null);
        }
    }

    private void validateImage(MultipartFile file) {

        if (file.isEmpty()) {
            throw new RuntimeException("File is empty");
        }

        if (file.getSize() > 2 * 1024 * 1024) {
            throw new RuntimeException("File size exceeds 2 MB");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new RuntimeException("Only image files are allowed");
        }
    }

    public ResponseEntity<ApiResponse<Map<String, Object>>> getUserProfile(String email) {
        Optional<User> optionalUser = userRepository.findByEmail(email);
        if (optionalUser.isEmpty()) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, "User not found", null));
        }

        User user = optionalUser.get();
        Map<String, Object> profile = new HashMap<>();
        profile.put("username", user.getUsername());
        profile.put("email", user.getEmail());
        if (user.getAvatarUrl() != null) {
            profile.put("profileImage", s3Service.generatePresignedDownloadUrl(user.getAvatarUrl(), Duration.ofHours(1)));
        }
        else {
            profile.put("profileImage", null);
        }

        return ResponseEntity.ok(new ApiResponse<>(200, "Profile fetched successfully", profile));
    }

    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getUserCommunities(String email) {
        Optional<User> optionalUser = userRepository.findByEmail(email);
        if (optionalUser.isEmpty()) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, "User not found", null));
        }

        User user = optionalUser.get();
        List<CommunityUser> memberships = communityUserRepository.findByUser(user);

        List<Map<String, Object>> communities = memberships.stream().map(cu -> {
            Community community = cu.getCommunity();
            Map<String, Object> map = new HashMap<>();
            map.put("id", community.getId());
            map.put("name", community.getName());
            if (community.getImageUrl() != null) {
                map.put("imageUrl", s3Service.generatePresignedDownloadUrl(community.getImageUrl(), Duration.ofHours(1)));
            }
            else {
                map.put("imageUrl", null);
            }
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(new ApiResponse<>(200, "Communities fetched successfully", communities));
    }

}

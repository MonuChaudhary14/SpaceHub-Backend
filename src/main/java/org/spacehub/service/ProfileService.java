package org.spacehub.service;

import org.spacehub.DTO.UserProfileDTO;
import org.spacehub.entities.User.User;
import org.spacehub.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;

@Service
public class ProfileService {

    private final UserRepository userRepository;
    private final S3Service s3Service;

    public ProfileService(UserRepository userRepository, S3Service s3Service) {
        this.userRepository = userRepository;
        this.s3Service = s3Service;
    }

    public User getProfile(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getAvatarUrl() != null && !user.getAvatarUrl().isEmpty()) {
            user.setAvatarUrl(s3Service.generatePresignedDownloadUrl(user.getAvatarUrl(), Duration.ofMinutes(15)));
        }

        if (user.getCoverPhotoUrl() != null && !user.getCoverPhotoUrl().isEmpty()) {
            user.setCoverPhotoUrl(s3Service.generatePresignedDownloadUrl(user.getCoverPhotoUrl(), Duration.ofMinutes(15)));
        }

        return user;
    }

    public User updateProfile(Long userId, UserProfileDTO dto) {
        User user = getProfile(userId);

        if (dto.getFirstName() != null) user.setFirstName(dto.getFirstName());

        if (dto.getLastName() != null) user.setLastName(dto.getLastName());

        if (dto.getBio() != null) user.setBio(dto.getBio());

        if (dto.getLocation() != null) user.setLocation(dto.getLocation());

        if (dto.getWebsite() != null) user.setWebsite(dto.getWebsite());

        if (dto.getDateOfBirth() != null) user.setDateOfBirth(LocalDate.parse(dto.getDateOfBirth()));

        if (dto.getIsPrivate() != null) user.setIsPrivate(dto.getIsPrivate());

        return userRepository.save(user);
    }

    public User uploadAvatar(Long userId, MultipartFile file) throws IOException {
        validateImage(file);

        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));

        String key = "avatars/" + file.getOriginalFilename();
        s3Service.uploadFile(key, file.getInputStream(), file.getSize());

        user.setAvatarUrl(key);
        return userRepository.save(user);
    }

    public User uploadCoverPhoto(Long userId, MultipartFile file) throws IOException {
        validateImage(file);

        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));

        String key = "covers/" + file.getOriginalFilename();
        s3Service.uploadFile(key, file.getInputStream(), file.getSize());

        user.setCoverPhotoUrl(key);
        return userRepository.save(user);
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

}

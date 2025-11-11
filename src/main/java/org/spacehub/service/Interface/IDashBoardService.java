package org.spacehub.service.Interface;

import org.spacehub.entities.ApiResponse.ApiResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface IDashBoardService {

  ApiResponse<String> saveUsernameByEmail(String email, String username);

  ApiResponse<String> uploadProfileImage(String email, MultipartFile image);

  ApiResponse<Map<String, Object>> getUserProfileSummary(String email);

  ApiResponse<Map<String, Object>> saveProfileChanges(
    String email,
    String newEmail,
    String oldPassword,
    String newPassword,
    String newUsername,
    MultipartFile image
  );

}


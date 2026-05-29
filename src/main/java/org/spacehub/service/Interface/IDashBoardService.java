package org.spacehub.service.Interface;

import org.spacehub.entities.ApiResponse.ApiResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface IDashBoardService {

  ApiResponse<String> saveUsername(String username);

  ApiResponse<String> uploadProfileImage(MultipartFile image);

  ApiResponse<Map<String, Object>> getUserProfileSummary();

  ApiResponse<Map<String, Object>> saveProfileChanges(
    String newEmail,
    String oldPassword,
    String newPassword,
    String newUsername,
    MultipartFile image
  );

}


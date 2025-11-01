package org.spacehub.service.Interface;

import org.spacehub.entities.ApiResponse.ApiResponse;
import org.springframework.web.multipart.MultipartFile;

public interface IDashBoardService {

  ApiResponse<String> saveUsernameByEmail(String email, String username);

  ApiResponse<String> uploadProfileImage(String email, MultipartFile image);

}


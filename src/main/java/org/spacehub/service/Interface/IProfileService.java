package org.spacehub.service.Interface;

import org.spacehub.DTO.UserProfileDTO;
import org.spacehub.entities.User.User;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

public interface IProfileService {

  User getProfile(Long userId);

  User updateProfile(Long userId, UserProfileDTO dto);

  User uploadAvatar(Long userId, MultipartFile file) throws IOException;

  User uploadCoverPhoto(Long userId, MultipartFile file) throws IOException;

}


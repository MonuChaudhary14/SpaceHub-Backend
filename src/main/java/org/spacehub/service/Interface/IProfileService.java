package org.spacehub.service.Interface;

import org.spacehub.DTO.User.UserProfileDTO;
import org.spacehub.DTO.User.UserProfileResponse;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

public interface IProfileService {

  UserProfileResponse getProfileByEmail(String email);

  UserProfileResponse updateProfileByEmail(String email, UserProfileDTO dto);

  UserProfileResponse uploadAvatarByEmail(String email, MultipartFile file) throws IOException;

  UserProfileResponse uploadCoverPhotoByEmail(String email, MultipartFile file) throws IOException;

  void deleteAccount(String email, String currentPassword);
}

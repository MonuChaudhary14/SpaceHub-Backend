package org.spacehub.service.Interface;

import org.spacehub.DTO.User.DeleteAccount;
import org.spacehub.DTO.User.UserProfileDTO;
import org.spacehub.DTO.User.UserProfileResponse;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

public interface IProfileService {

  UserProfileResponse getProfile();

  UserProfileResponse updateProfile(UserProfileDTO dto);

  UserProfileResponse uploadAvatar(MultipartFile file) throws IOException;

  UserProfileResponse uploadCoverPhoto(MultipartFile file) throws IOException;

  void deleteAccount(DeleteAccount request);
}

package org.spacehub.service.Interface;

import org.spacehub.DTO.UserProfileDTO;
import org.spacehub.DTO.UserProfileResponse;
import org.spacehub.entities.User.User;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

public interface IProfileService {

  UserProfileResponse getProfileByEmail(String email);

  User updateProfileByEmail(String email, UserProfileDTO dto);

  User uploadAvatarByEmail(String email, MultipartFile file) throws IOException;

  User uploadCoverPhotoByEmail(String email, MultipartFile file) throws IOException;

}

package org.spacehub.utils;

import org.springframework.web.multipart.MultipartFile;

public class ImageValidator {

  private static final long MAX_FILE_SIZE = 2 * 1024 * 1024;

  private ImageValidator() {
  }

  public static void validate(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new RuntimeException("File is empty");
    }

    if (file.getSize() > MAX_FILE_SIZE) {
      throw new RuntimeException("File size exceeds 2 MB");
    }

    String contentType = file.getContentType();
    if (contentType == null || !contentType.startsWith("image/")) {
      throw new RuntimeException("Only image files are allowed");
    }
  }
}

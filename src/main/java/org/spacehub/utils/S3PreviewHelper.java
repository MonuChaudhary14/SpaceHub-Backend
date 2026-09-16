package org.spacehub.utils;

import org.spacehub.service.File.S3Service;
import java.time.Duration;

public final class S3PreviewHelper {
  private S3PreviewHelper() {
  }

  public static String generatePreviewUrlQuietly(S3Service s3Service, String fileKey, Duration ttl) {
    if (fileKey == null || fileKey.isBlank()) {
      return null;
    }
    if (fileKey.startsWith("http://") || fileKey.startsWith("https://")) {
      return fileKey;
    }
    if (s3Service == null) {
      return null;
    }
    try {
      return s3Service.generatePresignedDownloadUrl(fileKey, ttl);
    } catch (Exception ignored) {
      return null;
    }
  }
}

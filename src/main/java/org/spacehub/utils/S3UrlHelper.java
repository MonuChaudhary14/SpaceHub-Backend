package org.spacehub.utils;

import org.springframework.stereotype.Component;
import org.spacehub.service.S3Service;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Component
public class S3UrlHelper {

  private final S3Service s3Service;

  public S3UrlHelper(S3Service s3Service) {
    this.s3Service = s3Service;
  }

  public Map<String, Object> generatePresignedUrl(String key, Duration duration) {
    Map<String, Object> result = new HashMap<>();

    if (key != null && !key.isBlank()) {
      try {
        String presigned = s3Service.generatePresignedDownloadUrl(key, duration);
        result.put("url", presigned);
        result.put("key", key);
      } catch (Exception e) {
        result.put("url", null);
        result.put("key", key);
      }
    } else {
      result.put("url", null);
    }

    return result;
  }
}

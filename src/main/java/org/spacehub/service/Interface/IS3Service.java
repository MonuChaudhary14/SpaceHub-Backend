package org.spacehub.service.Interface;

import java.io.InputStream;
import java.time.Duration;

public interface IS3Service {

  void uploadFile(String key, InputStream inputStream, long contentLength);

  void deleteFile(String key);

  String generatePresignedUploadUrl(String key, Duration duration);

  String generatePresignedDownloadUrl(String key, Duration duration);

  String generateFileKey(String originalFilename);

}


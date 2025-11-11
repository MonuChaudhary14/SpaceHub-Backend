package org.spacehub.service;

import org.spacehub.service.Interface.IS3Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import java.io.InputStream;
import java.time.Duration;
import java.util.UUID;

@Service
public class S3Service implements IS3Service {

  private final S3Client s3Client;
  private final S3Presigner s3Presigner;

  @Value("${aws.s3.bucket}")
  private String bucketName;

  public S3Service(S3Client s3Client, S3Presigner s3Presigner) {
    this.s3Client = s3Client;
    this.s3Presigner = s3Presigner;
  }

  public void uploadFile(String key, InputStream inputStream, long contentLength) {
    PutObjectRequest request = PutObjectRequest.builder()
            .bucket(bucketName)
            .key(key)
            .build();
    s3Client.putObject(request, RequestBody.fromInputStream(inputStream, contentLength));
  }

  public void deleteFile(String key) {
    DeleteObjectRequest request = DeleteObjectRequest.builder()
            .bucket(bucketName)
            .key(key)
            .build();
    s3Client.deleteObject(request);
  }

  public String generatePresignedUploadUrl(String key, Duration duration) {
    PutObjectRequest request = PutObjectRequest.builder()
            .bucket(bucketName)
            .key(key)
            .build();

    PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
            .signatureDuration(duration)
            .putObjectRequest(request)
            .build();

    return s3Presigner.presignPutObject(presignRequest).url().toString();
  }

  public String generatePresignedDownloadUrl(String key, Duration duration) {
    GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
            .getObjectRequest(b -> b.bucket(bucketName).key(key))
            .signatureDuration(duration)
            .build();

    return s3Presigner.presignGetObject(presignRequest).url().toString();
  }

  public String generateFileKey(String originalFilename) {
    String uniqueId = UUID.randomUUID().toString();
    return "chat-uploads/" + uniqueId + "_" + originalFilename;
  }

  public InputStream getFileStream(String key) {
    GetObjectRequest getReq = GetObjectRequest.builder()
            .bucket(bucketName)
            .key(key)
            .build();

    return s3Client.getObject(getReq, ResponseTransformer.toInputStream());
  }

  public String getContentType(String key) {
    try {
      HeadObjectRequest headReq = HeadObjectRequest.builder()
              .bucket(bucketName)
              .key(key)
              .build();
      HeadObjectResponse headResp = s3Client.headObject(headReq);
      return headResp.contentType();
    }
    catch (S3Exception e) {
      return null;
    }
  }

  public String uploadFileChat(MultipartFile file, String folderPath) {
    try {
      String key = folderPath + UUID.randomUUID() + "_" + file.getOriginalFilename();

      PutObjectRequest request = PutObjectRequest.builder()
              .bucket(bucketName)
              .key(key)
              .contentType(file.getContentType())
              .build();

      s3Client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

      return String.format("https://%s.s3.amazonaws.com/%s", bucketName, key);

    } catch (Exception e) {
      throw new RuntimeException("File upload failed: " + e.getMessage(), e);
    }
  }
}

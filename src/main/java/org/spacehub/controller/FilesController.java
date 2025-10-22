package org.spacehub.controller;

import org.spacehub.DTO.PresignedRequestDTO;
import org.spacehub.entities.ApiResponse.ApiResponse;
import org.spacehub.service.S3Service;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;

@RestController
@RequestMapping("api/v1/files")
public class FilesController {

  private final S3Service s3Service;

  public FilesController(S3Service s3Service) {
    this.s3Service = s3Service;
  }

  @PostMapping("/upload")
  public ResponseEntity<ApiResponse<String>> uploadFile(@RequestParam("file") MultipartFile file) throws IOException {
    String key = file.getOriginalFilename();
    s3Service.uploadFile(key, file.getInputStream(), file.getSize());
    return ResponseEntity.ok(new ApiResponse<>(200, "File uploaded successfully", key));
  }

  @PostMapping("/presigned/upload")
  public ResponseEntity<ApiResponse<String>> getPresignedUploadUrl(@RequestBody PresignedRequestDTO request) {
    String key = request.getFile();
    Duration duration = Duration.ofMinutes(10);
    String url = s3Service.generatePresignedUploadUrl(key, duration);
    return ResponseEntity.ok(new ApiResponse<>(200, "Presigned upload URL generated", url));
  }

  @PostMapping("/presigned/download")
  public ResponseEntity<ApiResponse<String>> getPresignedDownloadUrl(@RequestBody PresignedRequestDTO request) {
    String key = request.getFile();

    Duration duration = Duration.ofMinutes(10);
    String url = s3Service.generatePresignedDownloadUrl(key, duration);

    return ResponseEntity.ok(
            new ApiResponse<>(200, "Presigned download URL generated successfully", url)
    );
  }

  @DeleteMapping("/delete")
  public ResponseEntity<ApiResponse<String>> deleteFile(@RequestParam String key) {
    s3Service.deleteFile(key);
    return ResponseEntity.ok(new ApiResponse<>(200, "File deleted successfully", key));
  }
}

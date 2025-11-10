package org.spacehub.controller;

import org.spacehub.DTO.PresignedRequestDTO;
import org.spacehub.entities.ApiResponse.ApiResponse;
import org.spacehub.service.Interface.IS3Service;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("api/v1/files")
public class FilesController {

  private final IS3Service s3Service;

  public FilesController(IS3Service s3Service) {
    this.s3Service = s3Service;
  }

  @PostMapping("/upload")
  public ResponseEntity<ApiResponse<String>> uploadFile(@RequestParam("file") MultipartFile file) throws IOException {
    String key = s3Service.generateFileKey(file.getOriginalFilename());
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

  @PostMapping("/presigned/chat-upload")
  public ResponseEntity<ApiResponse<Map<String, String>>> getChatFilePresignedUrl(
          @RequestParam("filename") String filename) {

    String key = s3Service.generateFileKey(filename);
    String uploadUrl = s3Service.generatePresignedUploadUrl(key, Duration.ofMinutes(10));
    return ResponseEntity.ok(new ApiResponse<>(200, "Chat presigned upload URL generated",
            Map.of("uploadUrl", uploadUrl, "key", key)));
  }

  @GetMapping("/download")
  public ResponseEntity<ApiResponse<String>> getPresignedDownloadUrl(@RequestParam String key) {
    String url = s3Service.generatePresignedDownloadUrl(key, Duration.ofMinutes(10));
    return ResponseEntity.ok(new ApiResponse<>(200, "Presigned download URL generated successfully", url));
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

  @PostMapping("/upload-and-get-url")
  public ResponseEntity<ApiResponse<Map<String, String>>> uploadFileAndGetUrl(
          @RequestParam("file") MultipartFile file) throws IOException {

    String key = s3Service.generateFileKey(file.getOriginalFilename());
    s3Service.uploadFile(key, file.getInputStream(), file.getSize());

    String fileUrl = s3Service.generatePresignedDownloadUrl(key, Duration.ofMinutes(10));

    Map<String, String> response = Map.of(
            "fileName", Objects.requireNonNull(file.getOriginalFilename()),
            "fileKey", key,
            "fileUrl", fileUrl,
            "contentType", Objects.requireNonNull(file.getContentType()));

    return ResponseEntity.ok(new ApiResponse<>(200, "File uploaded successfully", response));
  }

}

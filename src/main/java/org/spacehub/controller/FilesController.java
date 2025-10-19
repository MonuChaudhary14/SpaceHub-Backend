package org.spacehub.controller;

import org.spacehub.service.S3Service;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;

@RestController
@RequestMapping("/files")
public class FilesController {

    private final S3Service s3Service;

    public FilesController(S3Service s3Service) {
        this.s3Service = s3Service;
    }

    @PostMapping("/upload")
    public ResponseEntity<String> uploadFile(@RequestParam("file") MultipartFile file) throws IOException {
        String key = file.getOriginalFilename();
        s3Service.uploadFile(key, file.getInputStream(), file.getSize());
        return ResponseEntity.ok("File uploaded: " + key);
    }

    @GetMapping("/presigned/upload")
    public ResponseEntity<String> getPresignedUploadUrl(@RequestParam String key) {
        String url = s3Service.generatePresignedUploadUrl(key, Duration.ofMinutes(10));
        return ResponseEntity.ok(url);
    }

    @GetMapping("/presigned/download")
    public ResponseEntity<String> getPresignedDownloadUrl(@RequestParam String key) {
        String url = s3Service.generatePresignedDownloadUrl(key, Duration.ofMinutes(10));
        return ResponseEntity.ok(url);
    }

    @DeleteMapping("/delete")
    public ResponseEntity<String> deleteFile(@RequestParam String key) {
        s3Service.deleteFile(key);
        return ResponseEntity.ok("File deleted: " + key);
    }
}

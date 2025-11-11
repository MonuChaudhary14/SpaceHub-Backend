package org.spacehub.controller;

import lombok.RequiredArgsConstructor;
import org.spacehub.entities.ChatRoom.ChatMessage;
import org.spacehub.entities.ChatRoom.NewChatRoom;
import org.spacehub.service.S3Service;
import org.spacehub.service.chatRoom.ChatMessageQueue;
import org.spacehub.service.chatRoom.NewChatRoomService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/chatMessage")
@RequiredArgsConstructor
public class ChatFileController {

    private final S3Service s3Service;
    private final NewChatRoomService newChatRoomService;
    private final ChatMessageQueue chatMessageQueue;

    @PostMapping("/upload")
    public ResponseEntity<?> uploadChatFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("email") String senderEmail,
            @RequestParam("roomCode") String roomCode) {
        try {
            Optional<NewChatRoom> optRoom = newChatRoomService.getEntityByCode(UUID.fromString(roomCode));
            if (optRoom.isEmpty()) {
                return ResponseEntity.status(404).body("Chat room not found");
            }

            String fileKey = s3Service.generateFileKey(file.getOriginalFilename());
            s3Service.uploadFile(fileKey, file.getInputStream(), file.getSize());
            String fileUrl = s3Service.generatePresignedDownloadUrl(fileKey, Duration.ofMinutes(15));

            ChatMessage message = ChatMessage.builder()
                    .senderEmail(senderEmail)
                    .message("[File] " + file.getOriginalFilename())
                    .fileName(file.getOriginalFilename())
                    .fileUrl(fileUrl)
                    .contentType(file.getContentType())
                    .timestamp(Instant.now().toEpochMilli())
                    .roomCode(roomCode)
                    .newChatRoom(optRoom.get())
                    .type("FILE")
                    .build();

            chatMessageQueue.enqueue(message);
            return ResponseEntity.ok(message);

        }
        catch (Exception e) {
            return ResponseEntity.internalServerError().body("File upload failed: " + e.getMessage());
        }
    }

    @GetMapping("/download")
    public ResponseEntity<?> getDownloadLink(@RequestParam("fileKey") String fileKey) {
        try {
            String presignedUrl = s3Service.generatePresignedDownloadUrl(fileKey, Duration.ofMinutes(15));
            return ResponseEntity.ok(presignedUrl);
        }
        catch (Exception e) {
            return ResponseEntity.internalServerError().body("Failed to generate download URL");
        }
    }

    @DeleteMapping("/file")
    public ResponseEntity<?> deleteFile(@RequestParam("fileUrl") String fileUrl) {
        try {
            String key = fileUrl.split(".com/")[1];
            s3Service.deleteFile(key);
            return ResponseEntity.ok("File deleted from S3");
        }
        catch (Exception e) {
            return ResponseEntity.internalServerError().body("Failed to delete file: " + e.getMessage());
        }
    }

}

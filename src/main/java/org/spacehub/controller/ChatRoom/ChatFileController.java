package org.spacehub.controller.ChatRoom;

import org.spacehub.ExceptionHandler.ResourceNotFoundException;
import org.spacehub.entities.ChatRoom.ChatMessage;
import org.spacehub.service.chatRoom.chatroomInterfaces.IChatFileService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/chatMessage")
public class ChatFileController {

  private final IChatFileService chatFileService;

  public ChatFileController(IChatFileService chatFileService) {
    this.chatFileService = chatFileService;
  }

  @PostMapping("/upload")
  public ResponseEntity<?> uploadChatFile(
    @RequestParam("file") MultipartFile file,
    @RequestParam("email") String senderEmail,
    @RequestParam("roomCode") String roomCode) {
    try {
      ChatMessage message = chatFileService.uploadChatFile(file, senderEmail, roomCode);
      return ResponseEntity.ok(message);
    } catch (ResourceNotFoundException e) {
      return ResponseEntity.status(404).body(e.getMessage());
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    } catch (Exception e) {
      return ResponseEntity.internalServerError().body("File upload failed: " + e.getMessage());
    }
  }

  @GetMapping("/download")
  public ResponseEntity<?> getDownloadLink(@RequestParam("fileKey") String fileKey) {
    try {
      String presignedUrl = chatFileService.getDownloadLink(fileKey);
      return ResponseEntity.ok(presignedUrl);
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    } catch (Exception e) {
      return ResponseEntity.internalServerError().body("Failed to generate download URL: " + e.getMessage());
    }
  }

  @DeleteMapping("/file")
  public ResponseEntity<?> deleteFile(@RequestParam("fileUrl") String fileUrl) {
    try {
      chatFileService.deleteFile(fileUrl);
      return ResponseEntity.ok("File deleted from S3");
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    } catch (Exception e) {
      return ResponseEntity.internalServerError().body("Failed to delete file: " + e.getMessage());
    }
  }

}

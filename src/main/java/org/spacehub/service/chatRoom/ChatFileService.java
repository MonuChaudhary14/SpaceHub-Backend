package org.spacehub.service.chatRoom;

import lombok.RequiredArgsConstructor;
import org.spacehub.ExceptionHandler.ResourceNotFoundException;
import org.spacehub.ExceptionHandler.StorageException;
import org.spacehub.entities.ChatRoom.ChatMessage;
import org.spacehub.entities.ChatRoom.NewChatRoom;
import org.spacehub.service.File.S3Service;
import org.spacehub.service.chatRoom.chatroomInterfaces.IChatFileService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatFileService implements IChatFileService {

  private final S3Service s3Service;
  private final NewChatRoomService newChatRoomService;
  private final ChatMessageQueue chatMessageQueue;

  public ChatMessage uploadChatFile(MultipartFile file, String senderEmail, String roomCode) {

    validateInputs(file, senderEmail, roomCode);

    UUID roomUuid = parseRoomCode(roomCode);
    NewChatRoom room = newChatRoomService.getEntityByCode(roomUuid)
      .orElseThrow(() -> new ResourceNotFoundException("Chat room not found"));

    String originalFilename = sanitizeFilename(file.getOriginalFilename());
    String fileKey = uploadFileToS3(file, originalFilename);
    String fileUrl = generateFileUrl(fileKey);

    ChatMessage message = ChatMessage.builder()
      .senderEmail(senderEmail)
      .message("[File] " + originalFilename)
      .fileName(originalFilename)
      .fileUrl(fileUrl)
      .contentType(Objects.requireNonNullElse(file.getContentType(), "application/octet-stream"))
      .timestamp(Instant.now().toEpochMilli())
      .roomCode(roomCode)
      .newChatRoom(room)
      .type("FILE")
      .build();

    enqueueMessage(message, fileKey);

    return message;
  }

  private void validateInputs(MultipartFile file, String senderEmail, String roomCode) {
    if (file == null || file.isEmpty()) {
      throw new IllegalArgumentException("file is required");
    }
    if (senderEmail == null || senderEmail.isBlank()) {
      throw new IllegalArgumentException("senderEmail is required");
    }
    if (roomCode == null || roomCode.isBlank()) {
      throw new IllegalArgumentException("roomCode is required");
    }
  }

  private UUID parseRoomCode(String roomCode) {
    try {
      return UUID.fromString(roomCode);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Invalid roomCode: not a UUID");
    }
  }

  private String sanitizeFilename(String name) {
    if (name == null) {
      return "file";
    }
    return name.replaceAll("[\\\\/]", "_");
  }

  private String uploadFileToS3(MultipartFile file, String originalFilename) {
    String key = s3Service.generateFileKey(originalFilename);
    try {
      s3Service.uploadFile(key, file.getInputStream(), file.getSize());
      return key;
    } catch (IOException | RuntimeException e) {
      throw new StorageException("Failed to upload file to S3", e);
    }
  }

  private String generateFileUrl(String fileKey) {
    try {
      return s3Service.generatePresignedDownloadUrl(fileKey, Duration.ofMinutes(15));
    } catch (RuntimeException e) {
      try {
        s3Service.deleteFile(fileKey);
      } catch (Exception ignore) {}
      throw new StorageException("Failed to generate download URL", e);
    }
  }

  private void enqueueMessage(ChatMessage message, String fileKey) {
    try {
      chatMessageQueue.enqueue(message);
    } catch (RuntimeException e) {
      try {
        s3Service.deleteFile(fileKey);
      } catch (Exception ignore) {}
      throw new RuntimeException("Failed to enqueue chat message", e);
    }
  }

  public String getDownloadLink(String fileKeyOrUrl) {
    String key = extractKeyFromUrlOrReturnKey(fileKeyOrUrl);
    return s3Service.generatePresignedDownloadUrl(key, Duration.ofMinutes(15));
  }

  public void deleteFile(String fileUrlOrKey) {
    String key = extractKeyFromUrlOrReturnKey(fileUrlOrKey);
    s3Service.deleteFile(key);
  }

  private String extractKeyFromUrlOrReturnKey(String fileUrlOrKey) {
    if (fileUrlOrKey == null || fileUrlOrKey.isBlank()) {
      throw new IllegalArgumentException("fileKey or fileUrl is required");
    }
    try {
      URI uri = URI.create(fileUrlOrKey);
      String path = uri.getPath();
      if (path != null && path.length() > 1) {
        return path.substring(1);
      }
    } catch (Exception ignored) {
    }
    return fileUrlOrKey;
  }

}

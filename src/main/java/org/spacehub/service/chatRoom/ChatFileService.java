package org.spacehub.service.chatRoom;

import lombok.RequiredArgsConstructor;
import org.spacehub.ExceptionHandler.ResourceNotFoundException;
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
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatFileService implements IChatFileService {

  private final S3Service s3Service;
  private final NewChatRoomService newChatRoomService;
  private final ChatMessageQueue chatMessageQueue;

  public ChatMessage uploadChatFile(MultipartFile file, String senderEmail, String roomCode) throws IOException {
    if (file == null || file.isEmpty()) {
      throw new IllegalArgumentException("file is required");
    }
    if (senderEmail == null || senderEmail.isBlank()) {
      throw new IllegalArgumentException("senderEmail is required");
    }
    if (roomCode == null || roomCode.isBlank()) {
      throw new IllegalArgumentException("roomCode is required");
    }

    Optional<NewChatRoom> optRoom = newChatRoomService.getEntityByCode(UUID.fromString(roomCode));
    if (optRoom.isEmpty()) {
      throw new ResourceNotFoundException("Chat room not found");
    }

    String originalFilename = Objects.requireNonNullElse(file.getOriginalFilename(), "file");
    String fileKey = s3Service.generateFileKey(originalFilename);

    s3Service.uploadFile(fileKey, file.getInputStream(), file.getSize());
    String fileUrl = s3Service.generatePresignedDownloadUrl(fileKey, Duration.ofMinutes(15));

    ChatMessage message = ChatMessage.builder()
      .senderEmail(senderEmail)
      .message("[File] " + originalFilename)
      .fileName(originalFilename)
      .fileUrl(fileUrl)
      .contentType(file.getContentType())
      .timestamp(Instant.now().toEpochMilli())
      .roomCode(roomCode)
      .newChatRoom(optRoom.get())
      .type("FILE")
      .build();

    chatMessageQueue.enqueue(message);
    return message;
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

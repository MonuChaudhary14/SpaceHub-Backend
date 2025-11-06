package org.spacehub.controller;

import lombok.RequiredArgsConstructor;
import org.spacehub.DTO.Notification.NotificationUserRequest;
import org.spacehub.service.Interface.INotificationService;
import org.spacehub.DTO.Notification.NotificationRequestDTO;
import org.spacehub.DTO.Notification.NotificationResponseDTO;
import org.spacehub.entities.ApiResponse.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

  private final INotificationService notificationService;

  @PostMapping("/create")
  public ResponseEntity<ApiResponse<String>> createNotification(@RequestBody NotificationRequestDTO request) {
    notificationService.createNotification(request);
    return ResponseEntity.ok(new ApiResponse<>(200, "Notification created successfully",
      "success"));
  }

  @PostMapping("/list")
  public ResponseEntity<ApiResponse<List<NotificationResponseDTO>>> getUserNotifications(
    @RequestBody NotificationUserRequest request) {
    List<NotificationResponseDTO> notifications = notificationService.getUserNotifications(
            request.getEmail(), request.getScope(), request.getPage(), request.getSize());
    return ResponseEntity.ok(new ApiResponse<>(200, "Notifications fetched successfully",
      notifications));
  }

  @PutMapping("/{id}/read")
  public ResponseEntity<ApiResponse<String>> markNotificationAsRead(@PathVariable UUID id) {
    notificationService.markAsRead(id);
    return ResponseEntity.ok(new ApiResponse<>(200, "Notification marked as read", "success"));
  }

  @PostMapping("/inbox")
  public ResponseEntity<ApiResponse<List<NotificationResponseDTO>>> openInbox(
    @RequestBody NotificationUserRequest request) {
    List<NotificationResponseDTO> notifications = notificationService.fetchAndMarkRead(request.getEmail(),
      request.getPage(), request.getSize());
    return ResponseEntity.ok(new ApiResponse<>(200, "Notifications fetched and marked as read",
      notifications));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<ApiResponse<String>> deleteNotification(@PathVariable UUID id) {
    notificationService.deleteNotification(id);
    return ResponseEntity.ok(new ApiResponse<>(200, "Notification deleted successfully",
      "success"));
  }

  @PostMapping("/unread-count")
  public ResponseEntity<ApiResponse<Long>> getUnreadCount(@RequestBody NotificationUserRequest request) {
    long count = notificationService.countUnreadNotifications(request.getEmail());
    return ResponseEntity.ok(new ApiResponse<>(200, "Unread count fetched successfully", count));
  }

}

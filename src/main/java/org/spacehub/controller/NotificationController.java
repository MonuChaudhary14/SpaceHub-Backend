package org.spacehub.controller;

import lombok.RequiredArgsConstructor;
import org.spacehub.DTO.Notification.NotificationRequestDTO;
import org.spacehub.DTO.Notification.NotificationResponseDTO;
import org.spacehub.entities.ApiResponse.ApiResponse;
import org.spacehub.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    @Autowired
    private final NotificationService notificationService;

    @PostMapping
    public ResponseEntity<ApiResponse<String>> createNotification(@RequestBody NotificationRequestDTO request) {
        notificationService.createNotification(request);
        return ResponseEntity.ok(new ApiResponse<>(200, "Notification created successfully", "success"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationResponseDTO>>> getUserNotifications(@RequestParam String email, @RequestParam String scope) {
        List<NotificationResponseDTO> notifications = notificationService.getUserNotifications(email, scope);
        return ResponseEntity.ok(new ApiResponse<>(200, "Notifications fetched successfully", notifications));
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<ApiResponse<String>> markNotificationAsRead(@PathVariable Long id) {
        notificationService.markAsRead(id);
        return ResponseEntity.ok(new ApiResponse<>(200, "Notification marked as read", "success"));
    }

    @GetMapping("/inbox")
    public ResponseEntity<ApiResponse<List<NotificationResponseDTO>>> openInbox(@RequestParam String email) {
        List<NotificationResponseDTO> notifications = notificationService.fetchAndMarkRead(email);
        return ResponseEntity.ok(new ApiResponse<>(200, "Notifications fetched and marked as read", notifications));
    }


    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> deleteNotification(@PathVariable Long id) {
        notificationService.deleteNotification(id);
        return ResponseEntity.ok(new ApiResponse<>(200, "Notification deleted successfully", "success"));
    }

}
package org.spacehub.DTO.Notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationResponseDTO {

    private Long id;
    private String title;
    private String message;
    private String type;
    private String scope;
    private boolean actionable;
    private boolean read;
    private LocalDateTime createdAt;

    private Long communityId;
    private String communityName;

    private Long referenceId;

}

package org.spacehub.DTO.Notification;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationRequestDTO {

    private String email;
    private String title;
    private String message;
    private String type;
    private String senderEmail;
    private Long communityId;
    private Long referenceId;
    private String scope;
    private boolean actionable;
}
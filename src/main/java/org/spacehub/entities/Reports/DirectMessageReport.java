package org.spacehub.entities.Reports;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "direct_message_reports")
public class DirectMessageReport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private Long messageId;

    @Column(nullable = false, length = 320)
    private String reporterEmail;

    @Column(nullable = false, length = 320)
    private String senderEmail;

    @Column(nullable = false, length = 320)
    private String receiverEmail;

    @Column(length = 1000)
    private String reason;

    @Enumerated(EnumType.STRING)
    private ReportStatus status = ReportStatus.PENDING;

    @Column(nullable = false)
    private LocalDateTime reportedAt = LocalDateTime.now();

}

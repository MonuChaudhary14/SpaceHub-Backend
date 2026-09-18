package org.spacehub.entities.Reports;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "chatroom_message_reports")
public class ChatRoomMessageReport {

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
  private String chatRoomCode;

  @Column(length = 320)
  private String communityCode;

  @Column(length = 1000)
  private String reason;

  @Builder.Default
  @Enumerated(EnumType.STRING)
  private ReportStatus status = ReportStatus.PENDING;

  @Builder.Default
  @Column(nullable = false)
  private LocalDateTime reportedAt = LocalDateTime.now();

}

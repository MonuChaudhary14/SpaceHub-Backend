package org.spacehub.repository.Reports;

import org.spacehub.entities.Reports.ChatRoomMessageReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ChatRoomMessageReportRepository extends JpaRepository<ChatRoomMessageReport, UUID> {

}

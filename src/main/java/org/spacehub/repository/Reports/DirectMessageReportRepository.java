package org.spacehub.repository.Reports;

import org.spacehub.entities.Reports.DirectMessageReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DirectMessageReportRepository extends JpaRepository<DirectMessageReport, UUID> {
}

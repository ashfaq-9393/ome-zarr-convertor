package org.ome.converter.dao.api;

import org.ome.converter.dao.entity.AuditLogEntity;

import java.util.List;

public interface AuditLogRepository {
    void log(String action, String details);
    List<AuditLogEntity> getRecentLogs(int limit);
}

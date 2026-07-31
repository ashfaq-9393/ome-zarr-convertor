package org.ome.converter.dao.entity;

import java.time.Instant;

public record AuditLogEntity(
    String id,
    Instant timestamp,
    String action,
    String details
) {}

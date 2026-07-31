package org.ome.converter.dao.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.ome.converter.dao.api.AuditLogRepository;
import org.ome.converter.dao.entity.AuditLogEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class JsonFileAuditLogRepository implements AuditLogRepository {
    private static final Logger log = LoggerFactory.getLogger(JsonFileAuditLogRepository.class);

    private final Path storageFile;
    private final ObjectMapper mapper;
    private final List<AuditLogEntity> logEntries = new CopyOnWriteArrayList<>();

    public JsonFileAuditLogRepository() {
        this(Paths.get(System.getProperty("user.home"), ".ome-zarr-converter", "audit_log.json"));
    }

    public JsonFileAuditLogRepository(Path storageFile) {
        this.storageFile = storageFile;
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
        load();
    }

    private synchronized void load() {
        try {
            if (Files.exists(storageFile)) {
                List<AuditLogEntity> list = mapper.readValue(storageFile.toFile(), new TypeReference<>() {});
                logEntries.clear();
                logEntries.addAll(list);
            }
        } catch (Exception e) {
            log.error("Failed to load audit logs from file: {}", storageFile, e);
        }
    }

    private synchronized void persist() {
        try {
            if (storageFile.getParent() != null) {
                Files.createDirectories(storageFile.getParent());
            }
            mapper.writeValue(storageFile.toFile(), new ArrayList<>(logEntries));
        } catch (Exception e) {
            log.error("Failed to persist audit logs to file: {}", storageFile, e);
        }
    }

    @Override
    public void log(String action, String details) {
        AuditLogEntity entry = new AuditLogEntity(UUID.randomUUID().toString(), Instant.now(), action, details);
        logEntries.add(entry);
        persist();
    }

    @Override
    public List<AuditLogEntity> getRecentLogs(int limit) {
        List<AuditLogEntity> sorted = new ArrayList<>(logEntries);
        sorted.sort(Comparator.comparing(AuditLogEntity::timestamp).reversed());
        return sorted.subList(0, Math.min(limit, sorted.size()));
    }
}

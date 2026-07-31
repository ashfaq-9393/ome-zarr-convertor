package org.ome.converter.dao.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.ome.converter.dao.api.JobRepository;
import org.ome.converter.dao.entity.JobEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class JsonFileJobRepository implements JobRepository {
    private static final Logger log = LoggerFactory.getLogger(JsonFileJobRepository.class);

    private final Path storageFile;
    private final ObjectMapper mapper;
    private final Map<String, JobEntity> jobMap = new ConcurrentHashMap<>();

    public JsonFileJobRepository() {
        this(Paths.get(System.getProperty("user.home"), ".ome-zarr-converter", "jobs.json"));
    }

    public JsonFileJobRepository(Path storageFile) {
        this.storageFile = storageFile;
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
        load();
    }

    private synchronized void load() {
        try {
            if (Files.exists(storageFile)) {
                List<JobEntity> list = mapper.readValue(storageFile.toFile(), new TypeReference<>() {});
                jobMap.clear();
                for (JobEntity job : list) {
                    jobMap.put(job.id(), job);
                }
            }
        } catch (Exception e) {
            log.error("Failed to load jobs from storage file: {}", storageFile, e);
        }
    }

    private synchronized void persist() {
        try {
            if (storageFile.getParent() != null) {
                Files.createDirectories(storageFile.getParent());
            }
            mapper.writeValue(storageFile.toFile(), new ArrayList<>(jobMap.values()));
        } catch (Exception e) {
            log.error("Failed to persist jobs to storage file: {}", storageFile, e);
        }
    }

    @Override
    public void save(JobEntity job) {
        if (job != null) {
            jobMap.put(job.id(), job);
            persist();
        }
    }

    @Override
    public Optional<JobEntity> findById(String id) {
        return Optional.ofNullable(jobMap.get(id));
    }

    @Override
    public List<JobEntity> findAll() {
        List<JobEntity> list = new ArrayList<>(jobMap.values());
        list.sort(Comparator.comparing(JobEntity::startTime, Comparator.nullsLast(Comparator.reverseOrder())));
        return Collections.unmodifiableList(list);
    }

    @Override
    public void delete(String id) {
        if (jobMap.remove(id) != null) {
            persist();
        }
    }
}

package org.ome.converter.dao.api;

import org.ome.converter.dao.entity.JobEntity;

import java.util.List;
import java.util.Optional;

public interface JobRepository {
    void save(JobEntity job);
    Optional<JobEntity> findById(String id);
    List<JobEntity> findAll();
    void delete(String id);
}

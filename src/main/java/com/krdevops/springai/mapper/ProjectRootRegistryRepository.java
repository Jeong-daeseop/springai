package com.krdevops.springai.mapper;

import com.krdevops.springai.service.write.ProjectRootRegistryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProjectRootRegistryRepository implements ProjectRootRegistryPort {

    private final JdbcTemplate jdbc;

    public ProjectRootRegistryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean isRegistered(String canonicalRoot) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM AI_PROJECT_ROOT_REGISTRY WHERE PROJECT_ROOT=?", Integer.class, canonicalRoot);
        return count != null && count > 0;
    }

    @Override
    public void register(String canonicalRoot, String registeredBy, String registrationSource) {
        jdbc.update(
                "INSERT IGNORE INTO AI_PROJECT_ROOT_REGISTRY (PROJECT_ROOT, REGISTERED_BY, REGISTRATION_SOURCE) "
                        + "VALUES (?, ?, ?)",
                canonicalRoot, registeredBy, registrationSource);
    }
}

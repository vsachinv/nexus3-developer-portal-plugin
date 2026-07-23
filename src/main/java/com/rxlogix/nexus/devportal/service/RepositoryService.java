package com.rxlogix.nexus.devportal.service;

import com.rxlogix.nexus.devportal.model.RepositoryInfo;

import java.util.List;
import java.util.Optional;

public interface RepositoryService {

    /** Returns all repositories that have at least one supported format. */
    List<RepositoryInfo> listAll();

    /** Returns only repositories for a specific format, e.g. "npm" or "maven2". */
    List<RepositoryInfo> listByFormat(String format);

    /** Look up a single repository by name. */
    Optional<RepositoryInfo> findByName(String name);
}

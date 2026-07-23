package com.rxlogix.nexus.devportal.service.impl;

import com.rxlogix.nexus.devportal.internal.NexusBeanLocator;
import com.rxlogix.nexus.devportal.internal.RepositoryAccessFilter;
import com.rxlogix.nexus.devportal.model.RepositoryInfo;
import com.rxlogix.nexus.devportal.service.RepositoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.manager.RepositoryManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class RepositoryServiceImpl implements RepositoryService {

    private static final Logger log = LoggerFactory.getLogger(RepositoryServiceImpl.class);

    private static final Set<String> SUPPORTED_FORMATS =
            Set.of("npm", "maven2", "pypi", "docker", "nuget", "helm", "go");

    // Resolved lazily at request time — RepositoryManager lives in Nexus's child
    // Spring context, which does not exist yet when this bean is created.
    private final NexusBeanLocator beanLocator;
    private final RepositoryAccessFilter accessFilter;

    public RepositoryServiceImpl(NexusBeanLocator beanLocator, RepositoryAccessFilter accessFilter) {
        this.beanLocator = beanLocator;
        this.accessFilter = accessFilter;
    }

    private RepositoryManager repositoryManager() {
        return beanLocator.lookup(RepositoryManager.class).orElseGet(() -> {
            log.warn("RepositoryManager not available in any known Spring context");
            return null;
        });
    }

    @Override
    public List<RepositoryInfo> listAll() {
        return browseReadable().stream()
                .filter(this::isSupportedFormat)
                .map(this::toRepositoryInfo)
                .collect(Collectors.toList());
    }

    @Override
    public List<RepositoryInfo> listByFormat(String format) {
        if (format == null || format.isBlank()) {
            return listAll();
        }
        return browseReadable().stream()
                .filter(r -> format.equalsIgnoreCase(r.getFormat().getValue()))
                .map(this::toRepositoryInfo)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<RepositoryInfo> findByName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        // Match against the permission-filtered set so an unreadable repository
        // is reported as not found rather than disclosed.
        return browseReadable().stream()
                .filter(r -> name.equals(r.getName()))
                .findFirst()
                .map(this::toRepositoryInfo);
    }

    /** All repositories the current subject may read, or empty on failure. */
    private List<Repository> browseReadable() {
        RepositoryManager rm = repositoryManager();
        if (rm == null) {
            return List.of();
        }
        try {
            List<Repository> all = StreamSupport.stream(rm.browse().spliterator(), false)
                    .collect(Collectors.toList());
            return accessFilter.readable(all);
        } catch (Exception e) {
            log.error("Failed to list repositories", e);
            return List.of();
        }
    }

    private boolean isSupportedFormat(Repository repository) {
        return SUPPORTED_FORMATS.contains(repository.getFormat().getValue().toLowerCase());
    }

    private RepositoryInfo toRepositoryInfo(Repository repository) {
        return RepositoryInfo.builder()
                .name(repository.getName())
                .format(repository.getFormat().getValue())
                .type(repository.getType().getValue())
                .online(repository.getConfiguration().isOnline())
                .url("/repository/" + repository.getName())
                .componentCount(0L)
                .build();
    }
}

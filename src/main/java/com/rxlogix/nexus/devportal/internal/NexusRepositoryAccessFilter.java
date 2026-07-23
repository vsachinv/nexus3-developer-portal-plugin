package com.rxlogix.nexus.devportal.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.security.RepositoryPermissionChecker;

import java.util.List;
import java.util.Optional;

/**
 * Nexus-backed {@link RepositoryAccessFilter}.
 *
 * The portal's REST resources run behind Nexus's auth filter, so the active
 * Shiro subject (an authenticated user, or the anonymous user when anonymous
 * access is enabled) is bound to the request thread. {@link RepositoryPermissionChecker}
 * evaluates repository-view permissions and content selectors against that
 * subject exactly as Nexus's own browse/search endpoints do.
 *
 * Fails CLOSED: if the checker cannot be resolved, no repositories are returned
 * rather than silently exposing content the subject may not be entitled to see.
 */
public class NexusRepositoryAccessFilter implements RepositoryAccessFilter {

    private static final Logger log = LoggerFactory.getLogger(NexusRepositoryAccessFilter.class);

    private final NexusBeanLocator beanLocator;

    public NexusRepositoryAccessFilter(NexusBeanLocator beanLocator) {
        this.beanLocator = beanLocator;
    }

    @Override
    public List<Repository> readable(List<Repository> repositories) {
        if (repositories.isEmpty()) {
            return repositories;
        }
        Optional<RepositoryPermissionChecker> checker =
                beanLocator.lookup(RepositoryPermissionChecker.class);
        if (checker.isEmpty()) {
            log.warn("RepositoryPermissionChecker unavailable; denying repository access "
                    + "(fail-closed) so unauthorized content is never exposed");
            return List.of();
        }
        return checker.get().userCanBrowseRepositories(repositories);
    }
}

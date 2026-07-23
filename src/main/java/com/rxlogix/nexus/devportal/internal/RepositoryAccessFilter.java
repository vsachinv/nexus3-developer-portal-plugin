package com.rxlogix.nexus.devportal.internal;

import org.sonatype.nexus.repository.Repository;

import java.util.List;

/**
 * Authorization gate for the portal: narrows a set of repositories to those the
 * current request's subject is permitted to read or browse.
 *
 * Defined as an interface so the security decision has a single implementation
 * ({@link NexusRepositoryAccessFilter}) shared by every provider and service,
 * and so it can be substituted in tests without depending on Nexus internals.
 */
@FunctionalInterface
public interface RepositoryAccessFilter {

    /** Returns the subset of {@code repositories} readable by the current subject. */
    List<Repository> readable(List<Repository> repositories);
}

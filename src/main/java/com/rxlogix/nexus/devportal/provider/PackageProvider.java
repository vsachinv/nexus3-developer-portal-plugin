package com.rxlogix.nexus.devportal.provider;

import com.rxlogix.nexus.devportal.model.PackageDetail;
import com.rxlogix.nexus.devportal.model.PackageSummary;
import com.rxlogix.nexus.devportal.model.PagedResult;
import com.rxlogix.nexus.devportal.model.SearchRequest;

import java.util.List;
import java.util.Optional;

/**
 * Abstracts package-format-specific operations over a Nexus repository.
 * Implementations exist per format: npm, maven, pypi, docker, etc.
 */
public interface PackageProvider {

    /** Returns the Nexus format identifier this provider handles, e.g. "npm", "maven2". */
    String getFormat();

    /** Human-readable label shown in the UI, e.g. "npm", "Maven". */
    String getDisplayName();

    /** Search packages across all repositories matching this format. */
    PagedResult<PackageSummary> search(SearchRequest request);

    /** Return recently published packages (descending by publish time). */
    List<PackageSummary> recent(int limit);

    /** Return the most downloaded / most depended-upon packages. */
    List<PackageSummary> popular(int limit);

    /** Look up a single package summary by name (and optional group for Maven). */
    Optional<PackageSummary> findPackage(String repository, String group, String name);

    /**
     * Build the full detail view for a package: metadata plus version list,
     * format-specific install snippets, and (when available) README/homepage.
     *
     * @param version the version to describe, or {@code null}/blank for the latest.
     */
    Optional<PackageDetail> detail(String repository, String group, String name, String version);

    /** List all recorded versions for a package, newest first. */
    List<String> listVersions(String repository, String group, String name);

    /** Returns true if this format is supported by the currently running Nexus instance. */
    boolean isAvailable();
}

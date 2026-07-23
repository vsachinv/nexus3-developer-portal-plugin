package com.rxlogix.nexus.devportal.service;

import com.rxlogix.nexus.devportal.model.PackageDetail;
import com.rxlogix.nexus.devportal.model.PackageSummary;
import com.rxlogix.nexus.devportal.model.PagedResult;
import com.rxlogix.nexus.devportal.model.SearchRequest;

import java.util.List;
import java.util.Optional;

public interface SearchService {

    /** Full-text search across all supported formats and repositories. */
    PagedResult<PackageSummary> search(SearchRequest request);

    /** Returns recently published packages across all formats, limit capped at 50. */
    List<PackageSummary> recent(int limit);

    /** Returns popular packages (by download count or dependency count), limit capped at 50. */
    List<PackageSummary> popular(int limit);

    /**
     * Full detail for one package, resolved by the provider matching {@code format}.
     *
     * @param version the version to describe, or {@code null}/blank for the latest.
     */
    Optional<PackageDetail> packageDetail(String format, String repository, String group,
                                          String name, String version);
}

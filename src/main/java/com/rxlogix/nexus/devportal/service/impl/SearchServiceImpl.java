package com.rxlogix.nexus.devportal.service.impl;

import com.rxlogix.nexus.devportal.model.PackageDetail;
import com.rxlogix.nexus.devportal.model.PackageSummary;
import com.rxlogix.nexus.devportal.model.PagedResult;
import com.rxlogix.nexus.devportal.model.SearchRequest;
import com.rxlogix.nexus.devportal.provider.PackageProvider;
import com.rxlogix.nexus.devportal.service.SearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class SearchServiceImpl implements SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchServiceImpl.class);
    private static final int MAX_LIMIT = 50;

    private final Map<String, PackageProvider> providers;

    public SearchServiceImpl(Map<String, PackageProvider> providers) {
        this.providers = providers;
    }

    @Override
    public PagedResult<PackageSummary> search(SearchRequest request) {
        List<PackageSummary> allResults = new ArrayList<>();

        for (PackageProvider provider : activeProviders(request.getFormat())) {
            try {
                PagedResult<PackageSummary> result = provider.search(request);
                allResults.addAll(result.getItems());
            } catch (Exception e) {
                log.warn("Search failed for provider: {}", provider.getFormat(), e);
            }
        }

        allResults.sort(Comparator.comparing(PackageSummary::getName));

        int from = request.getPage() * request.getPageSize();
        int to = Math.min(from + request.getPageSize(), allResults.size());
        List<PackageSummary> page = from >= allResults.size()
                ? Collections.emptyList()
                : allResults.subList(from, to);

        return PagedResult.of(page, request.getPage(), request.getPageSize(), allResults.size());
    }

    @Override
    public List<PackageSummary> recent(int limit) {
        int cap = Math.min(limit, MAX_LIMIT);
        List<PackageSummary> results = new ArrayList<>();

        for (PackageProvider provider : activeProviders(null)) {
            try {
                results.addAll(provider.recent(cap));
            } catch (Exception e) {
                log.warn("Recent query failed for provider: {}", provider.getFormat(), e);
            }
        }

        return results.stream()
                .sorted(Comparator.comparingLong(PackageSummary::getLastModified).reversed())
                .limit(cap)
                .collect(Collectors.toList());
    }

    @Override
    public List<PackageSummary> popular(int limit) {
        int cap = Math.min(limit, MAX_LIMIT);
        List<PackageSummary> results = new ArrayList<>();

        for (PackageProvider provider : activeProviders(null)) {
            try {
                results.addAll(provider.popular(cap));
            } catch (Exception e) {
                log.warn("Popular query failed for provider: {}", provider.getFormat(), e);
            }
        }

        return results.stream()
                .limit(cap)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<PackageDetail> packageDetail(String format, String repository,
                                                 String group, String name, String version) {
        if (format == null || format.isBlank()) {
            return Optional.empty();
        }
        PackageProvider provider = providers.get(format.toLowerCase());
        if (provider == null || !provider.isAvailable()) {
            return Optional.empty();
        }
        try {
            return provider.detail(repository, group, name, version);
        } catch (Exception e) {
            log.warn("Package detail lookup failed for {}:{}", format, name, e);
            return Optional.empty();
        }
    }

    private List<PackageProvider> activeProviders(String formatFilter) {
        return providers.values().stream()
                .filter(PackageProvider::isAvailable)
                .filter(p -> formatFilter == null || formatFilter.isBlank()
                        || p.getFormat().equalsIgnoreCase(formatFilter))
                .collect(Collectors.toList());
    }
}

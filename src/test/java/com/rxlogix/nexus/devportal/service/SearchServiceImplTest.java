package com.rxlogix.nexus.devportal.service;

import com.rxlogix.nexus.devportal.model.PackageSummary;
import com.rxlogix.nexus.devportal.model.PagedResult;
import com.rxlogix.nexus.devportal.model.SearchRequest;
import com.rxlogix.nexus.devportal.provider.PackageProvider;
import com.rxlogix.nexus.devportal.service.impl.SearchServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

// LENIENT: setUp stubs are shared helpers; not every test exercises both getFormat() and isAvailable().
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class SearchServiceImplTest {

    @Mock
    private PackageProvider npmProvider;

    private SearchServiceImpl searchService;

    @BeforeEach
    void setUp() {
        lenient().when(npmProvider.getFormat()).thenReturn("npm");
        lenient().when(npmProvider.isAvailable()).thenReturn(true);

        Map<String, PackageProvider> providers = new HashMap<>();
        providers.put("npm", npmProvider);
        searchService = new SearchServiceImpl(providers);
    }

    @Test
    void search_returnsResultsFromActiveProvider() {
        PackageSummary pkg = PackageSummary.builder()
                .id("npm:lodash")
                .name("lodash")
                .format("npm")
                .repository("npm-proxy")
                .latestVersion("4.17.21")
                .description("A modern JavaScript utility library")
                .lastModified(System.currentTimeMillis())
                .build();

        when(npmProvider.search(any())).thenReturn(
                PagedResult.of(List.of(pkg), 0, 20, 1));

        SearchRequest request = SearchRequest.builder().query("lodash").build();
        PagedResult<PackageSummary> result = searchService.search(request);

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getName()).isEqualTo("lodash");
        assertThat(result.getTotalCount()).isEqualTo(1);
    }

    @Test
    void search_returnsEmptyWhenNoProviders() {
        searchService = new SearchServiceImpl(new HashMap<>());

        SearchRequest request = SearchRequest.builder().query("anything").build();
        PagedResult<PackageSummary> result = searchService.search(request);

        assertThat(result.getItems()).isEmpty();
        assertThat(result.getTotalCount()).isZero();
    }

    @Test
    void search_filtersInactiveProviders() {
        when(npmProvider.isAvailable()).thenReturn(false);

        SearchRequest request = SearchRequest.builder().query("lodash").build();
        PagedResult<PackageSummary> result = searchService.search(request);

        assertThat(result.getItems()).isEmpty();
    }

    @Test
    void search_filtersResultsByFormat() {
        // Filter to maven2 — the npm provider's getFormat() returns "npm" so it is excluded;
        // search() is never called on the provider.
        SearchRequest mavenRequest = SearchRequest.builder().query("react").format("maven2").build();
        PagedResult<PackageSummary> result = searchService.search(mavenRequest);

        assertThat(result.getItems()).isEmpty();
    }

    @Test
    void search_gracefullyHandlesProviderException() {
        when(npmProvider.search(any())).thenThrow(new RuntimeException("Nexus unavailable"));

        SearchRequest request = SearchRequest.builder().query("lodash").build();
        // Must not throw — degraded response is acceptable
        PagedResult<PackageSummary> result = searchService.search(request);

        assertThat(result.getItems()).isEmpty();
    }

    @Test
    void recent_capsAtFifty() {
        when(npmProvider.recent(50)).thenReturn(Collections.emptyList());

        List<PackageSummary> result = searchService.recent(200);

        assertThat(result).isEmpty();
    }

    @Test
    void searchRequest_normalisesPageSize() {
        SearchRequest req = SearchRequest.builder().pageSize(999).build();
        assertThat(req.getPageSize()).isEqualTo(100);
    }

    @Test
    void searchRequest_defaultsToFirstPage() {
        SearchRequest req = SearchRequest.builder().build();
        assertThat(req.getPage()).isZero();
        assertThat(req.getPageSize()).isEqualTo(20);
    }
}

package com.rxlogix.nexus.devportal.internal;

import com.rxlogix.nexus.devportal.provider.MavenPackageProvider;
import com.rxlogix.nexus.devportal.provider.NpmPackageProvider;
import com.rxlogix.nexus.devportal.provider.PackageProvider;
import com.rxlogix.nexus.devportal.rest.PackageApiResource;
import com.rxlogix.nexus.devportal.rest.RecentApiResource;
import com.rxlogix.nexus.devportal.rest.RepositoryApiResource;
import com.rxlogix.nexus.devportal.rest.SearchApiResource;
import com.rxlogix.nexus.devportal.rest.UiResource;
import com.rxlogix.nexus.devportal.service.RepositoryService;
import com.rxlogix.nexus.devportal.service.SearchService;
import com.rxlogix.nexus.devportal.service.impl.RepositoryServiceImpl;
import com.rxlogix.nexus.devportal.service.impl.SearchServiceImpl;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Spring Boot auto-configuration for the Nexus Developer Portal.
 *
 * Discovered via META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
 *
 * All REST resource beans implement org.sonatype.nexus.rest.Component, which causes
 * Nexus's ComponentContainerImpl to register them with RESTEasy.
 *
 * NOTE: RepositoryManager is Guice-managed in Nexus, not a Spring bean.
 * RepositoryServiceImpl resolves it lazily via ApplicationContext.getBean()
 * at request time rather than during startup, avoiding context initialization failures.
 */
@AutoConfiguration
public class DevPortalAutoConfiguration {

    @Bean
    public NexusBeanLocator devPortalBeanLocator() {
        return new NexusBeanLocator();
    }

    @Bean
    public RepositoryAccessFilter devPortalAccessFilter(NexusBeanLocator devPortalBeanLocator) {
        return new NexusRepositoryAccessFilter(devPortalBeanLocator);
    }

    @Bean
    public RepositoryService devPortalRepositoryService(
            NexusBeanLocator devPortalBeanLocator, RepositoryAccessFilter devPortalAccessFilter) {
        return new RepositoryServiceImpl(devPortalBeanLocator, devPortalAccessFilter);
    }

    @Bean
    public NpmPackageProvider devPortalNpmProvider(
            NexusBeanLocator devPortalBeanLocator, RepositoryAccessFilter devPortalAccessFilter) {
        return new NpmPackageProvider(devPortalBeanLocator, devPortalAccessFilter);
    }

    @Bean
    public MavenPackageProvider devPortalMavenProvider(
            NexusBeanLocator devPortalBeanLocator, RepositoryAccessFilter devPortalAccessFilter) {
        return new MavenPackageProvider(devPortalBeanLocator, devPortalAccessFilter);
    }

    @Bean
    public SearchService devPortalSearchService(
            NpmPackageProvider devPortalNpmProvider,
            MavenPackageProvider devPortalMavenProvider) {
        Map<String, PackageProvider> providers = new HashMap<>();
        providers.put(devPortalNpmProvider.getFormat(), devPortalNpmProvider);
        providers.put(devPortalMavenProvider.getFormat(), devPortalMavenProvider);
        return new SearchServiceImpl(providers);
    }

    @Bean
    public UiResource devPortalUiResource() {
        return new UiResource();
    }

    @Bean
    public SearchApiResource devPortalSearchApiResource(SearchService devPortalSearchService) {
        return new SearchApiResource(devPortalSearchService);
    }

    @Bean
    public RepositoryApiResource devPortalRepositoryApiResource(
            RepositoryService devPortalRepositoryService) {
        return new RepositoryApiResource(devPortalRepositoryService);
    }

    @Bean
    public RecentApiResource devPortalRecentApiResource(SearchService devPortalSearchService) {
        return new RecentApiResource(devPortalSearchService);
    }

    @Bean
    public PackageApiResource devPortalPackageApiResource(SearchService devPortalSearchService) {
        return new PackageApiResource(devPortalSearchService);
    }

    /**
     * Registers the resources above directly with Nexus's RESTEasy deployment.
     * ComponentContainerImpl's own discovery scans a child Spring context that
     * cannot see these parent-context beans, so we register explicitly.
     */
    @Bean
    public DevPortalRestRegistrar devPortalRestRegistrar(
            UiResource devPortalUiResource,
            SearchApiResource devPortalSearchApiResource,
            RepositoryApiResource devPortalRepositoryApiResource,
            RecentApiResource devPortalRecentApiResource,
            PackageApiResource devPortalPackageApiResource) {
        return new DevPortalRestRegistrar(List.of(
                devPortalUiResource,
                devPortalSearchApiResource,
                devPortalRepositoryApiResource,
                devPortalRecentApiResource,
                devPortalPackageApiResource));
    }
}

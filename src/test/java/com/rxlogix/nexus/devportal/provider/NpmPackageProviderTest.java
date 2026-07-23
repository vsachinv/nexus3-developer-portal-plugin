package com.rxlogix.nexus.devportal.provider;

import com.rxlogix.nexus.devportal.internal.NexusBeanLocator;
import com.rxlogix.nexus.devportal.model.PackageDetail;
import com.rxlogix.nexus.devportal.model.PackageSummary;
import com.rxlogix.nexus.devportal.model.PagedResult;
import com.rxlogix.nexus.devportal.model.SearchRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.sonatype.nexus.common.collect.NestedAttributesMap;
import org.sonatype.nexus.common.entity.Continuation;
import org.sonatype.nexus.repository.Format;
import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.content.facet.ContentFacet;
import org.sonatype.nexus.repository.content.fluent.FluentComponent;
import org.sonatype.nexus.repository.content.fluent.FluentComponents;
import org.sonatype.nexus.repository.manager.RepositoryManager;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NpmPackageProviderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private NexusBeanLocator beanLocator;
    private RepositoryManager repositoryManager;
    private FluentComponents components;
    private NpmPackageProvider provider;

    @BeforeEach
    void setUp() {
        beanLocator = mock(NexusBeanLocator.class);
        repositoryManager = mock(RepositoryManager.class);
        components = mock(FluentComponents.class);

        when(beanLocator.lookup(RepositoryManager.class))
                .thenReturn(Optional.of(repositoryManager));

        Format npmFormat = mock(Format.class);
        when(npmFormat.getValue()).thenReturn("npm");

        ContentFacet contentFacet = mock(ContentFacet.class);
        when(contentFacet.components()).thenReturn(components);

        Repository repo = mock(Repository.class);
        when(repo.getName()).thenReturn("npm-hosted");
        when(repo.getFormat()).thenReturn(npmFormat);
        when(repo.optionalFacet(ContentFacet.class)).thenReturn(Optional.of(contentFacet));

        when(repositoryManager.browse()).thenReturn(List.of(repo));

        // Permissive access filter — permission enforcement is tested separately.
        provider = new NpmPackageProvider(beanLocator, repositories -> repositories);
    }

    /** Feeds a single page of components, then an empty page to end pagination. */
    @SuppressWarnings("unchecked")
    private void givenComponents(FluentComponent... comps) {
        when(components.browseWithAssets(any(Integer.class), isNull()))
                .thenReturn(new TestContinuation<>(List.of(comps)));
        when(components.browseWithAssets(any(Integer.class), eq(TestContinuation.TOKEN)))
                .thenReturn(new TestContinuation<>(List.of()));
    }

    private FluentComponent npmComponent(String namespace, String name, String version,
                                         long epochMillis, String description) {
        FluentComponent c = mock(FluentComponent.class);
        when(c.namespace()).thenReturn(namespace);
        when(c.name()).thenReturn(name);
        when(c.version()).thenReturn(version);
        when(c.lastUpdated()).thenReturn(
                OffsetDateTime.ofInstant(java.time.Instant.ofEpochMilli(epochMillis), ZoneOffset.UTC));
        NestedAttributesMap npm = mock(NestedAttributesMap.class);
        when(npm.get("description", String.class)).thenReturn(description);
        when(c.attributes("npm")).thenReturn(npm);
        when(c.assets()).thenReturn(List.of());
        return c;
    }

    @Test
    void scopedPackageGetsAtPrefix() {
        givenComponents(npmComponent("devportal", "world", "2.3.1", 2000L, "greets the world"));

        List<PackageSummary> recent = provider.recent(10);

        assertThat(recent).hasSize(1);
        PackageSummary pkg = recent.get(0);
        assertThat(pkg.getName()).isEqualTo("@devportal/world");
        assertThat(pkg.getGroup()).isEqualTo("devportal");
        assertThat(pkg.getDescription()).isEqualTo("greets the world");
    }

    @Test
    void unscopedPackageHasNoGroup() {
        givenComponents(npmComponent("", "hello-portal", "1.0.0", 1000L, "a tiny package"));

        List<PackageSummary> recent = provider.recent(10);

        assertThat(recent).hasSize(1);
        assertThat(recent.get(0).getName()).isEqualTo("hello-portal");
        assertThat(recent.get(0).getGroup()).isNull();
    }

    @Test
    void multipleVersionsDedupeToNewest() {
        givenComponents(
                npmComponent("", "hello-portal", "1.0.0", 1000L, "v1"),
                npmComponent("", "hello-portal", "1.1.0", 2000L, "v1.1"));

        List<PackageSummary> recent = provider.recent(10);

        assertThat(recent).hasSize(1);
        assertThat(recent.get(0).getLatestVersion()).isEqualTo("1.1.0");
    }

    @Test
    void searchMatchesNameOrDescriptionCaseInsensitively() {
        givenComponents(
                npmComponent("devportal", "world", "2.3.1", 2000L, "greets the world"),
                npmComponent("", "hello-portal", "1.1.0", 1000L, "a tiny package"));

        PagedResult<PackageSummary> byName = provider.search(
                SearchRequest.builder().query("WORLD").build());
        assertThat(byName.getItems()).extracting(PackageSummary::getName)
                .containsExactly("@devportal/world");

        PagedResult<PackageSummary> byDescription = provider.search(
                SearchRequest.builder().query("tiny").build());
        assertThat(byDescription.getItems()).extracting(PackageSummary::getName)
                .containsExactly("hello-portal");

        PagedResult<PackageSummary> noMatch = provider.search(
                SearchRequest.builder().query("zzz").build());
        assertThat(noMatch.getItems()).isEmpty();
        assertThat(noMatch.getTotalCount()).isZero();
    }

    @Test
    void detailBuildsInstallCommandVersionsAndScopedName() {
        givenComponents(
                npmComponent("devportal", "world", "2.3.1", 2000L, "greets the world"));
        when(components.versions("devportal", "world"))
                .thenReturn(List.of("2.3.1", "2.0.0", "1.0.0"));

        Optional<PackageDetail> detail =
                provider.detail("npm-hosted", "devportal", "@devportal/world", null);

        assertThat(detail).isPresent();
        PackageDetail d = detail.get();
        assertThat(d.getName()).isEqualTo("@devportal/world");
        assertThat(d.getInstallSnippets()).extracting("label", "code")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("npm", "npm install @devportal/world"),
                        org.assertj.core.groups.Tuple.tuple("yarn", "yarn add @devportal/world"),
                        org.assertj.core.groups.Tuple.tuple("pnpm", "pnpm add @devportal/world"));
        assertThat(d.getLatestVersion()).isEqualTo("2.3.1");
        assertThat(d.getVersions()).containsExactly("2.3.1", "2.0.0", "1.0.0");
        assertThat(d.getRepository()).isEqualTo("npm-hosted");
        assertThat(d.getGroup()).isEqualTo("devportal");
    }

    @Test
    void packageRootMetadataParsesObjectAuthorAndDependencies() throws Exception {
        JsonNode doc = MAPPER.readTree("""
            {
              "dist-tags": {"latest": "1.1.0"},
              "versions": {
                "1.1.0": {
                  "author": {"name": "Ada Lovelace", "email": "ada@example.com"},
                  "dependencies": {"lodash": "^4.17.0", "chalk": "~5.0.0"}
                }
              }
            }
            """);
        PackageDetail.Builder builder = PackageDetail.builder();

        NpmPackageProvider.applyPackageRootMetadata(doc, "1.1.0", builder);
        PackageDetail d = builder.build();

        assertThat(d.getAuthor()).isEqualTo("Ada Lovelace <ada@example.com>");
        assertThat(d.getDependencies())
                .containsEntry("lodash", "^4.17.0")
                .containsEntry("chalk", "~5.0.0");
    }

    @Test
    void packageRootMetadataParsesStringAuthorAndNoDependencies() throws Exception {
        JsonNode doc = MAPPER.readTree("""
            {"versions": {"1.0.0": {"author": "Grace Hopper"}}}
            """);
        PackageDetail.Builder builder = PackageDetail.builder();

        NpmPackageProvider.applyPackageRootMetadata(doc, "1.0.0", builder);
        PackageDetail d = builder.build();

        assertThat(d.getAuthor()).isEqualTo("Grace Hopper");
        assertThat(d.getDependencies()).isEmpty();
    }

    @Test
    void detailReturnsEmptyWhenPackageAbsent() {
        givenComponents(npmComponent("", "hello-portal", "1.0.0", 1000L, "x"));

        assertThat(provider.detail("npm-hosted", "", "does-not-exist", null)).isEmpty();
    }

    @Test
    void deniedRepositoriesYieldNoResults() {
        givenComponents(npmComponent("", "hello-portal", "1.0.0", 1000L, "x"));
        // Access filter denies everything (e.g. subject lacks read permission).
        NpmPackageProvider denied = new NpmPackageProvider(beanLocator, repositories -> List.of());

        assertThat(denied.recent(10)).isEmpty();
        assertThat(denied.search(SearchRequest.builder().query("hello").build()).getItems())
                .isEmpty();
    }

    @Test
    void extractsChangelogFromTarball() throws Exception {
        byte[] tgz = npmTarball("package/CHANGELOG.md",
                "# Changelog\n\n## 1.1.0\n- added things\n");

        String changelog = NpmPackageProvider.extractChangelog(
                new java.io.ByteArrayInputStream(tgz));

        assertThat(changelog).contains("## 1.1.0").contains("added things");
    }

    @Test
    void returnsNullWhenTarballHasNoChangelog() throws Exception {
        byte[] tgz = npmTarball("package/index.js", "module.exports = {};\n");

        assertThat(NpmPackageProvider.extractChangelog(new java.io.ByteArrayInputStream(tgz)))
                .isNull();
    }

    /** Builds a minimal gzipped-tar (npm .tgz) containing one file. */
    private static byte[] npmTarball(String entryName, String content) throws Exception {
        var bytes = content.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var buffer = new java.io.ByteArrayOutputStream();
        try (var gz = new org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream(buffer);
             var tar = new org.apache.commons.compress.archivers.tar.TarArchiveOutputStream(gz)) {
            var entry = new org.apache.commons.compress.archivers.tar.TarArchiveEntry(entryName);
            entry.setSize(bytes.length);
            tar.putArchiveEntry(entry);
            tar.write(bytes);
            tar.closeArchiveEntry();
        }
        return buffer.toByteArray();
    }

    @Test
    void isAvailableReflectsRepositoryManagerPresence() {
        assertThat(provider.isAvailable()).isTrue();

        when(beanLocator.lookup(RepositoryManager.class)).thenReturn(Optional.empty());
        assertThat(provider.isAvailable()).isFalse();
    }

    /** Minimal real Continuation so mocked browse pages iterate cleanly. */
    private static final class TestContinuation<E> extends ArrayList<E> implements Continuation<E> {
        static final String TOKEN = "next";

        TestContinuation(List<E> items) {
            super(items);
        }

        @Override
        public String nextContinuationToken() {
            return isEmpty() ? null : TOKEN;
        }
    }
}

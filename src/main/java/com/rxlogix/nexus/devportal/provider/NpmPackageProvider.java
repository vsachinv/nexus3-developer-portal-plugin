package com.rxlogix.nexus.devportal.provider;

import com.rxlogix.nexus.devportal.internal.NexusBeanLocator;
import com.rxlogix.nexus.devportal.internal.RepositoryAccessFilter;
import com.rxlogix.nexus.devportal.model.InstallSnippet;
import com.rxlogix.nexus.devportal.model.Link;
import com.rxlogix.nexus.devportal.model.PackageDetail;
import com.rxlogix.nexus.devportal.model.PackageSummary;
import com.rxlogix.nexus.devportal.model.PagedResult;
import com.rxlogix.nexus.devportal.model.SearchRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.nexus.common.entity.Continuation;
import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.content.AssetBlob;
import org.sonatype.nexus.repository.content.facet.ContentFacet;
import org.sonatype.nexus.repository.content.fluent.FluentAsset;
import org.sonatype.nexus.repository.content.fluent.FluentComponent;
import org.sonatype.nexus.repository.manager.RepositoryManager;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * npm implementation of {@link PackageProvider}, backed by Nexus's datastore
 * content API (the SQL-backed model used in Nexus 3.94 CE).
 *
 * npm stores one component per (name, version) pair, so a package with three
 * published versions is three components sharing a name. Search and recent
 * therefore dedupe by package name, keeping the newest version of each.
 *
 * RepositoryManager is resolved lazily through {@link NexusBeanLocator} because
 * it lives in Nexus's child Spring context, not our own.
 */
public class NpmPackageProvider implements PackageProvider {

    private static final Logger log = LoggerFactory.getLogger(NpmPackageProvider.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String FORMAT = "npm";

    // npm package.json fields, extracted into component attributes under the "npm" child.
    private static final String NPM_ATTR = "npm";
    private static final String P_DESCRIPTION = "description";
    private static final String P_README = "readme";
    private static final String P_HOMEPAGE = "homepage";

    // Page size when walking the datastore, and a hard ceiling on how many
    // components a single query will scan so a huge repo can't stall a request.
    private static final int BROWSE_PAGE = 100;
    private static final int MAX_SCAN = 5000;
    private static final int MAX_CHANGELOG_BYTES = 512 * 1024;

    private final NexusBeanLocator beanLocator;
    private final RepositoryAccessFilter accessFilter;

    public NpmPackageProvider(NexusBeanLocator beanLocator, RepositoryAccessFilter accessFilter) {
        this.beanLocator = beanLocator;
        this.accessFilter = accessFilter;
    }

    @Override
    public String getFormat() {
        return FORMAT;
    }

    @Override
    public String getDisplayName() {
        return "npm";
    }

    @Override
    public boolean isAvailable() {
        return repositoryManager().isPresent();
    }

    @Override
    public PagedResult<PackageSummary> search(SearchRequest request) {
        String query = request.getQuery() == null ? "" : request.getQuery().toLowerCase();

        List<PackageSummary> latestPerPackage = latestPerPackage(request.getRepository()).stream()
                .filter(p -> query.isEmpty()
                        || p.getName().toLowerCase().contains(query)
                        || (p.getDescription() != null
                                && p.getDescription().toLowerCase().contains(query)))
                .sorted(Comparator.comparing(PackageSummary::getName))
                .collect(Collectors.toList());

        int from = request.getPage() * request.getPageSize();
        if (from >= latestPerPackage.size()) {
            return PagedResult.of(List.of(), request.getPage(), request.getPageSize(),
                    latestPerPackage.size());
        }
        int to = Math.min(from + request.getPageSize(), latestPerPackage.size());
        return PagedResult.of(latestPerPackage.subList(from, to), request.getPage(),
                request.getPageSize(), latestPerPackage.size());
    }

    @Override
    public List<PackageSummary> recent(int limit) {
        return latestPerPackage(null).stream()
                .sorted(Comparator.comparingLong(PackageSummary::getLastModified).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    public List<PackageSummary> popular(int limit) {
        // Nexus CE does not track download counts in the content store, so
        // "popular" has no meaningful signal yet. Fall back to recent.
        return recent(limit);
    }

    @Override
    public Optional<PackageSummary> findPackage(String repository, String group, String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return latestPerPackage(repository).stream()
                .filter(p -> p.getName().equalsIgnoreCase(name))
                .findFirst();
    }

    @Override
    public Optional<PackageDetail> detail(String repository, String group, String name,
                                          String version) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }

        FluentComponent latest = null;      // newest version overall
        FluentComponent selected = null;    // the requested version, if any
        ContentFacet facetOf = null;
        String repoName = null;
        for (Repository repo : npmRepositories(repository)) {
            Optional<ContentFacet> facet = contentFacet(repo);
            if (facet.isEmpty()) {
                continue;
            }
            for (FluentComponent component : browseAll(facet.get(), repo.getName())) {
                if (!name.equalsIgnoreCase(displayName(component))
                        && !name.equalsIgnoreCase(component.name())) {
                    continue;
                }
                if (latest == null || isNewer(component, latest)) {
                    latest = component;
                    facetOf = facet.get();
                    repoName = repo.getName();
                }
                if (version != null && !version.isBlank()
                        && version.equals(component.version())) {
                    selected = component;
                }
            }
        }
        if (latest == null) {
            return Optional.empty();
        }
        // A specific version was requested but not found.
        if (version != null && !version.isBlank() && selected == null) {
            return Optional.empty();
        }
        FluentComponent shown = selected != null ? selected : latest;

        String fullName = displayName(shown);
        String actualNamespace = shown.namespace() == null ? "" : shown.namespace();
        String latestVersion = latest.version();
        boolean pinned = !shown.version().equals(latestVersion);

        PackageDetail.Builder builder = PackageDetail.builder()
                .name(fullName)
                .format(FORMAT)
                .repository(repoName)
                .group(actualNamespace.isBlank() ? null : actualNamespace)
                .version(shown.version())
                .latestVersion(latestVersion)
                .description(extractString(shown, P_DESCRIPTION))
                .readme(extractString(shown, P_README))
                .changelog(readChangelog(shown))
                .links(npmLinks(shown))
                .installSnippets(npmSnippets(fullName, shown.version(), pinned))
                .publishedBy(publisherOf(shown))
                .lastModified(epochMillis(shown))
                .versions(listVersions(repoName, actualNamespace, shown.name()));

        // author + dependencies live only in the package-root metadata document,
        // not in the searchable format attributes — read and parse it.
        enrichFromPackageRoot(facetOf, fullName, shown.version(), builder);

        return Optional.of(builder.build());
    }

    /**
     * Reads a CHANGELOG from the version's tarball, if the package ships one.
     * npm records the README in metadata but not the changelog, so this streams
     * the .tgz and extracts a top-level CHANGELOG/CHANGES/HISTORY file.
     */
    private String readChangelog(FluentComponent component) {
        try {
            for (FluentAsset asset : component.assets()) {
                if (asset.path() == null || !asset.path().endsWith(".tgz")) {
                    continue;
                }
                try (InputStream tgz = asset.download().openInputStream()) {
                    return extractChangelog(tgz);
                }
            }
        } catch (Exception e) {
            log.debug("Could not read changelog for '{}': {}",
                    component.name(), e.getMessage());
        }
        return null;
    }

    /**
     * Extracts a top-level changelog file from an npm .tgz stream, capped at
     * {@link #MAX_CHANGELOG_BYTES}. Package-visible for unit testing. Returns
     * null if the tarball has no recognizable changelog.
     */
    static String extractChangelog(InputStream tgzStream) throws IOException {
        try (GzipCompressorInputStream gz = new GzipCompressorInputStream(tgzStream);
             TarArchiveInputStream tar = new TarArchiveInputStream(gz)) {
            ArchiveEntry entry;
            while ((entry = tar.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String nm = entry.getName().replaceFirst("^\\./", "").toLowerCase();
                if (nm.matches("(package/)?(changelog|changes|history)\\.(md|markdown|txt)")) {
                    long size = entry.getSize();
                    int toRead = size > 0
                            ? (int) Math.min(size, MAX_CHANGELOG_BYTES)
                            : MAX_CHANGELOG_BYTES;
                    byte[] bytes = tar.readNBytes(toRead);
                    return new String(bytes, StandardCharsets.UTF_8);
                }
            }
        }
        return null;
    }

    /** External links for the Details panel — npm's homepage, when present. */
    private List<Link> npmLinks(FluentComponent component) {
        String homepage = extractString(component, P_HOMEPAGE);
        if (homepage == null || homepage.isBlank()) {
            return List.of();
        }
        return List.of(new Link("Homepage", homepage));
    }

    /** npm/yarn/pnpm install snippets; pinned to the version when it isn't the latest. */
    private static List<InstallSnippet> npmSnippets(String fullName, String version,
                                                    boolean pinned) {
        String ref = pinned ? fullName + "@" + version : fullName;
        return List.of(
                new InstallSnippet("npm", "shell", "npm install " + ref),
                new InstallSnippet("yarn", "shell", "yarn add " + ref),
                new InstallSnippet("pnpm", "shell", "pnpm add " + ref));
    }

    /**
     * Reads the npm PACKAGE_ROOT metadata document (served at "/{package}") and
     * fills in the author and dependency map for the given version. Best-effort:
     * any failure leaves those fields unset.
     */
    private void enrichFromPackageRoot(ContentFacet content, String fullName,
                                       String version, PackageDetail.Builder builder) {
        try {
            Optional<FluentAsset> root = content.assets().path("/" + fullName).find();
            if (root.isEmpty()) {
                return;
            }
            JsonNode doc;
            try (InputStream in = root.get().download().openInputStream()) {
                doc = MAPPER.readTree(in);
            }
            applyPackageRootMetadata(doc, version, builder);
        } catch (Exception e) {
            log.debug("Could not read npm package-root metadata for '{}': {}",
                    fullName, e.getMessage());
        }
    }

    /**
     * Extracts author and dependencies for {@code version} from a parsed npm
     * package-root document. Package-visible for unit testing the parsing in
     * isolation from the Nexus content API.
     */
    static void applyPackageRootMetadata(JsonNode doc, String version,
                                         PackageDetail.Builder builder) {
        JsonNode versionDoc = doc.path("versions").path(version);
        if (versionDoc.isMissingNode()) {
            return;
        }
        builder.author(formatAuthor(versionDoc.get("author")));

        JsonNode deps = versionDoc.path("dependencies");
        if (deps.isObject() && !deps.isEmpty()) {
            Map<String, String> map = new LinkedHashMap<>();
            deps.fields().forEachRemaining(e -> map.put(e.getKey(), e.getValue().asText()));
            builder.dependencies(map);
        }

        // npm CLI records the README in the metadata document (version-level, or
        // top-level for the latest). Only override if actually present.
        String readme = firstNonBlank(
                versionDoc.path("readme").asText(null), doc.path("readme").asText(null));
        if (readme != null) {
            builder.readme(readme);
        }
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return (b != null && !b.isBlank()) ? b : null;
    }

    /** npm's author field may be a plain string or an object {name,email,url}. */
    private static String formatAuthor(JsonNode author) {
        if (author == null || author.isNull() || author.isMissingNode()) {
            return null;
        }
        if (author.isTextual()) {
            return author.asText();
        }
        String name = author.path("name").asText("");
        String email = author.path("email").asText("");
        if (name.isBlank()) {
            return email.isBlank() ? null : email;
        }
        return email.isBlank() ? name : name + " <" + email + ">";
    }

    /** The Nexus user who uploaded the package, taken from the tarball's blob. */
    private String publisherOf(FluentComponent component) {
        try {
            for (FluentAsset asset : component.assets()) {
                Optional<AssetBlob> blob = asset.blob();
                if (blob.isPresent() && blob.get().createdBy().isPresent()) {
                    return blob.get().createdBy().get();
                }
            }
        } catch (Exception e) {
            log.debug("Could not resolve publisher for '{}': {}",
                    component.name(), e.getMessage());
        }
        return null;
    }

    @Override
    public List<String> listVersions(String repository, String group, String name) {
        if (name == null || name.isBlank()) {
            return List.of();
        }
        List<String> versions = new ArrayList<>();
        for (Repository repo : npmRepositories(repository)) {
            contentFacet(repo).ifPresent(content -> {
                Collection<String> found = content.components().versions(
                        group == null ? "" : group, name);
                versions.addAll(found);
            });
        }
        return versions.stream()
                .distinct()
                .sorted(Comparator.reverseOrder())
                .collect(Collectors.toList());
    }

    // ── internals ──────────────────────────────────────────────────────────

    /**
     * Walks the npm components across the selected repositories and reduces them
     * to one summary per package name — the newest version seen for that name.
     */
    private List<PackageSummary> latestPerPackage(String repositoryFilter) {
        Map<String, PackageSummary> byName = new LinkedHashMap<>();

        for (Repository repo : npmRepositories(repositoryFilter)) {
            Optional<ContentFacet> facet = contentFacet(repo);
            if (facet.isEmpty()) {
                continue;
            }
            for (FluentComponent component : browseAll(facet.get(), repo.getName())) {
                PackageSummary summary = toSummary(component, repo.getName());
                byName.merge(summary.getName(), summary, NpmPackageProvider::newer);
            }
        }
        return new ArrayList<>(byName.values());
    }

    /**
     * Materializes up to {@link #MAX_SCAN} components from a repository, following
     * continuation tokens. Bounding the scan keeps a single request from stalling
     * on a very large repository; the cap is logged when hit.
     */
    private List<FluentComponent> browseAll(ContentFacet content, String repositoryName) {
        List<FluentComponent> all = new ArrayList<>();
        String token = null;
        do {
            Continuation<FluentComponent> page =
                    content.components().browseWithAssets(BROWSE_PAGE, token);
            if (page.isEmpty()) {
                break;
            }
            for (FluentComponent component : page) {
                all.add(component);
                if (all.size() >= MAX_SCAN) {
                    log.warn("npm scan cap ({}) reached in repository '{}'; "
                            + "results may be incomplete", MAX_SCAN, repositoryName);
                    return all;
                }
            }
            token = page.nextContinuationToken();
        } while (token != null);
        return all;
    }

    private static PackageSummary newer(PackageSummary a, PackageSummary b) {
        return b.getLastModified() >= a.getLastModified() ? b : a;
    }

    private static boolean isNewer(FluentComponent candidate, FluentComponent current) {
        return epochMillis(candidate) >= epochMillis(current);
    }

    private static long epochMillis(FluentComponent component) {
        OffsetDateTime updated = component.lastUpdated();
        return updated == null ? 0L : updated.toInstant().toEpochMilli();
    }

    /** npm scopes are stored without the leading '@'; restore it for display/install. */
    private static String displayName(FluentComponent component) {
        String namespace = component.namespace();
        return (namespace == null || namespace.isBlank())
                ? component.name()
                : "@" + namespace + "/" + component.name();
    }

    /**
     * Reads an npm package.json field from format attributes under the "npm" child.
     * Depending on the publish path the fields may land on the component or on the
     * tarball asset, so both are checked.
     */
    private String extractString(FluentComponent component, String key) {
        try {
            String fromComponent = component.attributes(NPM_ATTR).get(key, String.class);
            if (fromComponent != null && !fromComponent.isBlank()) {
                return fromComponent;
            }
        } catch (Exception e) {
            // fall through to assets
        }
        try {
            for (FluentAsset asset : component.assets()) {
                String fromAsset = asset.attributes(NPM_ATTR).get(key, String.class);
                if (fromAsset != null && !fromAsset.isBlank()) {
                    return fromAsset;
                }
            }
        } catch (Exception e) {
            // value stays null
        }
        return null;
    }

    private PackageSummary toSummary(FluentComponent component, String repositoryName) {
        String namespace = component.namespace();
        String fullName = displayName(component);

        return PackageSummary.builder()
                .id(repositoryName + ":" + fullName)
                .name(fullName)
                .format(FORMAT)
                .repository(repositoryName)
                .group(namespace == null || namespace.isBlank() ? null : namespace)
                .latestVersion(component.version())
                .description(extractString(component, P_DESCRIPTION))
                .lastModified(epochMillis(component))
                .build();
    }

    private List<Repository> npmRepositories(String repositoryFilter) {
        Optional<RepositoryManager> rm = repositoryManager();
        if (rm.isEmpty()) {
            return List.of();
        }
        List<Repository> matching = StreamSupport.stream(rm.get().browse().spliterator(), false)
                .filter(r -> FORMAT.equalsIgnoreCase(r.getFormat().getValue()))
                .filter(r -> repositoryFilter == null || repositoryFilter.isBlank()
                        || repositoryFilter.equals(r.getName()))
                .collect(Collectors.toList());
        // Only expose repositories the current subject is permitted to read.
        return accessFilter.readable(matching);
    }

    private Optional<ContentFacet> contentFacet(Repository repository) {
        try {
            return repository.optionalFacet(ContentFacet.class);
        } catch (Exception e) {
            log.debug("No ContentFacet on repository '{}': {}",
                    repository.getName(), e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<RepositoryManager> repositoryManager() {
        return beanLocator.lookup(RepositoryManager.class);
    }
}

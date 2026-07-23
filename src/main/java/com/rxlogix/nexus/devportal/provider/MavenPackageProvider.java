package com.rxlogix.nexus.devportal.provider;

import com.rxlogix.nexus.devportal.internal.NexusBeanLocator;
import com.rxlogix.nexus.devportal.internal.RepositoryAccessFilter;
import com.rxlogix.nexus.devportal.model.InstallSnippet;
import com.rxlogix.nexus.devportal.model.Link;
import com.rxlogix.nexus.devportal.model.PackageDetail;
import com.rxlogix.nexus.devportal.model.PackageSummary;
import com.rxlogix.nexus.devportal.model.PagedResult;
import com.rxlogix.nexus.devportal.model.SearchRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.nexus.common.entity.Continuation;
import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.content.AssetBlob;
import org.sonatype.nexus.repository.content.facet.ContentFacet;
import org.sonatype.nexus.repository.content.fluent.FluentAsset;
import org.sonatype.nexus.repository.content.fluent.FluentComponent;
import org.sonatype.nexus.repository.manager.RepositoryManager;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
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
 * Maven (maven2) implementation of {@link PackageProvider}, backed by Nexus's
 * datastore content API.
 *
 * A Maven package is identified by groupId:artifactId; each published version is
 * a separate component sharing that coordinate, so search and recent dedupe by
 * coordinate, keeping the newest version.
 *
 * Coordinates and the POM name/description are stored in component format
 * attributes (child "maven2"). Dependencies are NOT stored there — they are read
 * by parsing the artifact's .pom file on demand.
 */
public class MavenPackageProvider implements PackageProvider {

    private static final Logger log = LoggerFactory.getLogger(MavenPackageProvider.class);

    private static final String FORMAT = "maven2";
    private static final String MAVEN_ATTR = "maven2";
    private static final String P_POM_DESCRIPTION = "pom_description";
    private static final String P_POM_NAME = "pom_name";

    private static final int BROWSE_PAGE = 100;
    private static final int MAX_SCAN = 5000;

    private final NexusBeanLocator beanLocator;
    private final RepositoryAccessFilter accessFilter;

    public MavenPackageProvider(NexusBeanLocator beanLocator, RepositoryAccessFilter accessFilter) {
        this.beanLocator = beanLocator;
        this.accessFilter = accessFilter;
    }

    @Override
    public String getFormat() {
        return FORMAT;
    }

    @Override
    public String getDisplayName() {
        return "Maven";
    }

    @Override
    public boolean isAvailable() {
        return repositoryManager().isPresent();
    }

    @Override
    public PagedResult<PackageSummary> search(SearchRequest request) {
        String query = request.getQuery() == null ? "" : request.getQuery().toLowerCase();

        List<PackageSummary> matches = latestPerCoordinate(request.getRepository()).stream()
                .filter(p -> query.isEmpty()
                        || p.getName().toLowerCase().contains(query)
                        || (p.getGroup() != null && p.getGroup().toLowerCase().contains(query))
                        || (p.getDescription() != null
                                && p.getDescription().toLowerCase().contains(query)))
                .sorted(Comparator.comparing(PackageSummary::getName))
                .collect(Collectors.toList());

        int from = request.getPage() * request.getPageSize();
        if (from >= matches.size()) {
            return PagedResult.of(List.of(), request.getPage(), request.getPageSize(),
                    matches.size());
        }
        int to = Math.min(from + request.getPageSize(), matches.size());
        return PagedResult.of(matches.subList(from, to), request.getPage(),
                request.getPageSize(), matches.size());
    }

    @Override
    public List<PackageSummary> recent(int limit) {
        return latestPerCoordinate(null).stream()
                .sorted(Comparator.comparingLong(PackageSummary::getLastModified).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    public List<PackageSummary> popular(int limit) {
        // Nexus CE has no download-count signal in the content store; fall back to recent.
        return recent(limit);
    }

    @Override
    public Optional<PackageSummary> findPackage(String repository, String group, String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return latestPerCoordinate(repository).stream()
                .filter(p -> name.equalsIgnoreCase(p.getName())
                        && (group == null || group.isBlank() || group.equalsIgnoreCase(p.getGroup())))
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
        for (Repository repo : mavenRepositories(repository)) {
            Optional<ContentFacet> facet = contentFacet(repo);
            if (facet.isEmpty()) {
                continue;
            }
            for (FluentComponent component : browseAll(facet.get(), repo.getName())) {
                if (!name.equalsIgnoreCase(component.name())) {
                    continue;
                }
                if (group != null && !group.isBlank()
                        && !group.equalsIgnoreCase(component.namespace())) {
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
        if (version != null && !version.isBlank() && selected == null) {
            return Optional.empty();
        }
        FluentComponent shown = selected != null ? selected : latest;

        String groupId = shown.namespace();
        String artifactId = shown.name();
        String shownVersion = shown.version();

        PomData pom = readPom(facetOf, groupId, artifactId, shownVersion);

        return Optional.of(PackageDetail.builder()
                .name(artifactId)
                .format(FORMAT)
                .repository(repoName)
                .group(groupId)
                .version(shownVersion)
                .latestVersion(latest.version())
                .description(extractString(shown, P_POM_DESCRIPTION, P_POM_NAME))
                .installSnippets(mavenSnippets(groupId, artifactId, shownVersion))
                .links(mavenLinks(pom))
                .publishedBy(publisherOf(shown))
                .lastModified(epochMillis(shown))
                .versions(listVersions(repoName, groupId, artifactId))
                .dependencies(pom.dependencies)
                .build());
    }

    @Override
    public List<String> listVersions(String repository, String group, String name) {
        if (name == null || name.isBlank()) {
            return List.of();
        }
        List<String> versions = new ArrayList<>();
        for (Repository repo : mavenRepositories(repository)) {
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

    /** Reduces components to one summary per groupId:artifactId, newest version. */
    private List<PackageSummary> latestPerCoordinate(String repositoryFilter) {
        Map<String, PackageSummary> byCoordinate = new LinkedHashMap<>();
        for (Repository repo : mavenRepositories(repositoryFilter)) {
            Optional<ContentFacet> facet = contentFacet(repo);
            if (facet.isEmpty()) {
                continue;
            }
            for (FluentComponent component : browseAll(facet.get(), repo.getName())) {
                PackageSummary summary = toSummary(component, repo.getName());
                String key = summary.getGroup() + ":" + summary.getName();
                byCoordinate.merge(key, summary, MavenPackageProvider::newer);
            }
        }
        return new ArrayList<>(byCoordinate.values());
    }

    private PackageSummary toSummary(FluentComponent component, String repositoryName) {
        String groupId = component.namespace();
        String artifactId = component.name();
        return PackageSummary.builder()
                .id(repositoryName + ":" + groupId + ":" + artifactId)
                .name(artifactId)
                .format(FORMAT)
                .repository(repositoryName)
                .group(groupId)
                .latestVersion(component.version())
                .description(extractString(component, P_POM_DESCRIPTION, P_POM_NAME))
                .lastModified(epochMillis(component))
                .build();
    }

    /**
     * External links for the Details panel. Maven changelogs live in source
     * control, so the project URL is surfaced as "Changelog / Source", plus the
     * issue tracker when the POM declares one.
     */
    private static List<Link> mavenLinks(PomData pom) {
        List<Link> links = new ArrayList<>();
        if (pom.projectUrl != null) {
            links.add(new Link("Changelog / Source", pom.projectUrl));
        }
        if (pom.issueUrl != null) {
            links.add(new Link("Issues", pom.issueUrl));
        }
        return links;
    }

    /** Maven XML, Gradle (Groovy), and Gradle (Kotlin) install snippets. */
    private static List<InstallSnippet> mavenSnippets(String groupId, String artifactId,
                                                      String version) {
        String mavenXml = "<dependency>\n"
                + "  <groupId>" + groupId + "</groupId>\n"
                + "  <artifactId>" + artifactId + "</artifactId>\n"
                + "  <version>" + version + "</version>\n"
                + "</dependency>";
        String coord = groupId + ":" + artifactId + ":" + version;
        return List.of(
                new InstallSnippet("Maven", "xml", mavenXml),
                new InstallSnippet("Gradle", "groovy", "implementation '" + coord + "'"),
                new InstallSnippet("Gradle (Kotlin)", "kotlin",
                        "implementation(\"" + coord + "\")"));
    }

    /** Parsed POM data the portal cares about: dependencies, project URL, issue tracker. */
    static final class PomData {
        final Map<String, String> dependencies;
        final String projectUrl;
        final String issueUrl;
        PomData(Map<String, String> dependencies, String projectUrl, String issueUrl) {
            this.dependencies = dependencies;
            this.projectUrl = projectUrl;
            this.issueUrl = issueUrl;
        }
        static PomData empty() {
            return new PomData(Map.of(), null, null);
        }
    }

    /**
     * Reads and parses the artifact's .pom. Returns {@link PomData#empty()} on any
     * failure (missing POM, parse error).
     */
    private PomData readPom(ContentFacet content, String groupId,
                            String artifactId, String version) {
        String pomPath = "/" + groupId.replace('.', '/') + "/" + artifactId + "/" + version
                + "/" + artifactId + "-" + version + ".pom";
        try {
            Optional<FluentAsset> pom = content.assets().path(pomPath).find();
            if (pom.isEmpty()) {
                return PomData.empty();
            }
            try (InputStream in = pom.get().download().openInputStream()) {
                return parsePom(in);
            }
        } catch (Exception e) {
            log.debug("Could not read POM for {}:{}:{}: {}",
                    groupId, artifactId, version, e.getMessage());
            return PomData.empty();
        }
    }

    /**
     * Parses a POM's direct dependencies (coordinate→version) and project URL.
     * Package-visible for unit testing. Resolves simple ${property} references
     * from the POM's own &lt;properties&gt; block. XXE-hardened.
     */
    static PomData parsePom(InputStream pomStream) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // Harden against XXE — this is untrusted content from a repository.
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setExpandEntityReferences(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(pomStream);
        doc.getDocumentElement().normalize();
        Element project = doc.getDocumentElement();

        Map<String, String> properties = readProperties(doc);

        Map<String, String> deps = new LinkedHashMap<>();
        NodeList dependenciesNodes = project.getElementsByTagName("dependencies");
        for (int i = 0; i < dependenciesNodes.getLength(); i++) {
            Node depsNode = dependenciesNodes.item(i);
            // Skip <dependencies> nested under <dependencyManagement> or a plugin.
            if (!"project".equals(nodeName(depsNode.getParentNode()))) {
                continue;
            }
            NodeList children = ((Element) depsNode).getElementsByTagName("dependency");
            for (int j = 0; j < children.getLength(); j++) {
                Element dep = (Element) children.item(j);
                String g = resolve(childText(dep, "groupId"), properties);
                String a = resolve(childText(dep, "artifactId"), properties);
                String v = resolve(childText(dep, "version"), properties);
                if (a == null || a.isBlank()) {
                    continue;
                }
                String coord = (g == null || g.isBlank()) ? a : g + ":" + a;
                deps.put(coord, v == null || v.isBlank() ? "(managed)" : v);
            }
        }

        // Project URL for the "source / changelog" link-out: prefer <url>, then <scm><url>.
        String url = childText(project, "url");
        if (url == null || url.isBlank()) {
            url = childUrlOf(project, "scm");
        }
        // Issue tracker URL from <issueManagement><url>.
        String issueUrl = childUrlOf(project, "issueManagement");

        return new PomData(deps,
                url == null || url.isBlank() ? null : url,
                issueUrl == null || issueUrl.isBlank() ? null : issueUrl);
    }

    /** Reads the &lt;url&gt; child of the first direct &lt;tag&gt; element under project. */
    private static String childUrlOf(Element project, String tag) {
        NodeList nodes = project.getElementsByTagName(tag);
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i).getParentNode() == project && nodes.item(i) instanceof Element) {
                return childText((Element) nodes.item(i), "url");
            }
        }
        return null;
    }

    /** Back-compat helper for tests: just the dependency map. */
    static Map<String, String> parsePomDependencies(InputStream pomStream) throws Exception {
        return parsePom(pomStream).dependencies;
    }

    private static Map<String, String> readProperties(Document doc) {
        Map<String, String> properties = new LinkedHashMap<>();
        NodeList propsNodes = doc.getDocumentElement().getElementsByTagName("properties");
        for (int i = 0; i < propsNodes.getLength(); i++) {
            Node propsNode = propsNodes.item(i);
            if (!"project".equals(nodeName(propsNode.getParentNode()))) {
                continue;
            }
            NodeList props = propsNode.getChildNodes();
            for (int j = 0; j < props.getLength(); j++) {
                Node prop = props.item(j);
                if (prop.getNodeType() == Node.ELEMENT_NODE) {
                    properties.put(prop.getNodeName(), prop.getTextContent().trim());
                }
            }
        }
        return properties;
    }

    /** Resolves a single ${property} reference from the given map; leaves others as-is. */
    private static String resolve(String value, Map<String, String> properties) {
        if (value == null || !value.startsWith("${") || !value.endsWith("}")) {
            return value;
        }
        String key = value.substring(2, value.length() - 1);
        return properties.getOrDefault(key, value);
    }

    private static String childText(Element parent, String tag) {
        NodeList list = parent.getElementsByTagName(tag);
        for (int i = 0; i < list.getLength(); i++) {
            if (list.item(i).getParentNode() == parent) {
                return list.item(i).getTextContent().trim();
            }
        }
        return null;
    }

    private static String nodeName(Node node) {
        return node == null ? null : node.getNodeName();
    }

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
                    log.warn("maven scan cap ({}) reached in repository '{}'; "
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

    /** Reads the first non-blank of the given attribute keys from the "maven2" child. */
    private String extractString(FluentComponent component, String... keys) {
        try {
            for (String key : keys) {
                String value = component.attributes(MAVEN_ATTR).get(key, String.class);
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
        } catch (Exception e) {
            // attribute absent — return null
        }
        return null;
    }

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

    private List<Repository> mavenRepositories(String repositoryFilter) {
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

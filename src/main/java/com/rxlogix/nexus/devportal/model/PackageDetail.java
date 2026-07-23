package com.rxlogix.nexus.devportal.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Full detail for a single package: everything the package page shows —
 * metadata, the ordered version list, an install command, and an optional README.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class PackageDetail {

    private final String name;
    private final String format;
    private final String repository;
    private final String group;
    private final String version;
    private final String latestVersion;
    private final String description;
    private final List<InstallSnippet> installSnippets;
    private final String readme;
    private final String changelog;
    private final List<Link> links;
    private final String author;
    private final String publishedBy;
    private final long lastModified;
    private final List<String> versions;
    private final Map<String, String> dependencies;

    @JsonCreator
    public PackageDetail(
            @JsonProperty("name") String name,
            @JsonProperty("format") String format,
            @JsonProperty("repository") String repository,
            @JsonProperty("group") String group,
            @JsonProperty("version") String version,
            @JsonProperty("latestVersion") String latestVersion,
            @JsonProperty("description") String description,
            @JsonProperty("installSnippets") List<InstallSnippet> installSnippets,
            @JsonProperty("readme") String readme,
            @JsonProperty("changelog") String changelog,
            @JsonProperty("links") List<Link> links,
            @JsonProperty("author") String author,
            @JsonProperty("publishedBy") String publishedBy,
            @JsonProperty("lastModified") long lastModified,
            @JsonProperty("versions") List<String> versions,
            @JsonProperty("dependencies") Map<String, String> dependencies) {
        this.name = name;
        this.format = format;
        this.repository = repository;
        this.group = group;
        this.version = version;
        this.latestVersion = latestVersion;
        this.description = description;
        this.installSnippets = installSnippets == null ? Collections.emptyList()
                : Collections.unmodifiableList(installSnippets);
        this.readme = readme;
        this.changelog = changelog;
        this.links = links == null ? Collections.emptyList()
                : Collections.unmodifiableList(links);
        this.author = author;
        this.publishedBy = publishedBy;
        this.lastModified = lastModified;
        this.versions = versions == null ? Collections.emptyList()
                : Collections.unmodifiableList(versions);
        this.dependencies = dependencies == null ? Collections.emptyMap()
                : Collections.unmodifiableMap(dependencies);
    }

    public String getName() { return name; }
    public String getFormat() { return format; }
    public String getRepository() { return repository; }
    public String getGroup() { return group; }
    public String getVersion() { return version; }
    public String getLatestVersion() { return latestVersion; }
    public String getDescription() { return description; }
    public List<InstallSnippet> getInstallSnippets() { return installSnippets; }
    public String getReadme() { return readme; }
    public String getChangelog() { return changelog; }
    public List<Link> getLinks() { return links; }
    public String getAuthor() { return author; }
    public String getPublishedBy() { return publishedBy; }
    public long getLastModified() { return lastModified; }
    public List<String> getVersions() { return versions; }
    public Map<String, String> getDependencies() { return dependencies; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String name;
        private String format;
        private String repository;
        private String group;
        private String version;
        private String latestVersion;
        private String description;
        private List<InstallSnippet> installSnippets;
        private String readme;
        private String changelog;
        private List<Link> links;
        private String author;
        private String publishedBy;
        private long lastModified;
        private List<String> versions;
        private Map<String, String> dependencies;

        private Builder() {}

        public Builder name(String name) { this.name = name; return this; }
        public Builder format(String format) { this.format = format; return this; }
        public Builder repository(String repository) { this.repository = repository; return this; }
        public Builder group(String group) { this.group = group; return this; }
        public Builder version(String version) { this.version = version; return this; }
        public Builder latestVersion(String latestVersion) { this.latestVersion = latestVersion; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder installSnippets(List<InstallSnippet> installSnippets) { this.installSnippets = installSnippets; return this; }
        public Builder readme(String readme) { this.readme = readme; return this; }
        public Builder changelog(String changelog) { this.changelog = changelog; return this; }
        public Builder links(List<Link> links) { this.links = links; return this; }
        public Builder author(String author) { this.author = author; return this; }
        public Builder publishedBy(String publishedBy) { this.publishedBy = publishedBy; return this; }
        public Builder lastModified(long lastModified) { this.lastModified = lastModified; return this; }
        public Builder versions(List<String> versions) { this.versions = versions; return this; }
        public Builder dependencies(Map<String, String> dependencies) { this.dependencies = dependencies; return this; }

        public PackageDetail build() {
            return new PackageDetail(name, format, repository, group, version, latestVersion,
                    description, installSnippets, readme, changelog, links, author, publishedBy,
                    lastModified, versions, dependencies);
        }
    }
}

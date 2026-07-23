package com.rxlogix.nexus.devportal.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class PackageSummary {

    private final String id;
    private final String name;
    private final String format;
    private final String repository;
    private final String latestVersion;
    private final String description;
    private final String group;
    private final long lastModified;

    @JsonCreator
    public PackageSummary(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("format") String format,
            @JsonProperty("repository") String repository,
            @JsonProperty("latestVersion") String latestVersion,
            @JsonProperty("description") String description,
            @JsonProperty("group") String group,
            @JsonProperty("lastModified") long lastModified) {
        this.id = id;
        this.name = name;
        this.format = format;
        this.repository = repository;
        this.latestVersion = latestVersion;
        this.description = description;
        this.group = group;
        this.lastModified = lastModified;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getFormat() { return format; }
    public String getRepository() { return repository; }
    public String getLatestVersion() { return latestVersion; }
    public String getDescription() { return description; }
    public String getGroup() { return group; }
    public long getLastModified() { return lastModified; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id;
        private String name;
        private String format;
        private String repository;
        private String latestVersion;
        private String description;
        private String group;
        private long lastModified;

        private Builder() {}

        public Builder id(String id) { this.id = id; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder format(String format) { this.format = format; return this; }
        public Builder repository(String repository) { this.repository = repository; return this; }
        public Builder latestVersion(String latestVersion) { this.latestVersion = latestVersion; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder group(String group) { this.group = group; return this; }
        public Builder lastModified(long lastModified) { this.lastModified = lastModified; return this; }

        public PackageSummary build() {
            return new PackageSummary(id, name, format, repository, latestVersion, description, group, lastModified);
        }
    }
}

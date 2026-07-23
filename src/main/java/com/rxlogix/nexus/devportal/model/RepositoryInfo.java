package com.rxlogix.nexus.devportal.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class RepositoryInfo {

    private final String name;
    private final String format;
    private final String type;
    private final boolean online;
    private final String url;
    private final long componentCount;

    @JsonCreator
    public RepositoryInfo(
            @JsonProperty("name") String name,
            @JsonProperty("format") String format,
            @JsonProperty("type") String type,
            @JsonProperty("online") boolean online,
            @JsonProperty("url") String url,
            @JsonProperty("componentCount") long componentCount) {
        this.name = name;
        this.format = format;
        this.type = type;
        this.online = online;
        this.url = url;
        this.componentCount = componentCount;
    }

    public String getName() { return name; }
    public String getFormat() { return format; }
    public String getType() { return type; }
    public boolean isOnline() { return online; }
    public String getUrl() { return url; }
    public long getComponentCount() { return componentCount; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String name;
        private String format;
        private String type;
        private boolean online = true;
        private String url;
        private long componentCount;

        private Builder() {}

        public Builder name(String name) { this.name = name; return this; }
        public Builder format(String format) { this.format = format; return this; }
        public Builder type(String type) { this.type = type; return this; }
        public Builder online(boolean online) { this.online = online; return this; }
        public Builder url(String url) { this.url = url; return this; }
        public Builder componentCount(long componentCount) { this.componentCount = componentCount; return this; }

        public RepositoryInfo build() {
            return new RepositoryInfo(name, format, type, online, url, componentCount);
        }
    }
}

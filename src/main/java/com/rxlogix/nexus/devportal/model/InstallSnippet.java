package com.rxlogix.nexus.devportal.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One copy-pasteable install snippet for a package, labelled by the tool it
 * targets (e.g. "npm", "yarn", "Maven", "Gradle"). {@code language} hints the
 * UI how to present it (e.g. "shell", "xml", "groovy").
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class InstallSnippet {

    private final String label;
    private final String language;
    private final String code;

    @JsonCreator
    public InstallSnippet(
            @JsonProperty("label") String label,
            @JsonProperty("language") String language,
            @JsonProperty("code") String code) {
        this.label = label;
        this.language = language;
        this.code = code;
    }

    public String getLabel() { return label; }
    public String getLanguage() { return language; }
    public String getCode() { return code; }
}

package com.rxlogix.nexus.devportal.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A labelled external link shown in the package's Details panel, e.g.
 * "Homepage", "Changelog / Source", "Issues". Lets each format supply its own
 * meaningful labels without the UI having to branch on format.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class Link {

    private final String label;
    private final String url;

    @JsonCreator
    public Link(@JsonProperty("label") String label, @JsonProperty("url") String url) {
        this.label = label;
        this.url = url;
    }

    public String getLabel() { return label; }
    public String getUrl() { return url; }
}

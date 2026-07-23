package com.rxlogix.nexus.devportal.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;

public final class PagedResult<T> {

    private final List<T> items;
    private final int page;
    private final int pageSize;
    private final long totalCount;
    private final boolean hasMore;

    @JsonCreator
    public PagedResult(
            @JsonProperty("items") List<T> items,
            @JsonProperty("page") int page,
            @JsonProperty("pageSize") int pageSize,
            @JsonProperty("totalCount") long totalCount) {
        this.items = items == null ? Collections.emptyList() : Collections.unmodifiableList(items);
        this.page = page;
        this.pageSize = pageSize;
        this.totalCount = totalCount;
        this.hasMore = (long) page * pageSize < totalCount;
    }

    public List<T> getItems() { return items; }
    public int getPage() { return page; }
    public int getPageSize() { return pageSize; }
    public long getTotalCount() { return totalCount; }
    public boolean isHasMore() { return hasMore; }

    public static <T> PagedResult<T> of(List<T> items, int page, int pageSize, long totalCount) {
        return new PagedResult<>(items, page, pageSize, totalCount);
    }

    public static <T> PagedResult<T> empty() {
        return new PagedResult<>(Collections.emptyList(), 0, 20, 0);
    }
}

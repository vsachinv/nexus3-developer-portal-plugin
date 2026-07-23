package com.rxlogix.nexus.devportal.model;

public final class SearchRequest {

    private final String query;
    private final String format;
    private final String repository;
    private final int page;
    private final int pageSize;

    private SearchRequest(Builder builder) {
        this.query = builder.query;
        this.format = builder.format;
        this.repository = builder.repository;
        this.page = Math.max(0, builder.page);
        this.pageSize = builder.pageSize < 1 ? 20 : Math.min(builder.pageSize, 100);
    }

    public String getQuery() { return query; }
    public String getFormat() { return format; }
    public String getRepository() { return repository; }
    public int getPage() { return page; }
    public int getPageSize() { return pageSize; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String query = "";
        private String format;
        private String repository;
        private int page = 0;
        private int pageSize = 20;

        private Builder() {}

        public Builder query(String query) { this.query = query == null ? "" : query.trim(); return this; }
        public Builder format(String format) { this.format = format; return this; }
        public Builder repository(String repository) { this.repository = repository; return this; }
        public Builder page(int page) { this.page = page; return this; }
        public Builder pageSize(int pageSize) { this.pageSize = pageSize; return this; }

        public SearchRequest build() { return new SearchRequest(this); }
    }
}

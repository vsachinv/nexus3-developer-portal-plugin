package com.rxlogix.nexus.devportal.rest;

import com.rxlogix.nexus.devportal.model.PagedResult;
import com.rxlogix.nexus.devportal.model.PackageSummary;
import com.rxlogix.nexus.devportal.model.SearchRequest;
import com.rxlogix.nexus.devportal.service.SearchService;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.nexus.rest.Resource;

/**
 * GET /service/rest/devportal/api/search?q=lodash&format=npm&page=0&pageSize=20
 */
@Path("/devportal/api/search")
@Produces(MediaType.APPLICATION_JSON)
public class SearchApiResource implements Resource {

    private static final Logger log = LoggerFactory.getLogger(SearchApiResource.class);

    private final SearchService searchService;

    public SearchApiResource(SearchService searchService) {
        this.searchService = searchService;
    }

    @GET
    public Response search(
            @QueryParam("q")          @DefaultValue("")   String query,
            @QueryParam("format")                         String format,
            @QueryParam("repository")                     String repository,
            @QueryParam("page")       @DefaultValue("0")  int    page,
            @QueryParam("pageSize")   @DefaultValue("20") int    pageSize) {

        log.debug("Search: q={} format={} repository={} page={} pageSize={}", query, format, repository, page, pageSize);

        SearchRequest request = SearchRequest.builder()
                .query(query)
                .format(format)
                .repository(repository)
                .page(page)
                .pageSize(pageSize)
                .build();

        PagedResult<PackageSummary> result = searchService.search(request);
        return Response.ok(result).build();
    }
}

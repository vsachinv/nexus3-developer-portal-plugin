package com.rxlogix.nexus.devportal.rest;

import com.rxlogix.nexus.devportal.model.PackageSummary;
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

import java.util.List;

/**
 * GET /service/rest/devportal/api/recent?limit=10
 * GET /service/rest/devportal/api/popular?limit=10
 */
@Path("/devportal/api")
@Produces(MediaType.APPLICATION_JSON)
public class RecentApiResource implements Resource {

    private static final Logger log = LoggerFactory.getLogger(RecentApiResource.class);

    private final SearchService searchService;

    public RecentApiResource(SearchService searchService) {
        this.searchService = searchService;
    }

    @GET
    @Path("/recent")
    public Response recent(@QueryParam("limit") @DefaultValue("10") int limit) {
        log.debug("Fetching {} recent packages", limit);
        List<PackageSummary> packages = searchService.recent(limit);
        return Response.ok(packages).build();
    }

    @GET
    @Path("/popular")
    public Response popular(@QueryParam("limit") @DefaultValue("10") int limit) {
        log.debug("Fetching {} popular packages", limit);
        List<PackageSummary> packages = searchService.popular(limit);
        return Response.ok(packages).build();
    }
}

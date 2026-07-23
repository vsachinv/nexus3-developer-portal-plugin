package com.rxlogix.nexus.devportal.rest;

import com.rxlogix.nexus.devportal.model.PackageDetail;
import com.rxlogix.nexus.devportal.service.SearchService;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.nexus.rest.Resource;

import java.util.Optional;

/**
 * GET /service/rest/devportal/api/package?format=npm&name=@scope/pkg&group=&repository=
 *
 * Query parameters are used (rather than path segments) because scoped npm names
 * contain '/' and '@', which are awkward and ambiguous inside a path.
 */
@Path("/devportal/api/package")
@Produces(MediaType.APPLICATION_JSON)
public class PackageApiResource implements Resource {

    private static final Logger log = LoggerFactory.getLogger(PackageApiResource.class);

    private final SearchService searchService;

    public PackageApiResource(SearchService searchService) {
        this.searchService = searchService;
    }

    @GET
    public Response detail(
            @QueryParam("format")     String format,
            @QueryParam("name")       String name,
            @QueryParam("group")      String group,
            @QueryParam("repository") String repository,
            @QueryParam("version")    String version) {

        log.debug("Package detail: format={} name={} group={} repository={} version={}",
                format, name, group, repository, version);

        if (format == null || format.isBlank() || name == null || name.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"message\":\"'format' and 'name' query parameters are required\"}")
                    .build();
        }

        Optional<PackageDetail> detail =
                searchService.packageDetail(format, repository, group, name, version);
        return detail
                .map(d -> Response.ok(d).build())
                .orElseGet(() -> Response.status(Response.Status.NOT_FOUND)
                        .entity("{\"message\":\"Package not found\"}")
                        .build());
    }
}

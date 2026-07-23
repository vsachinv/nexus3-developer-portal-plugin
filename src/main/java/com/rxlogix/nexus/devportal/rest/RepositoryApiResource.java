package com.rxlogix.nexus.devportal.rest;

import com.rxlogix.nexus.devportal.model.RepositoryInfo;
import com.rxlogix.nexus.devportal.service.RepositoryService;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.nexus.rest.Resource;

import java.util.List;
import java.util.Optional;

/**
 * GET /service/rest/devportal/api/repositories
 * GET /service/rest/devportal/api/repositories?format=npm
 * GET /service/rest/devportal/api/repositories/{name}
 */
@Path("/devportal/api/repositories")
@Produces(MediaType.APPLICATION_JSON)
public class RepositoryApiResource implements Resource {

    private static final Logger log = LoggerFactory.getLogger(RepositoryApiResource.class);

    private final RepositoryService repositoryService;

    public RepositoryApiResource(RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    @GET
    public Response list(@QueryParam("format") String format) {
        log.debug("Listing repositories, format filter: {}", format);
        List<RepositoryInfo> repos = (format == null || format.isBlank())
                ? repositoryService.listAll()
                : repositoryService.listByFormat(format);
        return Response.ok(repos).build();
    }

    @GET
    @Path("{name}")
    public Response getByName(@PathParam("name") String name) {
        log.debug("Getting repository: {}", name);
        Optional<RepositoryInfo> repo = repositoryService.findByName(name);
        return repo.map(r -> Response.ok(r).build())
                   .orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build());
    }
}

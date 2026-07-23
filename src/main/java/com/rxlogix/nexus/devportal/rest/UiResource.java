package com.rxlogix.nexus.devportal.rest;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.nexus.rest.Resource;

import java.io.InputStream;
import java.net.URI;

/**
 * Serves the developer portal SPA.
 *
 * Entry:  GET /service/rest/devportal/ui
 * Assets: GET /service/rest/devportal/ui/{path}
 *
 * Unknown sub-paths fall back to index.html so the hash-router handles them.
 */
@Path("/devportal/ui")
public class UiResource implements Resource {

    private static final Logger log = LoggerFactory.getLogger(UiResource.class);
    private static final String STATIC_ROOT = "/static/devportal/";

    // Revalidate on every load so a plugin upgrade never leaves a stale SPA cached.
    private static final String NO_CACHE = "no-cache, must-revalidate";

    @GET
    public Response index(@Context UriInfo uriInfo) {
        // Relative asset URLs (css/…, js/…) only resolve under the trailing-slash
        // form of this path; redirect /devportal/ui → /devportal/ui/
        URI requestUri = uriInfo.getRequestUri();
        if (!requestUri.getPath().endsWith("/")) {
            return Response.seeOther(URI.create(requestUri + "/")).build();
        }
        return serveClasspathResource("index.html", "text/html;charset=UTF-8");
    }

    @GET
    @Path("{path:.*}")
    public Response asset(@PathParam("path") String path) {
        if (path == null || path.isBlank() || path.contains("..")) {
            return serveClasspathResource("index.html", "text/html;charset=UTF-8");
        }

        InputStream resource = getClass().getResourceAsStream(STATIC_ROOT + path);
        if (resource == null) {
            return serveClasspathResource("index.html", "text/html;charset=UTF-8");
        }

        return Response.ok(resource).type(contentTypeFor(path))
                .header("Cache-Control", NO_CACHE).build();
    }

    private Response serveClasspathResource(String resourceName, String contentType) {
        InputStream stream = getClass().getResourceAsStream(STATIC_ROOT + resourceName);
        if (stream == null) {
            log.error("Static resource not found on classpath: {}{}", STATIC_ROOT, resourceName);
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(stream).type(contentType)
                .header("Cache-Control", NO_CACHE).build();
    }

    private static String contentTypeFor(String path) {
        if (path.endsWith(".html"))  return "text/html;charset=UTF-8";
        if (path.endsWith(".css"))   return "text/css;charset=UTF-8";
        if (path.endsWith(".js"))    return "application/javascript;charset=UTF-8";
        if (path.endsWith(".svg"))   return "image/svg+xml";
        if (path.endsWith(".png"))   return "image/png";
        if (path.endsWith(".ico"))   return "image/x-icon";
        if (path.endsWith(".woff2")) return "font/woff2";
        if (path.endsWith(".woff"))  return "font/woff";
        return "application/octet-stream";
    }
}

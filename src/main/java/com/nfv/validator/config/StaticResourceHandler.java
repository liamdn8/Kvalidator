package com.nfv.validator.config;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Static resource handler to serve React app and handle client-side routing
 */
@Path("/kvalidator/web")
public class StaticResourceHandler {

    /**
     * Serve index.html for all React routes (client-side routing)
     */
    @GET
    @Path("/")
    public Response getIndex() {
        return serveIndexHtml();
    }

    /**
     * Handle client-side routes - return index.html for non-asset paths
     */
    @GET
    @Path("/{path:.*}")
    public Response getResource(@PathParam("path") String path) {
        // If it's an asset file (js, css, images), let Quarkus handle it normally
        if (path.startsWith("assets/") || path.endsWith(".js") || 
            path.endsWith(".css") || path.endsWith(".ico") || 
            path.endsWith(".png") || path.endsWith(".svg")) {
            // Return 404 to let Quarkus static handler take over
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        
        // For all other paths (React routes), serve index.html
        return serveIndexHtml();
    }

    private Response serveIndexHtml() {
        try {
            InputStream indexStream = getClass().getResourceAsStream("/META-INF/resources/kvalidator/web/index.html");
            if (indexStream != null) {
                String content = new String(indexStream.readAllBytes(), StandardCharsets.UTF_8);
                return Response.ok(content)
                    .header("Content-Type", "text/html; charset=utf-8")
                    .header("Cache-Control", "no-cache")
                    .build();
            }
        } catch (Exception e) {
            // Log error but don't expose details
            System.err.println("Error serving index.html: " + e.getMessage());
        }
        return Response.status(Response.Status.NOT_FOUND).build();
    }
}
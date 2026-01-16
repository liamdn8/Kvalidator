package com.nfv.validator.api;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import java.net.URI;

/**
 * Redirect root paths to web UI
 */
@Path("/")
public class RootRedirectResource {

    @GET
    public Response redirectRoot() {
        return Response.seeOther(URI.create("/kvalidator/web/")).build();
    }

    @GET
    @Path("/kvalidator")
    public Response redirectKvalidator() {
        return Response.seeOther(URI.create("/kvalidator/web/")).build();
    }
}

package com.johnsonfitness.qq;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.keycloak.models.KeycloakSession;

@Path("/qq/endpoint")
public class QQIdentityProviderEndpoint {

    private static final Logger logger = Logger.getLogger(QQIdentityProviderEndpoint.class);

    private final KeycloakSession session;
    private final QQIdentityProvider provider;

    public QQIdentityProviderEndpoint(KeycloakSession session) {
        this.session = session;
        QQIdentityProviderConfig config = new QQIdentityProviderConfig();
        this.provider = new QQIdentityProvider(session, config);
    }

    @GET
    @Path("/userinfo")
    @Produces(MediaType.APPLICATION_JSON)
    public Response userInfo(@QueryParam("access_token") String accessToken) {
        try {
            JsonNode profile = provider.fetchQQUserInfo(accessToken);
            return Response.ok(profile.toString()).build();
        } catch (Exception e) {
            logger.error("QQ userinfo failed", e);
            return Response.status(Response.Status.BAD_REQUEST)
                           .entity("{\"error\":\"invalid_token\"}")
                           .build();
        }
    }

    public static class TokenExchangeRequest {
        public String grant_type;
        public String subject_token;
        public String subject_issuer;
    }

    @POST
    @Path("/token-exchange")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response tokenExchange(TokenExchangeRequest req) {
        if (!"urn:ietf:params:oauth:grant-type:token-exchange".equals(req.grant_type)
                || !"qq".equals(req.subject_issuer)) {
            return Response.status(Response.Status.BAD_REQUEST)
                           .entity("{\"error\":\"unsupported_grant_type\"}")
                           .build();
        }

        try {
            JsonNode profile = provider.fetchQQUserInfo(req.subject_token);
            return Response.ok(profile.toString()).build();
        } catch (Exception e) {
            logger.error("QQ token exchange error", e);
            return Response.status(Response.Status.BAD_REQUEST)
                           .entity("{\"error\":\"invalid_token\"}")
                           .build();
        }
    }
}
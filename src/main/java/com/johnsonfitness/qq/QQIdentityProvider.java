package com.johnsonfitness.qq;

import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.json.JSONObject;
import org.keycloak.OAuth2Constants;
import org.keycloak.OAuthErrorException;
import org.keycloak.broker.oidc.OIDCIdentityProvider;
import org.keycloak.broker.oidc.OIDCIdentityProviderConfig;
import org.keycloak.broker.provider.BrokeredIdentityContext;
import org.keycloak.broker.provider.IdentityBrokerException;
import org.keycloak.broker.provider.util.SimpleHttp;
import org.keycloak.broker.social.SocialIdentityProvider;
import org.keycloak.events.Details;
import org.keycloak.events.Errors;
import org.keycloak.events.EventBuilder;
import org.keycloak.models.KeycloakContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.services.ErrorResponseException;
import org.keycloak.services.Urls;
import org.keycloak.sessions.AuthenticationSessionModel;
import org.keycloak.vault.VaultStringSecret;

import com.johnsonfitness.broker.ExternalTokenExchangeCapable;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class QQIdentityProvider extends OIDCIdentityProvider implements SocialIdentityProvider<OIDCIdentityProviderConfig>, ExternalTokenExchangeCapable {

    static final String QQ_AUTHZ_CODE = "qq-authz-code";
    static final String ACCESS_TOKEN = "access_token";

    public QQIdentityProvider(KeycloakSession session, QQIdentityProviderConfig config) {
        super(session, config);
    }

    @Override
    protected String getDefaultScopes() {
        return "openid get_user_info";
    }

    protected String getAuthorizationUrl() {
        return "https://graph.qq.com/oauth2.0/authorize";
    }

    protected String getTokenUrl() {
        return "https://graph.qq.com/oauth2.0/token";
    }

    @Override
    protected String getUserInfoUrl() {
        return "https://graph.qq.com/user/get_user_info";
    }

    @Override
    protected BrokeredIdentityContext exchangeExternalTokenV1Impl(EventBuilder event, MultivaluedMap<String, String> params) {
        TokenExchangeParams exchangeParams = new TokenExchangeParams(params);
        if (exchangeParams.getSubjectToken() == null) {
            event.detail(Details.REASON, OAuth2Constants.SUBJECT_TOKEN + " param unset");
            event.error(Errors.INVALID_TOKEN);
            throw new ErrorResponseException(OAuthErrorException.INVALID_TOKEN, "token not set", Response.Status.BAD_REQUEST);
        }

        if (QQ_AUTHZ_CODE.equals(exchangeParams.getSubjectTokenType())) {
            return exchangeAuthorizationCode(exchangeParams.getSubjectToken());
        } else {
            event.detail(Details.REASON, OAuth2Constants.SUBJECT_TOKEN_TYPE + " invalid");
            event.error(Errors.INVALID_TOKEN_TYPE);
            throw new ErrorResponseException(OAuthErrorException.INVALID_TOKEN, "invalid token type", Response.Status.BAD_REQUEST);
        }
    }
    
    private BrokeredIdentityContext exchangeAuthorizationCode(String authorizationCode) {
        String clientId = getConfig().getClientId();
        try {
            return sendTokenRequest(authorizationCode, clientId, null);
        } catch (IOException e) {
            logger.warn("Error exchanging QQ authorization_code. clientId=" + clientId, e);
            return null;
        }
    }

    public BrokeredIdentityContext sendTokenRequest(String authorizationCode, String clientId, AuthenticationSessionModel authSession) throws IOException {
        SimpleHttp.Response response = generateTokenRequest(authorizationCode, clientId).asResponse();

        if (response.getStatus() > 299) {
            logger.warn("Error response from QQ: status=" + response.getStatus() + ", body=" + response.asString());
            return null;
        }

        String accessToken = extractAccessToken(response.asString());
        BrokeredIdentityContext federatedIdentity = doGetFederatedIdentity(accessToken);
        federatedIdentity.setIdp(QQIdentityProvider.this);
        federatedIdentity.setAuthenticationSession(authSession);
        return federatedIdentity;
    }

    public SimpleHttp generateTokenRequest(String authorizationCode, String clientId) {
        KeycloakContext context = session.getContext();
        VaultStringSecret clientSecret = session.vault().getStringSecret(getConfig().getClientSecret());
        return SimpleHttp.doPost(getTokenUrl(), session)
                         .param(OAUTH2_PARAMETER_CODE, authorizationCode)
                         .param(OAUTH2_PARAMETER_REDIRECT_URI, Urls.identityProviderAuthnResponse(context.getUri().getBaseUri(), getConfig().getAlias(), context.getRealm().getName()).toString())
                         .param(OAUTH2_PARAMETER_GRANT_TYPE, OAUTH2_GRANT_TYPE_AUTHORIZATION_CODE)
                         .param(OAUTH2_PARAMETER_CLIENT_ID, clientId)
                         .param(OAUTH2_PARAMETER_CLIENT_SECRET, clientSecret.get().orElse(getConfig().getClientSecret()));
    }

    @Override
    protected BrokeredIdentityContext doGetFederatedIdentity(String accessToken) {
        try {
            SimpleHttp openidRequest = SimpleHttp.doGet("https://graph.qq.com/oauth2.0/me", session)
                    .param("access_token", accessToken);

            String openidResponse = openidRequest.asString();
            String openid = extractOpenId(openidResponse);

            SimpleHttp userInfoRequest = SimpleHttp.doGet(getUserInfoUrl(), session)
                    .param("access_token", accessToken)
                    .param("oauth_consumer_key", getConfig().getClientId())
                    .param("openid", openid);

            JSONObject userInfo = new JSONObject(userInfoRequest.asString());

            BrokeredIdentityContext context = new BrokeredIdentityContext(openid, getConfig());
            context.setUsername("QQ-" + openid);
            context.setEmail(openid + "@qq.jrny.cn");
            context.setIdp(this);

            context.setUserAttribute("nickname", userInfo.optString("nickname"));
            context.setUserAttribute("figureurl_qq_1", userInfo.optString("figureurl_qq_1"));
            context.setUserAttribute("gender", userInfo.optString("gender"));
            context.setUserAttribute("qq_openid", openid);

            return context;
        } catch (Exception e) {
            throw new IdentityBrokerException("Could not obtain user profile from QQ.", e);
        }
    }

    private String extractOpenId(String response) {
        int start = response.indexOf("{");
        int end = response.lastIndexOf("}");
        try {
            if (start >= 0 && end >= 0) {
                String json = response.substring(start, end + 1);
                JSONObject obj = new JSONObject(json);
                return obj.getString("openid");
            }
        } catch (Exception e) {
            logger.error("extractOpenId parse error:" + response, e);
        }
        throw new IdentityBrokerException("Cannot extract openid from QQ response: " + response);
    }

    private String extractAccessToken(String response) {
        Map<String, String> tokenMap = new HashMap<>();
        try {
            for (String pair: response.split("&")) {
                String[] parts = pair.split("=", 2);
                if (parts.length == 2) {
                    String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
                    String value = URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
                    tokenMap.put(key, value);
                }
            }
        } catch (Exception e) {
            logger.error("extractAccessToken parse error: " + response, e);
            throw new IdentityBrokerException("Cannot extract access token from QQ response: " + response);
        }

        return tokenMap.get(ACCESS_TOKEN);
    }

    @Override
    public QQIdentityProviderConfig getConfig() {
        return (QQIdentityProviderConfig) super.getConfig();
    }

    @Override
    public BrokeredIdentityContext exchangeExternalToken(KeycloakSession session, RealmModel realm, String externalToken)
        throws Exception {
      EventBuilder event = new EventBuilder(realm, session, session.getContext().getConnection());

      MultivaluedMap<String, String> params = new MultivaluedHashMap<>();
      params.add(OAuth2Constants.SUBJECT_TOKEN, externalToken);
      params.add(OAuth2Constants.GRANT_TYPE, OAuth2Constants.TOKEN_EXCHANGE_GRANT_TYPE);
      params.add(OAuth2Constants.SUBJECT_TOKEN_TYPE, QQ_AUTHZ_CODE);

      return exchangeExternalTokenV1Impl(event, params);
    }
}
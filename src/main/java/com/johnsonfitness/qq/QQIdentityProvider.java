package com.johnsonfitness.qq;

import com.johnsonfitness.keycloak.common.constants.CommonErrorCode;
import com.johnsonfitness.keycloak.common.exception.BaseKeycloakException;
import com.johnsonfitness.keycloak.common.exception.ValidationException;
import com.johnsonfitness.keycloak.common.interfaces.ExternalTokenExchangeCapable;
import com.johnsonfitness.keycloak.common.rest.ErrorResponseBuilder;
import com.johnsonfitness.keycloak.common.util.RandomUtils;
import com.johnsonfitness.qq.exception.QQException;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.json.JSONObject;
import org.keycloak.OAuth2Constants;
import org.keycloak.broker.oidc.OIDCIdentityProvider;
import org.keycloak.broker.oidc.OIDCIdentityProviderConfig;
import org.keycloak.broker.provider.BrokeredIdentityContext;
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
        try {
            TokenExchangeParams exchangeParams = new TokenExchangeParams(params);
            if (exchangeParams.getSubjectToken() == null) {
                event.detail(Details.REASON, OAuth2Constants.SUBJECT_TOKEN + " param unset");
                event.error(Errors.INVALID_TOKEN);
                throw new ValidationException(CommonErrorCode.VALIDATION_ERROR, "Subject token param unset");
            }

            if (QQ_AUTHZ_CODE.equals(exchangeParams.getSubjectTokenType())) {
                return exchangeAuthorizationCode(exchangeParams.getSubjectToken());
            } else {
                event.detail(Details.REASON, OAuth2Constants.SUBJECT_TOKEN_TYPE + " invalid");
                event.error(Errors.INVALID_TOKEN_TYPE);
                throw new ValidationException(CommonErrorCode.VALIDATION_ERROR, "Invalid token type");
            }

        } catch (BaseKeycloakException e) {
            throw new ErrorResponseException(
                ErrorResponseBuilder.buildErrorResponse(e)
            );
        } catch (Exception e) {
            throw new ErrorResponseException(
                ErrorResponseBuilder.buildErrorResponse(
                    new QQException(
                        CommonErrorCode.INTERNAL_SERVER_ERROR,
                        Response.Status.INTERNAL_SERVER_ERROR,
                        e
                    )
                )
            );
        }
    }

    private BrokeredIdentityContext exchangeAuthorizationCode(String authorizationCode) throws IOException {
        String clientId = getConfig().getClientId();
        return sendTokenRequest(authorizationCode, clientId, null);
    }

    public BrokeredIdentityContext sendTokenRequest(String authorizationCode, String clientId, AuthenticationSessionModel authSession) throws IOException {
        SimpleHttp.Response response = generateTokenRequest(authorizationCode, clientId).asResponse();

        checkQQResponseStatus(response);

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

    public String getOpenId(String accessToken) throws IOException {
        SimpleHttp openidRequest = SimpleHttp.doGet("https://graph.qq.com/oauth2.0/me", session)
            .param("access_token", accessToken);

        SimpleHttp.Response openidResponse = openidRequest.asResponse();
        checkQQResponseStatus(openidResponse);

        return extractOpenId(openidResponse.asString());
    }

    @Override
    protected BrokeredIdentityContext doGetFederatedIdentity(String accessToken) {
        try {
            String openid = getOpenId(accessToken);
            SimpleHttp userInfoRequest = SimpleHttp.doGet(getUserInfoUrl(), session)
                .param("access_token", accessToken)
                .param("oauth_consumer_key", getConfig().getClientId())
                .param("openid", openid);

            JSONObject userInfo = new JSONObject(userInfoRequest.asString());

            BrokeredIdentityContext context = new BrokeredIdentityContext(openid, getConfig());
            context.setUsername("QQ-" + openid);
            context.setEmail(openid + "@qq.jrny.cn");
            context.setIdp(this);

            context.setUserAttribute("nickname", userInfo.optString("nickname", RandomUtils.getRandomNickname()));
            context.setUserAttribute("figureurl_qq_1", userInfo.optString("figureurl_qq_1"));
            context.setUserAttribute("gender", userInfo.optString("gender"));
            context.setUserAttribute("qq_openid", openid);

            return context;
        } catch (Exception e) {
            throw new QQException(
                CommonErrorCode.INTERNAL_SERVER_ERROR,
                Response.Status.INTERNAL_SERVER_ERROR,
                e
            );
        }
    }

    private String extractOpenId(String response) {
        int start = response.indexOf("{");
        int end = response.lastIndexOf("}");
        if (start >= 0 && end >= 0) {
            String json = response.substring(start, end + 1);
            JSONObject obj = new JSONObject(json);
            return obj.getString("openid");
        } else {
            throw new QQException(
                CommonErrorCode.INTERNAL_SERVER_ERROR,
                Response.Status.INTERNAL_SERVER_ERROR,
                "Invalid openId response format"
            );
        }
    }

    private String extractAccessToken(String response) {
        Map<String, String> tokenMap = new HashMap<>();
        for (String pair : response.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2) {
                String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
                String value = URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
                tokenMap.put(key, value);
            }
        }

        String accessToken = tokenMap.get(ACCESS_TOKEN);
        if (accessToken == null) {
            throw new QQException(
                CommonErrorCode.INTERNAL_SERVER_ERROR,
                Response.Status.INTERNAL_SERVER_ERROR,
                "QQ access token is null"
            );
        }

        return accessToken;
    }

    private void checkQQResponseStatus(SimpleHttp.Response response) throws IOException {
        if (response.getStatus() > 299) {
            String details = "Unexpected response status from QQ, status= " + response.getStatus() + ", body= " + response.asString();
            throw new QQException(
                CommonErrorCode.INTERNAL_SERVER_ERROR,
                Response.Status.INTERNAL_SERVER_ERROR,
                details
            );
        }
    }

    @Override
    public QQIdentityProviderConfig getConfig() {
        return (QQIdentityProviderConfig) super.getConfig();
    }

    @Override
    public BrokeredIdentityContext exchangeExternalToken(KeycloakSession session, RealmModel realm, String externalToken) {
        EventBuilder event = new EventBuilder(realm, session, session.getContext().getConnection());

        MultivaluedMap<String, String> params = new MultivaluedHashMap<>();
        params.add(OAuth2Constants.SUBJECT_TOKEN, externalToken);
        params.add(OAuth2Constants.GRANT_TYPE, OAuth2Constants.TOKEN_EXCHANGE_GRANT_TYPE);
        params.add(OAuth2Constants.SUBJECT_TOKEN_TYPE, QQ_AUTHZ_CODE);

        return exchangeExternalTokenV1Impl(event, params);
    }
}
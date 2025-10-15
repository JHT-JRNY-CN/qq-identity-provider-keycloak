package com.johnsonfitness.qq;

import com.johnsonfitness.keycloak.common.constants.CommonErrorCode;
import com.johnsonfitness.keycloak.common.exception.BaseKeycloakException;
import com.johnsonfitness.keycloak.common.exception.ValidationException;
import com.johnsonfitness.keycloak.common.interfaces.ExternalTokenExchangeCapable;
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

    private String getTokenUrl() {
        return "https://graph.qq.com/oauth2.0/token";
    }

    private String getOpenidUrl() {
        return "https://graph.qq.com/oauth2.0/me";
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
            throw new ValidationException(CommonErrorCode.VALIDATION_ERROR, "Subject token param unset");
        }

        if (QQ_AUTHZ_CODE.equals(exchangeParams.getSubjectTokenType())) {
            return exchangeAuthorizationCode(exchangeParams.getSubjectToken());
        } else {
            event.detail(Details.REASON, OAuth2Constants.SUBJECT_TOKEN_TYPE + " invalid");
            event.error(Errors.INVALID_TOKEN_TYPE);
            throw new ValidationException(CommonErrorCode.VALIDATION_ERROR, "Invalid token type");
        }
    }

    private BrokeredIdentityContext exchangeAuthorizationCode(String authorizationCode) {
        String clientId = getConfig().getClientId();
        try {
            return sendTokenRequest(authorizationCode, clientId, null);
        } catch (IOException e) {
            throw new BaseKeycloakException(
                CommonErrorCode.INTERNAL_SERVER_ERROR,
                Response.Status.INTERNAL_SERVER_ERROR,
                e
            );
        }
    }

    public BrokeredIdentityContext sendTokenRequest(String authorizationCode, String clientId, AuthenticationSessionModel authSession) throws IOException {
        String accessToken = getAccessToken(authorizationCode, clientId);
        BrokeredIdentityContext federatedIdentity = doGetFederatedIdentity(accessToken);
        federatedIdentity.setIdp(QQIdentityProvider.this);
        federatedIdentity.setAuthenticationSession(authSession);
        return federatedIdentity;
    }

    public String getAccessToken(String authorizationCode, String clientId) throws IOException {
        String response = generateTokenRequest(authorizationCode, clientId).asString();
        return extractAccessToken(response);
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
                String.format("Fail to get access token, response: %s", response)
            );
        }

        return accessToken;
    }

    public String getOpenid(String accessToken) throws IOException {
        String openidResponse = generateOpenidRequest(accessToken).asString();
        return extractOpenid(openidResponse);
    }

    public SimpleHttp generateOpenidRequest(String accessToken) {
        return SimpleHttp.doGet(getOpenidUrl(), session)
            .param("access_token", accessToken);
    }

    private String extractOpenid(String response) {
        int start = response.indexOf("{");
        int end = response.lastIndexOf("}");
        try {
            String json = response.substring(start, end + 1);
            JSONObject obj = new JSONObject(json);
            return obj.getString("openid");
        } catch (Exception e) {
            throw new QQException(
                CommonErrorCode.INTERNAL_SERVER_ERROR,
                Response.Status.INTERNAL_SERVER_ERROR,
                String.format("Fail to get openid, response: %s", response)
            );
        }
    }

    public JSONObject getUserInfo(String accessToken, String openid) throws IOException {
        String userInfoString = generateUserInfoRequest(accessToken, openid).asString();
        JSONObject userInfo = new JSONObject(userInfoString);
        if (userInfo.getLong("ret") != 0) {
            throw new QQException(
                CommonErrorCode.INTERNAL_SERVER_ERROR,
                Response.Status.INTERNAL_SERVER_ERROR,
                String.format("Fail to get user info, response: %s", userInfoString)
            );
        }

        return userInfo;
    }

    public SimpleHttp generateUserInfoRequest(String accessToken, String openid) {
        return SimpleHttp.doGet(getUserInfoUrl(), session)
            .param("access_token", accessToken)
            .param("oauth_consumer_key", getConfig().getClientId())
            .param("openid", openid);
    }

    @Override
    protected BrokeredIdentityContext doGetFederatedIdentity(String accessToken) {
        try {
            String openid = getOpenid(accessToken);
            JSONObject userInfo = getUserInfo(accessToken, openid);

            BrokeredIdentityContext context = new BrokeredIdentityContext(openid, getConfig());
            context.setUsername("QQ-" + openid);
            context.setEmail(openid + "@qq.jrny.cn");
            context.setIdp(this);

            context.setUserAttribute("nickname", userInfo.optString("nickname", RandomUtils.getRandomNickname()));
            context.setUserAttribute("figureurl_qq_1", userInfo.optString("figureurl_qq_1"));
            context.setUserAttribute("gender", userInfo.optString("gender"));
            context.setUserAttribute("qq_openid", openid);

            return context;
        } catch (IOException e) {
            throw new QQException(
                CommonErrorCode.INTERNAL_SERVER_ERROR,
                Response.Status.INTERNAL_SERVER_ERROR,
                e
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
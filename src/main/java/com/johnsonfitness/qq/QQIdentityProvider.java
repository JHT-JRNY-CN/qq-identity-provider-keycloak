package com.johnsonfitness.qq;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.jboss.logging.Logger;
import org.keycloak.broker.oidc.AbstractOAuth2IdentityProvider;
import org.keycloak.broker.social.SocialIdentityProvider;
import org.keycloak.broker.provider.BrokeredIdentityContext;
import org.keycloak.events.EventBuilder;
import org.keycloak.models.IdentityProviderModel;
import org.keycloak.models.KeycloakSession;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QQIdentityProvider 
    extends AbstractOAuth2IdentityProvider<QQIdentityProviderConfig>
    implements SocialIdentityProvider<QQIdentityProviderConfig> {

    private static final Logger logger = Logger.getLogger(QQIdentityProvider.class);
    private final CloseableHttpClient httpClient = HttpClients.createDefault();
    private final IdentityProviderModel identityProviderModel;

    public QQIdentityProvider(KeycloakSession session, QQIdentityProviderConfig config) {
        super(session, config);
        this.identityProviderModel = config.getModel();
        getConfig().setAuthorizationUrl("https://graph.qq.com/oauth2.0/authorize");
        getConfig().setTokenUrl("https://graph.qq.com/oauth2.0/token");
        getConfig().setUserInfoUrl("https://graph.qq.com/user/get_user_info");
    }

    @Override
    protected String getDefaultScopes() {
        return "get_user_info";
    }

    @Override
    protected BrokeredIdentityContext extractIdentityFromProfile(EventBuilder event, JsonNode profile) {
        String openid = profile.get("openid").asText();
        String nickname = profile.get("nickname").asText();

        BrokeredIdentityContext context = new BrokeredIdentityContext(openid, this.identityProviderModel);
        context.setId(openid);
        context.setUsername(nickname);
        context.setFirstName(nickname);
        context.setBrokerUserId(openid);
        context.setIdp(this);
        return context;
    }


    private String extractOpenId(String accessToken) throws IOException {
        String url = "https://graph.qq.com/oauth2.0/me?access_token=" + accessToken;
        HttpGet request = new HttpGet(url);
        try (CloseableHttpResponse response = httpClient.execute(request)) {
            String body = EntityUtils.toString(response.getEntity());
            Matcher matcher = Pattern.compile("\"openid\":\"(.*?)\"").matcher(body);
            if (matcher.find()) {
                return matcher.group(1);
            }
            throw new RuntimeException("Unable to extract openid");
        }
    }

    public JsonNode fetchQQUserInfo(String accessToken) throws IOException {
        String openId = extractOpenId(accessToken);
        String url = getConfig().getUserInfoUrl() + "?access_token=" + accessToken +
                "&openid=" + openId +
                "&oauth_consumer_key=" + getConfig().getClientId();
        HttpGet request = new HttpGet(url);
        try (CloseableHttpResponse response = httpClient.execute(request)) {
            return mapper.readTree(response.getEntity().getContent());
        }
    }
}
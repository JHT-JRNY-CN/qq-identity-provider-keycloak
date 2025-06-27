public class QQIdentityProvider extends OAuth2IdentityProvider<QQIdentityProviderConfig> 
        implements SocialIdentityProvider<QQIdentityProviderConfig> {

    public QQIdentityProvider(KeycloakSession session, QQIdentityProviderConfig config) {
        super(session, config);
        this.authUrl = "https://graph.qq.com/oauth2.0/authorize";
        this.tokenUrl = "https://graph.qq.com/oauth2.0/token";
        this.userInfoUrl = "https://graph.qq.com/user/get_user_info";
    }

    @Override
    protected BrokeredIdentityContext extractIdentityFromProfile(EventBuilder event, JsonNode profile) {
        BrokeredIdentityContext context = new BrokeredIdentityContext(profile.get("nickname").asText());
        context.setUsername(profile.get("nickname").asText());
        context.setId(profile.get("openid").asText());
        context.setEmail(profile.has("email") ? profile.get("email").asText() : null);
        return context;
    }

    @Override
    protected String getDefaultScopes() {
        return "get_user_info";
    }
}


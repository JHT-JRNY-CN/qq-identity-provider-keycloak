public class QQIdentityProviderFactory extends AbstractOAuth2IdentityProviderFactory<QQIdentityProvider> {
    public static final String PROVIDER_ID = "qq";

    @Override
    public String getName() {
        return "QQ";
    }

    @Override
    public QQIdentityProvider create(KeycloakSession session, IdentityProviderModel model) {
        return new QQIdentityProvider(session, new QQIdentityProviderConfig(model));
    }

    @Override
    public QQIdentityProviderConfig createConfig() {
        return new QQIdentityProviderConfig();
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }
}


package com.johnsonfitness.qq;

import org.keycloak.broker.oidc.OIDCIdentityProviderConfig;
import org.keycloak.models.IdentityProviderModel;

public class QQIdentityProviderConfig extends OIDCIdentityProviderConfig {

    private static final String DISPLAY_ICON_CLASSES = "fa fa-qq";
    private static final String DISPLAY_NAME = "displayName";
    private static final String DEFAULT_DISPLAY_NAME = "Sign in with QQ";

    public QQIdentityProviderConfig(IdentityProviderModel identityProviderModel) {
        super(identityProviderModel);
    }

    public QQIdentityProviderConfig() {
        super();
    }

    @Override
    public void setDisplayName(String displayName) {
        getConfig().put(DISPLAY_NAME, displayName);
    }

    @Override
    public String getDisplayName() {
        var displayName = getConfig().get(DISPLAY_NAME);
        if (displayName == null || displayName.isBlank()) {
            return DEFAULT_DISPLAY_NAME;
        }
        return displayName;
    }

    @Override
    public String getDisplayIconClasses() {
        return DISPLAY_ICON_CLASSES;
    }

}

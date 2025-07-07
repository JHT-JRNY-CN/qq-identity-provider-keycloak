package com.johnsonfitness.qq;

import org.keycloak.broker.oidc.OAuth2IdentityProviderConfig;
import org.keycloak.models.IdentityProviderModel;

public class QQIdentityProviderConfig extends OAuth2IdentityProviderConfig {
    private IdentityProviderModel identityProviderModel;

    public QQIdentityProviderConfig() {
        super();
    }

    public QQIdentityProviderConfig(IdentityProviderModel model) {
        super(model);
        this.identityProviderModel = model;
    }

    public IdentityProviderModel getModel() {
        return this.identityProviderModel;
    }
}
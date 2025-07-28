package com.johnsonfitness.qq;

import jakarta.ws.rs.core.MultivaluedMap;
import org.keycloak.OAuth2Constants;

public class TokenExchangeParams {
    private String subjectToken;
    private String subjectTokenType;

    public TokenExchangeParams(MultivaluedMap<String, String> params) {
        this.subjectToken = params.getFirst(OAuth2Constants.SUBJECT_TOKEN);
        this.subjectTokenType = params.getFirst(OAuth2Constants.SUBJECT_TOKEN_TYPE);

        this.setTypeDefaultIfNull();
    }

    private void setTypeDefaultIfNull() {
        if (this.subjectTokenType == null || this.subjectTokenType.isBlank()) {
            this.subjectTokenType = QQIdentityProvider.QQ_AUTHZ_CODE;
        }
    }

    public String getSubjectToken() {
        return this.subjectToken;
    }

    public String getSubjectTokenType() {
        return this.subjectTokenType;
    }
}

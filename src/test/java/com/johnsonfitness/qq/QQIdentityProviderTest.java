package com.johnsonfitness.qq;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QQIdentityProviderTest {

    @Test
    void testConstants() {
        assertEquals("qq-authz-code", QQIdentityProvider.QQ_AUTHZ_CODE);
        assertEquals("access_token", QQIdentityProvider.ACCESS_TOKEN);
    }

    @Test
    void testConstantsNotNull() {
        assertNotNull(QQIdentityProvider.QQ_AUTHZ_CODE);
        assertNotNull(QQIdentityProvider.ACCESS_TOKEN);
    }

    @Test
    void testConstantsNotEmpty() {
        assertFalse(QQIdentityProvider.QQ_AUTHZ_CODE.isEmpty());
        assertFalse(QQIdentityProvider.ACCESS_TOKEN.isEmpty());
    }
}
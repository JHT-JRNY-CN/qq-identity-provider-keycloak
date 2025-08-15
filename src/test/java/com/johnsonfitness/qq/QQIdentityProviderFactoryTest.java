package com.johnsonfitness.qq;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.keycloak.models.KeycloakSession;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class QQIdentityProviderFactoryTest {

    @Mock
    private KeycloakSession session;
    
    private QQIdentityProviderFactory factory;

    @BeforeEach
    void setUp() {
        factory = new QQIdentityProviderFactory();
    }

    @Test
    void testGetName() {
        assertEquals("QQ", factory.getName());
    }

    @Test
    void testGetId() {
        assertEquals("qq", factory.getId());
    }

    @Test
    void testCreateConfig() {
        QQIdentityProviderConfig config = factory.createConfig();
        assertNotNull(config);
        assertEquals("Sign in with QQ", config.getDisplayName());
        assertEquals("fa fa-qq", config.getDisplayIconClasses());
    }

    @Test
    void testFactoryNotNull() {
        assertNotNull(factory);
    }
}
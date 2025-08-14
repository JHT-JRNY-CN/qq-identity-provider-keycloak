package com.johnsonfitness.qq;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

class QQIdentityProviderConfigTest {

    private QQIdentityProviderConfig config;

    @BeforeEach
    void setUp() {
        config = new QQIdentityProviderConfig();
    }

    @Test
    void testDefaultDisplayName() {
        assertEquals("Sign in with QQ", config.getDisplayName());
    }

    @Test
    void testDisplayIconClasses() {
        assertEquals("fa fa-qq", config.getDisplayIconClasses());
    }

    @Test
    void testSetCustomDisplayName() {
        config.setDisplayName("Custom QQ Login");
        assertEquals("Custom QQ Login", config.getDisplayName());
    }

    @Test
    void testSetEmptyDisplayName() {
        config.setDisplayName("");
        assertEquals("Sign in with QQ", config.getDisplayName());
    }

    @Test
    void testSetBlankDisplayName() {
        config.setDisplayName("   ");
        assertEquals("Sign in with QQ", config.getDisplayName());
    }

    @Test
    void testSetNullDisplayName() {
        config.setDisplayName(null);
        assertEquals("Sign in with QQ", config.getDisplayName());
    }

    @Test
    void testSetDisplayNameWithSpaces() {
        config.setDisplayName("  QQ Login  ");
        assertEquals("  QQ Login  ", config.getDisplayName());
    }

    @Test
    void testSetDisplayNameWithSpecialChars() {
        config.setDisplayName("QQ登入");
        assertEquals("QQ登入", config.getDisplayName());
    }

    @Test
    void testSetLongDisplayName() {
        String longName = "Very Long Display Name for QQ Identity Provider";
        config.setDisplayName(longName);
        assertEquals(longName, config.getDisplayName());
    }
}
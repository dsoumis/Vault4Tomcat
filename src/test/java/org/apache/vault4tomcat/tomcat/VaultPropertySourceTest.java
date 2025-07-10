package org.apache.vault4tomcat.tomcat;

import org.apache.vault4tomcat.core.VaultClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class VaultPropertySourceTest {

    private VaultClient mockVaultClient;
    private VaultPropertySource propertySource;

    @BeforeEach
    void setUp() throws Exception {
        mockVaultClient = mock(VaultClient.class);
        propertySource = new VaultPropertySource(mockVaultClient);
    }

    @Test
    void testValidVaultPlaceholderReturnsSecretValue() throws Exception {
        Map<String, String> secret = Map.of("password", "topsecret");
        when(mockVaultClient.getSecret("secret/app")).thenReturn(secret);

        String value = propertySource.getProperty("vault:secret/app#password");

        assertEquals("topsecret", value);
    }

    @Test
    void testMissingHashInKeyReturnsNull() {
        String result = propertySource.getProperty("vault:secret/app");
        assertNull(result);
    }

    @Test
    void testMalformedPlaceholderReturnsNull() {
        String result = propertySource.getProperty("vault:#");
        assertNull(result);
    }

    @Test
    void testUnknownVaultKeyReturnsNull() throws Exception {
        Map<String, String> secret = Map.of("username", "admin");
        when(mockVaultClient.getSecret("secret/app")).thenReturn(secret);

        String result = propertySource.getProperty("vault:secret/app#password");
        assertNull(result);
    }

    @Test
    void testNonVaultKeyReturnsNull() {
        String result = propertySource.getProperty("java.version");
        assertNull(result);
    }

    @Test
    void testSecretIsCachedAfterFirstCall() throws Exception {
        Map<String, String> secret = Map.of("token", "abc123");
        when(mockVaultClient.getSecret("secret/api")).thenReturn(secret);

        String value1 = propertySource.getProperty("vault:secret/api#token");
        String value2 = propertySource.getProperty("vault:secret/api#token");

        assertEquals("abc123", value1);
        assertEquals("abc123", value2);
        verify(mockVaultClient, times(1)).getSecret("secret/api");
    }

    @Test
    void testVaultClientThrowsExceptionReturnsNull() throws Exception {
        when(mockVaultClient.getSecret("secret/app")).thenThrow(new RuntimeException("Vault error"));

        String result = propertySource.getProperty("vault:secret/app#token");
        assertNull(result);
    }
}

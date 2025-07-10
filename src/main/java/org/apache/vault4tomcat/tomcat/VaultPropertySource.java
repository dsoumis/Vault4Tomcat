package org.apache.vault4tomcat.tomcat;

import org.apache.catalina.Globals;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.IntrospectionUtils;
import org.apache.vault4tomcat.config.VaultConfig;
import org.apache.vault4tomcat.core.VaultClient;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class VaultPropertySource implements IntrospectionUtils.PropertySource {
    private static final String VAULT_PREFIX = "vault:";
    private static final Log log = LogFactory.getLog(VaultPropertySource.class);

    private static final String PROPERTY_FILE_RELATIVE_PATH = "/conf/vault.properties";
    private static final String catalinaHome = System.getProperty(Globals.CATALINA_HOME_PROP);
    private static final String catalinaBase = System.getProperty(Globals.CATALINA_BASE_PROP);

    // Cache for secrets: maps a Vault path to its key-value data
    private final Map<String, Map<String, String>> cache = new ConcurrentHashMap<>();
    private final VaultClient vaultClient;

    // Public no-arg constructor (required by Tomcat)
    public VaultPropertySource() throws Exception {

        String catalina;

        if (new File(catalinaHome + PROPERTY_FILE_RELATIVE_PATH).exists()) {
            catalina = catalinaHome;
        } else {
            catalina = catalinaBase;
        }

        VaultConfig cfg = new VaultConfig(catalina + PROPERTY_FILE_RELATIVE_PATH);

        this.vaultClient = new VaultClient(cfg);
    }

    //ONLY FOR TESTING PURPOSES
    public VaultPropertySource(VaultClient vaultClient) {
        this.vaultClient = vaultClient;
    }

    @Override
    public String getProperty(String key) {
        // Only handle placeholders that start with "vault:"
        if (key == null || !key.startsWith(VAULT_PREFIX)) {
            return null;  // not a Vault placeholder
        }
        String vaultSpec = key.substring(VAULT_PREFIX.length());  // e.g. "path/to/secret#field"
        int sepIndex = vaultSpec.indexOf('#');
        if (sepIndex < 0) {
            log.error("Invalid Vault placeholder (missing '#'): " + key);
            return null;
        }

        String secretPath = vaultSpec.substring(0, sepIndex);
        String secretKey = vaultSpec.substring(sepIndex + 1);
        if (secretPath.isEmpty() || secretKey.isEmpty()) {
            log.error("Invalid Vault placeholder format: " + key);
            return null;
        }

        try {
            // Check cache for this secret path
            Map<String, String> secretData = cache.get(secretPath);
            if (secretData == null) {
                // Not cached yet, retrieve from Vault
                secretData = vaultClient.getSecret(secretPath);
                if (secretData == null) {
                    log.error("Vault secret not found at path: " + secretPath);
                    return null;
                }
                cache.put(secretPath, secretData);  // cache the fetched secret
            }
            // Look up the specific key in the secret data
            String value = secretData.get(secretKey);
            if (value == null) {
                log.error("Vault secret key '" + secretKey + "' not found in path: " + secretPath);
            }
            return value;
        } catch (Exception e) {
            // Handle unexpected errors (e.g., VaultClient exceptions)
            log.error("Error retrieving Vault secret for " + key + ": " + e.getMessage());
            return null;
        }
    }
}

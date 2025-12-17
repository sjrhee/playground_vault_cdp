package com.example;

import com.centralmanagement.CentralManagementProvider;
import com.centralmanagement.CipherTextData;
import com.centralmanagement.ClientObserver;
import com.centralmanagement.RegisterClientParameters;
import com.centralmanagement.policy.CryptoManager;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.nio.charset.StandardCharsets;

public class CadpClient {

    private static final String PROPERTIES_FILE = "cadp.properties";
    private String protectionPolicyName;
    private String userName;

    private static volatile CadpClient instance;

    private CadpClient() {
        initialize();
    }

    public static CadpClient getInstance() {
        if (instance == null) {
            synchronized (CadpClient.class) {
                if (instance == null) {
                    instance = new CadpClient();
                }
            }
        }
        return instance;
    }

    private void initialize() {
        Properties prop = loadProperties();
        if (prop == null) {
            System.err.println("Failed to load cadp.properties");
            return;
        }
        String keyManagerHost = System.getenv("CADP_KEY_MANAGER_HOST");
        if (keyManagerHost == null || keyManagerHost.isEmpty()) {
            keyManagerHost = prop.getProperty("keyManagerHost");
        }

        String keyManagerPort = System.getenv("CADP_KEY_MANAGER_PORT");
        if (keyManagerPort == null || keyManagerPort.isEmpty()) {
            keyManagerPort = prop.getProperty("keyManagerPort");
        }

        String registrationToken = System.getenv("CADP_REGISTRATION_TOKEN");
        if (registrationToken == null || registrationToken.isEmpty()) {
            registrationToken = prop.getProperty("registrationToken");
        }

        this.protectionPolicyName = System.getenv("CADP_PROTECTION_POLICY_NAME");
        if (this.protectionPolicyName == null || this.protectionPolicyName.isEmpty()) {
            this.protectionPolicyName = prop.getProperty("protectionPolicyName");
        }

        this.userName = System.getenv("CADP_USER_NAME");
        if (this.userName == null || this.userName.isEmpty()) {
            this.userName = prop.getProperty("userName");
        }

        registerClient(keyManagerHost, keyManagerPort, registrationToken);
    }

    private Properties loadProperties() {
        Properties prop = new Properties();
        // Load from classpath
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(PROPERTIES_FILE)) {
            if (input == null) {
                System.err.println("Sorry, unable to find " + PROPERTIES_FILE);
                return null;
            }
            prop.load(input);
            return prop;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void registerClient(String keyManagerHost, String keyManagerPort, String registrationToken) {
        try {
            RegisterClientParameters.Builder builder = new RegisterClientParameters.Builder(keyManagerHost,
                    registrationToken.toCharArray());

            if (keyManagerPort != null && !keyManagerPort.isEmpty()) {
                try {
                    int port = Integer.parseInt(keyManagerPort);
                    builder.setWebPort(port);
                } catch (NumberFormatException e) {
                    System.err.println("Invalid port in properties: " + keyManagerPort);
                }
            }

            RegisterClientParameters registerClientParams = builder.build();

            CentralManagementProvider centralManagementProvider = new CentralManagementProvider(registerClientParams);
            centralManagementProvider.addProvider();

            // Status update implementation
            centralManagementProvider.subscribeToStatusUpdate(new ClientObserver<Object, Object>() {
                @Override
                public void notifyStatusUpdate(Object status, Object message) {
                    System.out.println("[CADP Status] Status: " + status + ", Message: " + message);
                }
            });
        } catch (Exception e) {
            System.err.println("Error registering client: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public String enc(String plainText) {
        return enc(this.protectionPolicyName, plainText);
    }

    public String enc(String policyName, String plainText) {
        if (plainText == null)
            return null;
        try {
            CipherTextData cipherTextData = CryptoManager.protect(plainText.getBytes(StandardCharsets.UTF_8),
                    policyName);
            return new String(cipherTextData.getCipherText(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public String dec(String cipherText) {
        return dec(this.protectionPolicyName, cipherText);
    }

    public String dec(String policyName, String cipherText) {
        if (cipherText == null)
            return null;
        try {
            CipherTextData cipherTextData = new CipherTextData();
            cipherTextData.setCipherText(cipherText.getBytes(StandardCharsets.UTF_8));

            byte[] revealedData = CryptoManager.reveal(cipherTextData, policyName, this.userName);
            return new String(revealedData, StandardCharsets.UTF_8);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}

package com.example;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Properties;
import java.io.InputStream;

public class CrdpClient {

    private String baseUrl;
    private String policy;
    private String token;
    private String user;
    private boolean useTls;

    private HttpClient httpClient;
    private final Gson gson;

    private static final int TIMEOUT = 10; // 10 seconds

    private static volatile CrdpClient instance;

    // Singleton getInstance
    public static CrdpClient getInstance() {
        if (instance == null) {
            synchronized (CrdpClient.class) {
                if (instance == null) {
                    instance = new CrdpClient();
                }
            }
        }
        return instance;
    }

    /**
     * Private Constructor for Singleton
     * Loads configuration and initializes the client
     */
    private CrdpClient() {
        this.gson = new Gson();
        initialize();
    }

    private void initialize() {
        Properties props = new Properties();
        try (InputStream is = CrdpClient.class.getClassLoader().getResourceAsStream("crdp.properties")) {
            if (is == null) {
                throw new RuntimeException("Configuration file 'crdp.properties' not found in classpath");
            }
            props.load(is);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load crdp.properties", e);
        }

        String endpoint = props.getProperty("crdp_endpoint");
        this.useTls = Boolean.parseBoolean(props.getProperty("crdp_tls", "true"));
        this.policy = props.getProperty("crdp_policy");
        this.user = props.getProperty("crdp_user_name");
        this.token = props.getProperty("crdp_jwt");

        if (endpoint == null || policy == null || token == null) {
            throw new RuntimeException("Missing required CRDP configuration in crdp.properties");
        }

        String protocol = useTls ? "https" : "http";
        this.baseUrl = protocol + "://" + endpoint;

        try {
            // 시스템 속성으로 호스트네임 검증 비활성화 (JDK 11 HttpClient workaround)
            System.setProperty("jdk.internal.httpclient.disableHostnameVerification", "true");

            SSLContext sslContext = getInsecureSslContext();

            // 호스트네임 검증 비활성화 (개발 환경용)
            javax.net.ssl.SSLParameters sslParams = new javax.net.ssl.SSLParameters();
            sslParams.setEndpointIdentificationAlgorithm(null);

            this.httpClient = HttpClient.newBuilder()
                    .sslContext(sslContext)
                    .sslParameters(sslParams)
                    .connectTimeout(Duration.ofSeconds(TIMEOUT))
                    .build();

            // Auto Warmup
            warmup();

        } catch (Exception e) {
            throw new RuntimeException("HttpClient 초기화 실패", e);
        }
    }

    public boolean isUseTls() {
        return useTls;
    }

    /**
     * 연결 워밍업 (Warm-up)
     */
    public void warmup() {
        try {
            String url = baseUrl + "/v1/protect";
            Map<String, String> request = new HashMap<>();
            request.put("protection_policy_name", policy);
            request.put("data", "WARMUP");
            post(url, request);
        } catch (Exception e) {
            // 워밍업 실패는 무시하지만 로깅은 남김
            System.out.println("CRDP Warmup failed (ignoring): " + e.getMessage());
        }
    }

    /**
     * 데이터 암호화 (Protect)
     */
    public String enc(String plaintext) throws Exception {
        if (plaintext == null)
            throw new IllegalArgumentException("입력 데이터는 null일 수 없습니다.");

        String url = baseUrl + "/v1/protect";
        Map<String, String> request = new HashMap<>();
        request.put("protection_policy_name", policy);
        request.put("data", plaintext);

        String responseJson = post(url, request);
        JsonObject response = gson.fromJson(responseJson, JsonObject.class);

        return response.get("protected_data").getAsString();
    }

    /**
     * 데이터 복호화 (Reveal)
     */
    public String dec(String encrypted) throws Exception {
        if (encrypted == null)
            throw new IllegalArgumentException("입력 데이터는 null일 수 없습니다.");

        String url = baseUrl + "/v1/reveal";
        Map<String, String> request = new HashMap<>();
        request.put("protection_policy_name", policy);
        request.put("protected_data", encrypted);
        request.put("username", user);

        String responseJson = post(url, request);
        JsonObject response = gson.fromJson(responseJson, JsonObject.class);

        return response.get("data").getAsString();
    }

    private String post(String url, Object requestBody) throws Exception {
        String jsonBody = gson.toJson(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .timeout(Duration.ofSeconds(TIMEOUT))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() >= 400) {
            throw new RuntimeException("CRDP 서버 오류 (HTTP " + response.statusCode() + "): " + response.body());
        }

        return response.body();
    }

    private SSLContext getInsecureSslContext() throws Exception {
        TrustManager[] trustAll = new TrustManager[] {
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }

                    public void checkClientTrusted(X509Certificate[] certs, String authType) {
                    }

                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                    }
                }
        };
        SSLContext sc = SSLContext.getInstance("TLS");
        sc.init(null, trustAll, new java.security.SecureRandom());
        return sc;
    }
}

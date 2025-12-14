package com.example;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.FileInputStream;
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
import java.io.IOException;
import java.util.Properties;

public class CrdpClient {

    private final String baseUrl;
    private final String policy;
    private final String token;
    private final String user;
    private final boolean useTls;

    private final HttpClient httpClient;
    private final Gson gson;

    private static final int TIMEOUT = 10; // 10 seconds

    /**
     * 설정 파일에서 클라이언트 생성 (Factory Method)
     * 
     * @param filePath 설정 파일 경로 (예: "crdp.properties")
     * @return 초기화된 CrdpClient 객체
     * @throws IOException 설정 파일을 읽을 수 없거나 필수 항목이 누락된 경우
     */
    public static CrdpClient fromConfigFile(String filePath) throws IOException {
        Properties config = new Properties();
        try (FileInputStream fis = new FileInputStream(filePath)) {
            config.load(fis);
        }

        String endpoint = config.getProperty("crdp_endpoint");
        String policy = config.getProperty("crdp_policy");
        String token = config.getProperty("crdp_jwt");
        String user = config.getProperty("crdp_user");
        boolean useTls = Boolean.parseBoolean(config.getProperty("crdp_tls", "true")); // Default to true if not specified

        if (endpoint == null || policy == null || token == null || user == null) {
            throw new IOException("설정 파일(" + filePath + ")에 필수 항목(crdp_endpoint, crdp_policy, crdp_jwt, crdp_user)이 누락되었습니다.");
        }

        return new CrdpClient(endpoint, policy, token, user, useTls);
    }

    /**
     * Factory method to create CrdpClient from crdp.properties
     */
    public static CrdpClient createFromProperties() {
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
        boolean useTls = Boolean.parseBoolean(props.getProperty("crdp_tls", "true"));
        String policy = props.getProperty("crdp_policy");
        String user = props.getProperty("crdp_user_name");
        String jwt = props.getProperty("crdp_jwt");

        if (endpoint == null || policy == null || jwt == null) {
            throw new RuntimeException("Missing required CRDP configuration in crdp.properties");
        }

        return new CrdpClient(endpoint, policy, jwt, user, useTls);
    }

    /**
     * 생성자
     * 
     * @param endpoint CRDP 서버 주소 (예: "192.168.0.1:443")
     * @param policy   보호 정책 이름 (예: "P01")
     * @param token    JWT 인증 토큰
     * @param user     사용자 이름 (reveal 요청 시 필요)
     * @param useTls   TLS 사용 여부
     */
    public CrdpClient(String endpoint, String policy, String token, String user, boolean useTls) {
        this.useTls = useTls;
        String protocol = useTls ? "https" : "http";
        this.baseUrl = protocol + "://" + endpoint;
        this.policy = policy;
        this.token = token;
        this.user = user;
        this.gson = new Gson();

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

package com.example;

import com.google.gson.Gson;

import com.google.gson.annotations.SerializedName;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.time.Duration;

public class CrdpClient {

    private final String baseUrl;
    private final String policy;
    private final String token;
    private final String user;

    private final HttpClient httpClient;
    private final Gson gson;

    private static final int TIMEOUT = 10; // 10 seconds

    /**
     * 생성자
     * 
     * @param endpoint CRDP 서버 주소 (예: "192.168.0.1:443")
     * @param policy   보호 정책 이름 (예: "P01")
     * @param token    JWT 인증 토큰
     * @param user     사용자 이름 (reveal 요청 시 필요)
     */
    public CrdpClient(String endpoint, String policy, String token, String user) {
        this.baseUrl = "https://" + endpoint;
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

    /**
     * 연결 워밍업 (Warm-up)
     */
    public void warmup() {
        try {
            String url = baseUrl + "/v1/protect";
            ProtectRequest request = new ProtectRequest(policy, "WARMUP");
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
        ProtectRequest request = new ProtectRequest(policy, plaintext);

        String responseJson = post(url, request);
        ProtectResponse response = gson.fromJson(responseJson, ProtectResponse.class);

        return response.protectedData;
    }

    /**
     * 데이터 복호화 (Reveal)
     */
    public String dec(String encrypted) throws Exception {
        if (encrypted == null)
            throw new IllegalArgumentException("입력 데이터는 null일 수 없습니다.");

        String url = baseUrl + "/v1/reveal";
        RevealRequest request = new RevealRequest(policy, encrypted, user);

        String responseJson = post(url, request);
        RevealResponse response = gson.fromJson(responseJson, RevealResponse.class);

        return response.data;
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

    // --- DTO Classes ---

    private static class ProtectRequest {
        @SerializedName("protection_policy_name")
        String policyName;
        String data;

        ProtectRequest(String policyName, String data) {
            this.policyName = policyName;
            this.data = data;
        }
    }

    private static class ProtectResponse {
        @SerializedName("protected_data")
        String protectedData;
    }

    private static class RevealRequest {
        @SerializedName("protection_policy_name")
        String policyName;
        @SerializedName("protected_data")
        String protectedData;
        String username;

        RevealRequest(String policyName, String protectedData, String username) {
            this.policyName = policyName;
            this.protectedData = protectedData;
            this.username = username;
        }
    }

    private static class RevealResponse {
        String data;
    }
}

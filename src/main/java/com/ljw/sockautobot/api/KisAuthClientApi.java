package com.ljw.sockautobot.api;

import lombok.extern.slf4j.Slf4j;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
public class KisAuthClientApi {

    private static final String TOKEN_URL = "https://openapivts.koreainvestment.com:29443/oauth2/tokenP";
    private static final String TOKEN_FILE_PATH = "token.json";

    private final RestTemplate restTemplate = new RestTemplate();

    public String getAccessToken(String appKey, String appSecret) throws JSONException {
        // ✅ 1. 기존 토큰이 있으면 읽기 (appKey 검사 포함)
        String cachedToken = readCachedToken(appKey);
        if (cachedToken != null) {
            log.info("🔁 기존 토큰 재사용");
            log.info(cachedToken);
            return cachedToken;
        }

        log.info("🆕 새 토큰 발급 요청 중...");

        // ✅ 2. 새 토큰 요청
        JSONObject body = new JSONObject();
        body.put("grant_type", "client_credentials");
        body.put("appkey", appKey);
        body.put("appsecret", appSecret);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>(body.toString(), headers);

        ResponseEntity<String> response = restTemplate.postForEntity(TOKEN_URL, entity, String.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            log.error("❌ 토큰 요청 실패: HTTP {}", response.getStatusCode());
            throw new RuntimeException("토큰 발급 실패: " + response);
        }

        JSONObject json = new JSONObject(response.getBody());

        if (!json.has("access_token")) {
            log.error("❌ 토큰 응답에 access_token 없음: {}", json);
            throw new RuntimeException("토큰 발급 실패: access_token 없음");
        }

        String token = json.getString("access_token");

        String expiredAtStr = json.optString("access_token_token_expired", null);

        // ✅ 3. 만료 시간 계산 (기본 23시간)
        LocalDateTime expiresAt = expiredAtStr != null
                ? LocalDateTime.parse(expiredAtStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                : LocalDateTime.now().plusHours(23);

        // ✅ 4. 토큰 + appKey 저장
        saveTokenToFile(appKey, token, expiresAt);

        log.info("✅ 새 토큰 발급 완료. 만료 예정 시각: {}", expiresAt);
        log.info(token);
        return token;
    }

    // ✅ 토큰 읽기 (appKey 검사 포함)
    private String readCachedToken(String currentAppKey) {
        try {
            Path path = Paths.get(TOKEN_FILE_PATH);
            if (!Files.exists(path)) return null;

            String content = Files.readString(path);
            if (content.isBlank()) return null;

            JSONObject json = new JSONObject(content);

            String savedAppKey = json.optString("appkey", "");
            String token = json.optString("access_token", "");
            String expiresAtStr = json.optString("expires_at", "");

            if (token.isEmpty() || expiresAtStr.isEmpty()) {
                log.warn("⚠️ 토큰 파일 내용 손상 → 삭제 후 재발급");
                Files.deleteIfExists(path);
                return null;
            }

            // ✅ appKey가 다르면 무조건 새 토큰 발급
            if (!savedAppKey.equals(currentAppKey)) {
                log.warn("🔄 appKey 변경 감지 → 새 토큰 발급");
                Files.deleteIfExists(path);
                return null;
            }

            LocalDateTime expiresAt = LocalDateTime.parse(expiresAtStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);

            // ✅ 만료 5분 전이면 새 토큰 발급
            if (LocalDateTime.now().isBefore(expiresAt.minusMinutes(5))) {
                return token;
            } else {
                log.warn("⏰ 토큰 만료됨 → 재발급 필요");
                Files.deleteIfExists(path);
                return null;
            }

        } catch (Exception e) {
            log.error("⚠️ 토큰 파일 읽기 실패: {}", e.getMessage());
            try {
                Files.deleteIfExists(Paths.get(TOKEN_FILE_PATH));
            } catch (IOException ignore) {}
            return null;
        }
    }

    // ✅ 토큰 저장 (appKey 포함)
    private void saveTokenToFile(String appKey, String token, LocalDateTime expiresAt) {
        try {
            JSONObject json = new JSONObject();
            json.put("appkey", appKey);
            json.put("access_token", token);
            json.put("expires_at", expiresAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

            Files.writeString(Paths.get(TOKEN_FILE_PATH), json.toString(),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            log.info("💾 토큰 파일 저장 완료: {}", TOKEN_FILE_PATH);
        } catch (IOException e) {
            log.error("❌ 토큰 파일 저장 실패: {}", e.getMessage());
        }
    }
}

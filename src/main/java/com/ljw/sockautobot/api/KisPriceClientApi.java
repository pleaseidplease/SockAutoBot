package com.ljw.sockautobot.api;

import org.json.JSONObject;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;

@Component
public class KisPriceClientApi {

    // ==========================================================
    // 🔗 실거래 / 모의투자 URL 자동 분기
    // ==========================================================
    private static final String REAL_BASE = "https://openapi.koreainvestment.com:9443";
    private static final String VIRTUAL_BASE = "https://openapivts.koreainvestment.com:29443";

    private final RestTemplate restTemplate;

    public KisPriceClientApi() {
        this.restTemplate = new RestTemplateBuilder()
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(3))
                .build();
    }


    // ==========================================================
    // 🔧 공통 GET 요청 실행 함수
    // ==========================================================
    private JSONObject sendGet(
            String url,
            String token,
            String appKey,
            String appSecret,
            String trId
    ) {

        HttpHeaders headers = new HttpHeaders();
        headers.set("content-type", "application/json; charset=utf-8");
        headers.set("authorization", "Bearer " + token);
        headers.set("appkey", appKey);
        headers.set("appsecret", appSecret);
        headers.set("tr_id", trId);
        headers.set("custtype", "P");

        try {

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class
            );

            return new JSONObject(response.getBody());

        } catch (Exception e) {
            System.err.println("❌ [KIS API] 요청 실패: " + e.getMessage());
            return new JSONObject();
        }
    }


    // ==========================================================
    // 📌 현재가 조회(실거래/모의투자 자동 처리)
    // ==========================================================
    public JSONObject getStockInfo(String token, String appKey, String appSecret, String symbol, String mode) {

        if (symbol == null || symbol.length() != 6) {
            System.err.println("⚠️ [KIS] 종목코드 형식이 잘못됨: " + symbol);
            return new JSONObject();
        }

        String baseUrl = mode.equalsIgnoreCase("real") ? REAL_BASE : VIRTUAL_BASE;

        String url = UriComponentsBuilder.fromHttpUrl(
                        baseUrl + "/uapi/domestic-stock/v1/quotations/inquire-price")
                .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                .queryParam("FID_INPUT_ISCD", symbol)
                .toUriString();

        JSONObject json = sendGet(url, token, appKey, appSecret, "FHKST01010100");

        if (!json.has("output")) {
            System.err.println("⚠️ [KIS] output 필드 없음 -> 응답: " + json);
            return new JSONObject();
        }

        return json.getJSONObject("output");
    }


    // ==========================================================
    // 📌 현재가만 안전하게 반환
    // ==========================================================
    public double getStockPrice(String token, String appKey, String appSecret, String symbol, String mode) {

        JSONObject info = getStockInfo(token, appKey, appSecret, symbol, mode);

        String priceStr = info.optString("stck_prpr", "0");

        try {
            return Double.parseDouble(priceStr);
        } catch (Exception e) {
            System.err.println("⚠️ 가격 파싱 실패: " + priceStr);
            return 0; // 절대 예외 터지지 않게 보호
        }
    }


    // ==========================================================
    // 📌 전일 종가 조회 (하이브리드 전략 핵심)
    // ==========================================================
    public double getPrevClose(String token, String appKey, String appSecret, String symbol, String mode) {

        if (symbol == null || symbol.length() != 6) {
            return 0;
        }

        String baseUrl = mode.equalsIgnoreCase("real") ? REAL_BASE : VIRTUAL_BASE;

        // 전일 종가 조회 TR
        String url = UriComponentsBuilder.fromHttpUrl(
                        baseUrl + "/uapi/domestic-stock/v1/quotations/inquire-daily-price")
                .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                .queryParam("FID_INPUT_ISCD", symbol)
                .toUriString();

        JSONObject json = sendGet(url, token, appKey, appSecret, "FHKST01010400");

        if (!json.has("output")) {
            System.err.println("⚠️ [KIS] 전일 종가 조회 실패: " + json);
            return 0;
        }

        JSONObject out = json.getJSONObject("output");

        String prev = out.optString("stck_clpr", "0");

        try {
            return Double.parseDouble(prev);
        } catch (Exception e) {
            return 0;
        }
    }
}

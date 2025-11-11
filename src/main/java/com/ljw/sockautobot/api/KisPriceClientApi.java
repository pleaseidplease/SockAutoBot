package com.ljw.sockautobot.api;

import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class KisPriceClientApi {

    private static final String PRICE_URL =
            "https://openapivts.koreainvestment.com:29443/uapi/domestic-stock/v1/quotations/inquire-price";

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * ✅ 종목 정보 조회 (현재가 + 종목명 + 시가/고가/저가 등)
     * 반환 예시:
     * {
     *   "stck_prpr": "612000",
     *   "prdt_name": "SK하이닉스",
     *   "stck_oprc": "609000",
     *   "stck_hgpr": "614000",
     *   "stck_lwpr": "607000"
     * }
     */
    public JSONObject getStockInfo(String token, String appKey, String appSecret, String symbol) throws JSONException {

        HttpHeaders headers = new HttpHeaders();
        headers.set("content-type", "application/json; charset=utf-8");
        headers.set("authorization", "Bearer " + token);
        headers.set("appkey", appKey);
        headers.set("appsecret", appSecret);
        headers.set("tr_id", "FHKST01010100"); // ✅ 모의투자용 현재가 조회 TR
        headers.set("custtype", "P"); // ✅ 개인

        // ✅ GET 요청 URL 구성
        String url = UriComponentsBuilder.fromHttpUrl(PRICE_URL)
                .queryParam("FID_COND_MRKT_DIV_CODE", "J")   // ✅ J: 주식
                .queryParam("FID_INPUT_ISCD", symbol)        // ✅ 예: 000660
                .toUriString();

        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            JSONObject json = new JSONObject(response.getBody());

            if (!json.has("output")) {
                System.err.println("⚠️ [KIS] output 필드가 없습니다. 응답 확인 필요: " + json);
                return new JSONObject();
            }

            JSONObject output = json.getJSONObject("output");

            // ✅ 필요한 정보만 정리해서 반환
            JSONObject result = new JSONObject();
            result.put("symbol", symbol);
            result.put("prdt_name", output.optString("prdt_name", "알 수 없음"));
            result.put("stck_prpr", output.optString("stck_prpr", "0"));  // 현재가
            result.put("stck_oprc", output.optString("stck_oprc", "0"));  // 시가
            result.put("stck_hgpr", output.optString("stck_hgpr", "0"));  // 고가
            result.put("stck_lwpr", output.optString("stck_lwpr", "0"));  // 저가

            return result;

        } catch (Exception e) {
            System.err.println("❌ [KIS API] 종목 정보 조회 실패: " + e.getMessage());
            return new JSONObject();
        }
    }

    /**
     * ✅ 현재가만 반환 (기존 호환용)
     */
    public double getStockPrice(String token, String appKey, String appSecret, String symbol) throws JSONException {
        JSONObject info = getStockInfo(token, appKey, appSecret, symbol);
        return Double.parseDouble(info.optString("stck_prpr", "0"));
    }
}

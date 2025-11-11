package com.ljw.sockautobot.api;

import lombok.RequiredArgsConstructor;
import org.json.JSONException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import org.json.JSONObject;

@Component
@RequiredArgsConstructor
public class KisTradeClientApi {

    private final RestTemplate restTemplate = new RestTemplate();

    public JSONObject buyStock(String token, String appKey, String appSecret, String accountNo, String symbol, int qty, int price) throws JSONException {
        String url = "https://openapivts.koreainvestment.com:29443/uapi/domestic-stock/v1/trading/order-cash";

        JSONObject body = new JSONObject();
        body.put("CANO", accountNo.substring(0, 8));
        body.put("ACNT_PRDT_CD", accountNo.substring(8));
        body.put("PDNO", symbol);
        body.put("ORD_DVSN", "00"); // 지정가
        body.put("ORD_QTY", String.valueOf(qty));
        body.put("ORD_UNPR", String.valueOf(price));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("content-type", "application/json; charset=utf-8");
        headers.set("authorization", "Bearer " + token);
        headers.set("appkey", appKey);
        headers.set("appsecret", appSecret);
        headers.set("tr_id", "VTTC0012U"); // 모의투자 매수용
        headers.set("custtype", "P");

        HttpEntity<String> entity = new HttpEntity<>(body.toString(), headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

        return new JSONObject(response.getBody());
    }

    public JSONObject sellStock(String token, String appKey, String appSecret, String accountNo, String symbol, int qty, int price) throws JSONException {

        String url = "https://openapivts.koreainvestment.com:29443/uapi/domestic-stock/v1/trading/order-cash";

        JSONObject body = new JSONObject();
        body.put("CANO", accountNo.substring(0, 8));
        body.put("ACNT_PRDT_CD", accountNo.substring(8));
        body.put("PDNO", symbol);

        // ✅ price가 0이면 시장가로 처리
        if (price <= 0) {
            body.put("ORD_DVSN", "01"); // 시장가
            body.put("ORD_UNPR", "0");
        } else {
            body.put("ORD_DVSN", "00"); // 지정가
            body.put("ORD_UNPR", String.valueOf(price));
        }

        body.put("ORD_QTY", String.valueOf(qty));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("content-type", "application/json; charset=utf-8");
        headers.set("authorization", "Bearer " + token);
        headers.set("appkey", appKey);
        headers.set("appsecret", appSecret);
        headers.set("tr_id", "VTTC0801U"); // ✅ 모의투자 매도용 TR ID (시장가/지정가 공통)
        headers.set("custtype", "P");

        HttpEntity<String> entity = new HttpEntity<>(body.toString(), headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

        return new JSONObject(response.getBody());
    }
}


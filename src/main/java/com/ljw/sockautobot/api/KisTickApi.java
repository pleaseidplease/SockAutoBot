package com.ljw.sockautobot.api;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class KisTickApi {

    private final RestTemplate restTemplate = new RestTemplate();

    public double getTickStrength(String token, String appKey, String appSecret, String symbol) {
        String url = "https://openapivts.koreainvestment.com:29443/uapi/domestic-stock/v1/quotations/inquire-ccnl";

        HttpHeaders headers = new HttpHeaders();
        headers.set("content-type","application/json; charset=utf-8");
        headers.set("authorization", "Bearer " + token);
        headers.set("appkey", appKey);
        headers.set("appsecret", appSecret);
        headers.set("tr_id", "FHKST01010300");
        headers.set("custtype", "P");

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url)
                .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                .queryParam("FID_INPUT_ISCD", symbol);

        HttpEntity<String> entity = new HttpEntity<>(headers);

        JSONObject json = new JSONObject(
                restTemplate.exchange(builder.toUriString(), HttpMethod.GET, entity,
                        String.class).getBody()
        );

        // output 배열 읽기
        JSONArray arr = json.optJSONArray("output");
        if (arr == null || arr.isEmpty()) {
            return 100; // 기본값
        }

        // 최신 체결 (index = 0)
        JSONObject latest = arr.getJSONObject(0);

        // ⭐ 체결강도 = tday_rltv
        return latest.optDouble("tday_rltv", 100);
    }
}

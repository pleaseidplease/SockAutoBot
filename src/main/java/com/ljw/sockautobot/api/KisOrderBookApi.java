package com.ljw.sockautobot.api;

import org.json.JSONObject;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class KisOrderBookApi {

    private final RestTemplate rt = new RestTemplate();

    public JSONObject getOrderBook(String token, String appKey, String appSecret, String symbol) {

        String url = "https://openapivts.koreainvestment.com:29443/uapi/domestic-stock/v1/quotations/inquire-asking-price-exp-ccn";

        HttpHeaders headers = new HttpHeaders();
        headers.set("content-type", "application/json; charset=utf-8");
        headers.set("authorization", "Bearer " + token);
        headers.set("appkey", appKey);
        headers.set("appsecret", appSecret);
        headers.set("tr_id", "FHKST01010200");   // 🔥 모의투자 호가창 TR
        headers.set("custtype", "P");

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url)
                .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                .queryParam("FID_INPUT_ISCD", symbol);

        HttpEntity<String> entity = new HttpEntity<>(headers);

        String raw = rt.exchange(builder.toUriString(), HttpMethod.GET, entity, String.class)
                .getBody();



        JSONObject json = new JSONObject(raw);

        return json.optJSONObject("output1");
    }
}


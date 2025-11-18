package com.ljw.sockautobot.api;

import org.json.JSONObject;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class KisMarketApi {

    private final RestTemplate restTemplate = new RestTemplate();
    private final KisPriceClientApi priceApi;

    public KisMarketApi(KisPriceClientApi priceApi) {
        this.priceApi = priceApi;
    }

    // ⭐ KOSPI = KODEX200 (069500)
    public double getKospiIndex(String token, String appKey, String appSecret) {
        return priceApi.getStockPrice(token, appKey, appSecret, "069500", "virtual");
    }

    // ⭐ KOSDAQ = KODEX 코스닥150 (229200)
    public double getKosdaqIndex(String token, String appKey, String appSecret) {
        return priceApi.getStockPrice(token, appKey, appSecret, "229200", "virtual");
    }
}


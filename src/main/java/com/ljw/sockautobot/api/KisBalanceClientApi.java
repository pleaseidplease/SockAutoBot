package com.ljw.sockautobot.api;

import org.json.JSONObject;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class KisBalanceClientApi {
    private static final String BALANCE_URL =
            "https://openapivts.koreainvestment.com:29443/uapi/domestic-stock/v1/trading/inquire-balance";

    private final RestTemplate restTemplate = new RestTemplate();

    public JSONObject getBalance(String token, String appKey, String appSecret, String accountNo) {
        try {
            String cano = accountNo.substring(0, 8);
            String acntCd = accountNo.substring(8);

            String url = UriComponentsBuilder.fromHttpUrl(BALANCE_URL)
                    .queryParam("CANO", cano)
                    .queryParam("ACNT_PRDT_CD", acntCd)
                    .queryParam("AFHR_FLPR_YN", "N")
                    .queryParam("OFL_YN", "N")
                    .queryParam("INQR_DVSN", "01")
                    .queryParam("UNPR_DVSN", "01")
                    .queryParam("FUND_STTL_ICLD_YN", "N")
                    .queryParam("FNCG_AMT_AUTO_RDPT_YN", "N")
                    .queryParam("PRCS_DVSN", "00")
                    .queryParam("CTX_AREA_FK100", "")
                    .queryParam("CTX_AREA_NK100", "")
                    .toUriString();

            HttpHeaders headers = new HttpHeaders();
            headers.set("content-type", "application/json; charset=utf-8");
            headers.set("authorization", "Bearer " + token);
            headers.set("appkey", appKey);
            headers.set("appsecret", appSecret);
            headers.set("tr_id", "VTTC8434R"); // ✅ 모의투자 잔고조회용 TR ID

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            JSONObject json = new JSONObject(response.getBody());
            return json;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}

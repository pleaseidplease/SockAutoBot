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
            // 1) 숫자만 남기기 (하이픈 제거)
            String clean = accountNo.replaceAll("[^0-9]", "");

            // 2) 최소 10자리 아니면 오류
            if (clean.length() < 10) {
                throw new IllegalArgumentException("❌ 계좌번호 형식 오류: " + accountNo);
            }

            // 3) 앞 8자리 = CANO, 뒤 2자리 = 상품코드
            String cano = clean.substring(0, 8);
            String acntCd = clean.substring(8, 10);

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

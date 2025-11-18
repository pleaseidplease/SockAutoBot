package com.ljw.sockautobot.service;

import com.ljw.sockautobot.api.*;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.json.JSONObject;
import org.json.JSONException;

@Service
@RequiredArgsConstructor
public class AutoTradeService {

    private final KisAuthClientApi authClient;
    private final KisPriceClientApi priceClient;
    private final KisTradeClientApi tradeClient;
    private final KisBalanceClientApi balanceClient;

    private final TradeCalculatorHybrid calculator;
    private final ProfitTracker profitTracker;

    @Value("${kis.app-key}") private String appKey;
    @Value("${kis.app-secret}") private String appSecret;
    @Value("${kis.account-no}") private String accountNo;

    private final KisRateLimiter limiter = new KisRateLimiter();

    private String token;
    private int qty = 0;
    private double avgBuyPrice = 0;

    private volatile String SYMBOL = "000660";

    public int getQty() { return qty; }
    public double getAvgBuyPrice() { return avgBuyPrice; }
    public String getSymbol(){ return SYMBOL; }


    // 주식 종목 변경
    public void updateSymbol(String newSymbol){
        if(newSymbol == null || newSymbol.isBlank()){
            System.out.println("종목코드가 비어있습니다.");
            return;
        }

        this.SYMBOL = newSymbol.trim();
        calculator.resetDaily();

        try {
            JSONObject balanceJson = balanceClient.getBalance(token, appKey, appSecret, accountNo);
            loadCurrentHolding(balanceJson);
        } catch (Exception e) {
            e.printStackTrace();
            this.qty = 0;
            this.avgBuyPrice = 0;
        }

        System.out.println("종목 변경 : " + this.SYMBOL +
                ", 보유수량=" + qty + ", 평균가=" + avgBuyPrice);
    }



    @PostConstruct
    public void initDaily() throws Exception {

        token = authClient.getAccessToken(appKey, appSecret);

        limiter.waitForNext();
        double prevClose = priceClient.getPrevClose(token, appKey, appSecret, SYMBOL, "virtual");

        calculator.setPrevClose(prevClose);
        calculator.resetDaily();

        qty = 0;
        avgBuyPrice = 0;

        limiter.waitForNext();
        JSONObject balanceJson = balanceClient.getBalance(token, appKey, appSecret, accountNo);

        loadCurrentHolding(balanceJson);
        System.out.println("잔고 조회 : " + balanceJson);
        profitTracker.trackBalance(balanceJson, false);

        System.out.println("🌅 새날 시작 — 전일 종가: " + prevClose);
    }


    // ============================================================
    //  🚀 하이브리드 자동매매 (2초마다 실행)
    // ============================================================
    @Scheduled(cron = "*/2 * 9-15 * * MON-FRI")
    public void autoTrade() throws Exception {

        if (token == null) token = authClient.getAccessToken(appKey, appSecret);

        limiter.waitForNext();
        double price = priceClient.getStockPrice(token, appKey, appSecret, SYMBOL, "virtual");
        calculator.addPrice(price);


        double dailyMomentum = calculator.getDailyMomentum(price);
        double shortMA = calculator.getShortMA();
        double longMA = calculator.getLongMA();
        double atr = calculator.getATR();

        double slope = calculator.getSlope();
        double accel = calculator.getAccel();
        double instantMom = calculator.getInstantMomentum();


        // ---------------------- 로그 ----------------------
        System.out.printf(
                "\n📊 price=%.2f qty=%d avg=%.2f | MOM=%.2f%% | slope=%.4f accel=%.4f instMom=%.3f%% | MA=%.2f/%.2f | ATR=%.3f\n",
                price, qty, avgBuyPrice, dailyMomentum, slope, accel, instantMom, shortMA, longMA, atr
        );


        // ============================================================
        //  🟢 1차 매수
        // ============================================================
        if (qty == 0 && calculator.shouldBuyHybrid(price)) {

            limiter.waitForNext();
            tradeClient.buyStock(token, appKey, appSecret, accountNo, SYMBOL, 1, (int) price);

            // ⭐ 매수 직후 실시간 잔고 조회로 실제 보유수량 반영
            JSONObject balanceJson = balanceClient.getBalance(token, appKey, appSecret, accountNo);
            loadCurrentHolding(balanceJson);
            profitTracker.trackBalance(balanceJson, true);

            System.out.println("🟢 [1차 매수] 조건 충족");
            return;
        }


        // ============================================================
        //  🟢 2차 매수 — 전고점 돌파 시도
        // ============================================================
        if (qty == 1 && price > avgBuyPrice * 1.002) {

            limiter.waitForNext();
            tradeClient.buyStock(token, appKey, appSecret, accountNo, SYMBOL, 1, (int) price);

            // ⭐ 잔고에서 다시 확인 (부분체결 대비)
            JSONObject balanceJson = balanceClient.getBalance(token, appKey, appSecret, accountNo);
            loadCurrentHolding(balanceJson);
            profitTracker.trackBalance(balanceJson, true);

            System.out.println("🟢 [2차 매수]");
            return;
        }


        // ============================================================
        //  🟢 3차 매수 — 강한 추세 유지
        // ============================================================
        if (qty == 2 && shortMA > longMA && slope > 0) {

            limiter.waitForNext();
            tradeClient.buyStock(token, appKey, appSecret, accountNo, SYMBOL, 1, (int) price);

            JSONObject balanceJson = balanceClient.getBalance(token, appKey, appSecret, accountNo);
            loadCurrentHolding(balanceJson);
            profitTracker.trackBalance(balanceJson, true);

            System.out.println("🟢 [3차 매수]");
            return;
        }


        // ============================================================
        //  🔴 매도
        // ============================================================
        if (qty > 0 && calculator.shouldSellHybrid(price, avgBuyPrice)) {

            limiter.waitForNext();
            tradeClient.sellStock(token, appKey, appSecret, accountNo, SYMBOL, qty, 0);

            System.out.println("🔴 [매도] 조건 충족");

            // ⭐ 매도 직후 최신 잔고 조회
            JSONObject balanceJson = balanceClient.getBalance(token, appKey, appSecret, accountNo);

            // ⭐ 실제 보유수량/평균단가 다시 계산
            loadCurrentHolding(balanceJson);

            // ⭐ 잔고 변화 기록
            profitTracker.trackBalance(balanceJson, true);

            // ⭐ 수익기록 — qty는 loadCurrentHolding() 이후 값 사용해야 함
            profitTracker.recordProfit(price, avgBuyPrice, qty);

        }
    }

    // 보유수량 확인
    private void loadCurrentHolding(JSONObject balanceJson) {
        var list = balanceJson.optJSONArray("output1");
        if (list == null) return;

        for (int i = 0; i < list.length(); i++) {
            var item = list.getJSONObject(i);

            if (item.optString("pdno", "").trim().equals(SYMBOL.trim())) {
                this.qty = item.optInt("hldg_qty", 0);
                this.avgBuyPrice = item.optDouble("pchs_avg_pric", 0);
                System.out.println("📌 계좌 보유 상태 로드 — qty=" + qty + " avgBuyPrice=" + avgBuyPrice);
                return;
            }
        }

        // 계좌에 종목이 없을 때
        this.qty = 0;
        this.avgBuyPrice = 0;
    }
}

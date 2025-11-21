package com.ljw.sockautobot.service;

import com.ljw.sockautobot.api.*;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.json.JSONObject;

@Service
@RequiredArgsConstructor
public class AutoTradeService {

    private final KisMarketApi marketApi;

    private final KisAuthClientApi authClient;
    private final KisPriceClientApi priceClient;
    private final KisTradeClientApi tradeClient;
    private final KisBalanceClientApi balanceClient;
    private final KisTickApi kisTickApi;
    private final KisOrderBookApi kisOrderBookApi;

    private final TradeCalculatorHybrid calculator;
    private final ProfitTracker profitTracker;

    @Value("${kis.app-key}") private String appKey;
    @Value("${kis.app-secret}") private String appSecret;
    @Value("${kis.account-no}") private String accountNo;

    // 🔥 초당 3건 제한 → 350ms 간격 유지
    private final KisRateLimiter limiter = new KisRateLimiter();

    private String token;
    private int qty = 0;
    private double avgBuyPrice = 0;

    private volatile String SYMBOL = "000660";

    public int getQty() { return qty; }
    public double getAvgBuyPrice() { return avgBuyPrice; }
    public String getSymbol() { return SYMBOL; }

    // ============================================================
    // 🔵 종목 변경
    // ============================================================
    public void updateSymbol(String newSymbol){
        if(newSymbol == null || newSymbol.isBlank()){
            System.out.println("종목코드가 비어있습니다.");
            return;
        }

        this.SYMBOL = newSymbol.trim();
        calculator.resetDaily();

        try {
            limiter.waitForNext();
            JSONObject balanceJson = balanceClient.getBalance(token, appKey, appSecret, accountNo);
            loadCurrentHolding(balanceJson);
        } catch (Exception e) {
            e.printStackTrace();
            this.qty = 0;
            this.avgBuyPrice = 0;
        }

        System.out.println("종목 변경됨: " + SYMBOL);
    }


    // ============================================================
    // 🔵 초기화 (매일 1회)
    // ============================================================
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
        profitTracker.trackBalance(balanceJson, false);

        System.out.println("🌅 새날 시작 — 전일 종가: " + prevClose);
    }


    // ============================================================
    // 🔥 KOSPI는 10초마다 업데이트 (안정화)
    // ============================================================
    private double kospiCache = 0;
    private long lastKospiTime = 0;

    private double getKospiSafe() throws Exception {
        long now = System.currentTimeMillis();

        if (now - lastKospiTime < 10_000) {
            return kospiCache; // 10초 이내는 캐시 사용
        }

        limiter.waitForNext();
        kospiCache = marketApi.getKospiIndex(token, appKey, appSecret);
        lastKospiTime = now;

        return kospiCache;
    }


    // ============================================================
    //  🚀 하이브리드 자동매매 (2초마다)
    // ============================================================
    @Scheduled(cron = "*/1 * 9-15 * * MON-FRI")
    public void autoTrade() {
        try {

            if (token == null) {
                token = authClient.getAccessToken(appKey, appSecret);
            }

            // --------------------------------------------------------
            // ⭐ 1) 통합 시세
            // --------------------------------------------------------
            limiter.waitForNext();
            JSONObject info = priceClient.getUnifiedPrice(token, appKey, appSecret, SYMBOL, "virtual");

            if (info.isEmpty()) {
                System.out.println("⚠️ 통합 시세 없음 — skip");
                return;
            }

            double newPrice = info.optDouble("price", 0);
            int volume = info.optInt("volume", 0);

            if (!Double.isFinite(newPrice) || newPrice <= 0) return;


            // --------------------------------------------------------
            // ⭐ 2) 체결강도
            // --------------------------------------------------------
            limiter.waitForNext();
            double tickStrength = kisTickApi.getTickStrength(token, appKey, appSecret, SYMBOL);


            // --------------------------------------------------------
            // ⭐ 3) 호가 (orderbook)
            // --------------------------------------------------------
            limiter.waitForNext();
            JSONObject orderBook = kisOrderBookApi.getOrderBook(token, appKey, appSecret, SYMBOL);

            int askQty = 0;
            int bidQty = 0;

            if (orderBook != null) {
                askQty = orderBook.optInt("askp_rsqn1", 0);  // 매도 잔량 1호가
                bidQty = orderBook.optInt("bidp_rsqn1", 0);  // 매수 잔량 1호가
            } else {
                System.out.println("⚠ 호가 데이터 없음 → 0 처리");
            }


            // --------------------------------------------------------
            // ⭐ 4) KOSPI (10초 캐시)
            // --------------------------------------------------------
            double kospi = getKospiSafe();
            calculator.updateMarket(kospi);


            // --------------------------------------------------------
            // 🔵 계산기 입력
            // --------------------------------------------------------
            calculator.addPrice(newPrice);
            calculator.updateVolume(volume);
            calculator.updateTickStrength(tickStrength);
            calculator.updateOrderBook(bidQty, askQty);


            // --------------------------------------------------------
            // 🔵 지표 계산
            // --------------------------------------------------------
            double shortMA = calculator.getShortMA();
            double longMA = calculator.getLongMA();
            double slope = calculator.getSlope();
            double accel = calculator.getAccel();
            double instantMom = calculator.getInstantMomentum();
            double dailyMomentum = calculator.getDailyMomentum(newPrice);
            double atr = calculator.getATR();


            // --------------------------------------------------------
            // 🔵 매수/매도 로직 (그대로 유지)
            // --------------------------------------------------------
            // 1차 매수
            if (qty == 0 && calculator.shouldBuyPro(newPrice)) {

                tradeClient.buyStock(token, appKey, appSecret, accountNo, SYMBOL, 1, (int)newPrice);
                reloadBalance();
                profitTracker.logTrade("🟢 매수 — " + SYMBOL);
                return;
            }

            // 2차 매수
            if (qty == 1 && newPrice > avgBuyPrice * 1.002) {

                tradeClient.buyStock(token, appKey, appSecret, accountNo, SYMBOL, 1, (int)newPrice);
                reloadBalance();
                profitTracker.logTrade("🟢 2차 매수 — " + SYMBOL);
                return;
            }

            // 3차 매수
            if (qty == 2 && shortMA > longMA && slope > 0) {

                tradeClient.buyStock(token, appKey, appSecret, accountNo, SYMBOL, 1, (int)newPrice);
                reloadBalance();
                profitTracker.logTrade("🟢 3차 매수 — " + SYMBOL);
                return;
            }

            // 매도
            if (qty > 0 && calculator.shouldSellPro(newPrice, avgBuyPrice)) {
                limiter.waitForNext();
                tradeClient.sellStock(token, appKey, appSecret, accountNo, SYMBOL, qty, 0);
                reloadBalance();
                profitTracker.logTrade("🔴 매도 — " + SYMBOL);
            }

        } catch (Exception e) {
            System.err.println("❌ autoTrade 오류 (안전복구됨): " + e.getMessage());
        }
    }


    // ============================================================
    // 🔵 잔고 업데이트
    // ============================================================
    private void reloadBalance() throws Exception {
        limiter.waitForNext(); // API 부하 완화
        JSONObject balanceJson = balanceClient.getBalance(token, appKey, appSecret, accountNo);
        loadCurrentHolding(balanceJson);
        profitTracker.trackBalance(balanceJson, true);
    }

    private void loadCurrentHolding(JSONObject balanceJson) {
        var list = balanceJson.optJSONArray("output1");
        if (list == null) return;

        for (int i = 0; i < list.length(); i++) {
            var item = list.getJSONObject(i);

            if (item.optString("pdno", "").trim().equals(SYMBOL.trim())) {
                this.qty = item.optInt("hldg_qty", 0);
                this.avgBuyPrice = item.optDouble("pchs_avg_pric", 0);
                return;
            }
        }

        this.qty = 0;
        this.avgBuyPrice = 0;
    }
}

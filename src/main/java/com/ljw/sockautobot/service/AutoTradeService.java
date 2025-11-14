package com.ljw.sockautobot.service;

import com.ljw.sockautobot.api.*;
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

    private static final String SYMBOL = "298380";

    public int getQty() { return qty; }
    public double getAvgBuyPrice() { return avgBuyPrice; }



    // ============================================================
    //  🌅 매일 아침 초기화
    // ============================================================
    @Scheduled(cron = "0 0 9 * * MON-FRI")
    public void initDaily() throws Exception {

        token = authClient.getAccessToken(appKey, appSecret);

        limiter.waitForNext();
        double prevClose = priceClient.getPrevClose(token, appKey, appSecret, SYMBOL, "virtual");

        calculator.setPrevClose(prevClose);
        calculator.resetDaily();

        qty = 0;
        avgBuyPrice = 0;

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

            qty = 1;
            avgBuyPrice = price;

            System.out.println("🟢 [1차 매수] 조건 충족");
            return;
        }


        // ============================================================
        //  🟢 2차 매수 — 전고점 돌파 시도
        // ============================================================
        if (qty == 1 && price > avgBuyPrice * 1.002) {

            limiter.waitForNext();
            tradeClient.buyStock(token, appKey, appSecret, accountNo, SYMBOL, 1, (int) price);

            avgBuyPrice = (avgBuyPrice + price) / 2;
            qty = 2;

            System.out.println("🟢 [2차 매수]");
            return;
        }


        // ============================================================
        //  🟢 3차 매수 — 강한 추세 유지
        // ============================================================
        if (qty == 2 && shortMA > longMA && slope > 0) {

            limiter.waitForNext();
            tradeClient.buyStock(token, appKey, appSecret, accountNo, SYMBOL, 1, (int) price);

            avgBuyPrice = (avgBuyPrice * 2 + price) / 3;
            qty = 3;

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

            qty = 0;
            avgBuyPrice = 0;
            profitTracker.recordProfit(price, avgBuyPrice, qty);

        }
    }
}

package com.ljw.sockautobot.service;

import com.ljw.sockautobot.api.*;
import lombok.RequiredArgsConstructor;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class AutoTradeService {

    private final KisAuthClientApi authClient;
    private final KisPriceClientApi priceClient;
    private final KisTradeClientApi tradeClient;
    private final KisBalanceClientApi balanceClient;
    private final TradeCalculator calculator;   // ✅ 계산 전담
    private final ProfitTracker profitTracker;  // ✅ 수익/잔고 추적 전담

    @Value("${kis.app-key}") private String appKey;
    @Value("${kis.app-secret}") private String appSecret;
    @Value("${kis.account-no}") private String accountNo;
    @Value("${kis.mode}") private String kisMode;

    private final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm:ss");
    private final KisRateLimiter limiter = new KisRateLimiter();

    private String token;
    private double avgBuyPrice = 0;
    private int qty = 0;
    private long lastOrderTime = 0L;

    private static final String SYMBOL = "486990"; // 노타

    @Scheduled(cron = "*/5 * 9-18 * * MON-FRI")
    public void autoTrade() throws JSONException {
        try {
            if (token == null) token = authClient.getAccessToken(appKey, appSecret);
            limiter.setMode(kisMode);

            // ✅ 잔고 조회
            limiter.waitForNext();
            JSONObject balance = balanceClient.getBalance(token, appKey, appSecret, accountNo);
            if (balance == null) {
                System.out.println("⚠️ 잔고 조회 실패 - 응답 null");
                return;
            }

            // ✅ 잔고 추적
            profitTracker.trackBalance(balance);

            // 보유 수량, 평균단가 조회
            var holdings = balance.optJSONArray("output1");
            if (holdings != null) {
                for (int i = 0; i < holdings.length(); i++) {
                    var stock = holdings.getJSONObject(i);
                    if (SYMBOL.equals(stock.optString("pdno"))) {
                        qty = stock.optInt("hldg_qty", 0);
                        avgBuyPrice = stock.optDouble("pchs_avg_pric", 0);
                    }
                }
            }

            // 📊 현재가 조회
            limiter.waitForNext();
            double price = priceClient.getStockPrice(token, appKey, appSecret, SYMBOL);
            calculator.addPrice(price);

            // 계산
            double slope = calculator.calculateSlope();
            double accel = calculator.calculateAcceleration();
            double momentum = calculator.calculateMomentum();
            double netProfit = calculator.calculateNetProfit(price, avgBuyPrice);

            String time = LocalDateTime.now().format(fmt);
            System.out.println("\n=================== 📊 " + SYMBOL + " — " + time + " ===================");
            System.out.printf("현재가: %,.0f원 | 보유수량: %d | 평균단가: %,.0f원\n", price, qty, avgBuyPrice);
            System.out.printf("📈 slope=%.5f / accel=%.5f / momentum=%.3f%% / 수익률=%.3f%%\n",
                    slope, accel, momentum, netProfit);

            // 최근 거래 후 5초 이내는 스킵
            if (System.currentTimeMillis() - lastOrderTime < 5000) {
                System.out.println("⏳ 최근 거래 이후 5초 미만 — 대기 중...");
                return;
            }

            // 매수 판단
            if (qty == 0 && calculator.shouldBuy(slope, accel, momentum)) {
                limiter.waitForNext();
                tradeClient.buyStock(token, appKey, appSecret, accountNo, SYMBOL, 1, (int) price);
                avgBuyPrice = price;
                qty = 1;
                lastOrderTime = System.currentTimeMillis();

                System.out.println("🟢 [AI 매수]");
                System.out.printf("   └─ 매수가: %,.0f원\n", price);
                return;
            }

            // 매도 판단
            if (qty > 0 && calculator.shouldSell(netProfit, slope, accel, momentum)) {
                limiter.waitForNext();
                tradeClient.sellStock(token, appKey, appSecret, accountNo, SYMBOL, qty, 0);
                lastOrderTime = System.currentTimeMillis();

                // ✅ 수익 계산 및 누적 기록
                profitTracker.recordProfit(price, avgBuyPrice, qty);

                avgBuyPrice = 0;
                qty = 0;
                System.out.println("🔴 [AI 매도 완료]");
            }

        } catch (Exception e) {
            System.out.println("❌ [AutoTrade 오류] " + e.getMessage());
        }
    }
}

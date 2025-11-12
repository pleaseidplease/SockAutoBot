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

    private static final String SYMBOL = "460940"; // KODEX 200선물인버스2X

    // ⚡ 공격형: 2초마다 판단
    @Scheduled(cron = "*/2 * 9-18 * * MON-FRI")
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
            profitTracker.trackBalance(balance, false);

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

            // 최근 거래 후 3초 이내는 스킵 (속도 조절)
            if (System.currentTimeMillis() - lastOrderTime < 3000) {
                System.out.println("⏳ 최근 거래 이후 3초 미만 — 대기 중...");
                return;
            }

            // 🚀 급상승 감지 매수 (단타 진입)
            boolean isRapidBuy = slope > 0.005 && accel > 0.02 && momentum > 0.15;

            // 📈 추가 매수 (상승 유지)
            boolean isAddBuy = qty > 0 && slope > 0.003 && accel > 0;

            // 💰 빠른 익절 / ⚠️ 급락 손절
            boolean isQuickSell = netProfit > 0.6; // +0.6% 이상 수익
            boolean isDropSell = slope < -0.004 || accel < -0.02;

            // 🔥 상승세 유지 중일 때 (보유 중일 때만)
            if (qty > 0 && accel > 0.01 && slope > 0.003) {
                System.out.println("🔥 상승세 유지 중 — 보유 지속");
                return;
            }

            // 🟢 첫 매수 진입
            if (qty == 0 && isRapidBuy) {
                limiter.waitForNext();
                tradeClient.buyStock(token, appKey, appSecret, accountNo, SYMBOL, 1, (int) price);
                avgBuyPrice = price;
                qty = 1;
                lastOrderTime = System.currentTimeMillis();

                System.out.println("🚀 [AI 급상승 진입]");
                System.out.printf("   └─ 매수가: %,.0f원\n", price);
                return;
            }

            // 📈 추가 매수
            if (isAddBuy && qty < 3 && System.currentTimeMillis() - lastOrderTime > 7000) {
                limiter.waitForNext();
                tradeClient.buyStock(token, appKey, appSecret, accountNo, SYMBOL, 1, (int) price);
                avgBuyPrice = (avgBuyPrice * qty + price) / (qty + 1);
                qty += 1;
                lastOrderTime = System.currentTimeMillis();

                System.out.println("📈 [AI 추가 매수] 상승세 지속 확인");
                return;
            }

            // 💰 빠른 익절 또는 ⚠️ 급락 손절
            if (qty > 0 && (isQuickSell || isDropSell)) {
                limiter.waitForNext();
                tradeClient.sellStock(token, appKey, appSecret, accountNo, SYMBOL, qty, 0);
                lastOrderTime = System.currentTimeMillis();

                profitTracker.recordProfit(price, avgBuyPrice, qty);
                avgBuyPrice = 0;
                qty = 0;

                if (isQuickSell) {
                    System.out.println("💰 [AI 단타 익절] 짧은 수익 실현");
                } else {
                    System.out.println("⚠️ [AI 급락 손절] 빠른 회피");
                }

                limiter.waitForNext();
                JSONObject updatedBalance = balanceClient.getBalance(token, appKey, appSecret, accountNo);
                profitTracker.trackBalance(updatedBalance, true);
                return;
            }

        } catch (Exception e) {
            System.out.println("❌ [AutoTrade 오류] " + e.getMessage());
        }
    }
}

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
import java.util.LinkedList;

/**
 * ✅ 자동매매 서비스 (안정 버전)
 * - 초당 요청 제한 대응
 * - 시장가 매도 (익절 0.7%, 손절 -0.4%)
 * - 거래 후 잔고 자동동기화
 */
@Service
@RequiredArgsConstructor
public class AutoTradeService {

    private final KisAuthClientApi authClient;
    private final KisPriceClientApi priceClient;
    private final KisTradeClientApi tradeClient;
    private final KisBalanceClientApi balanceClient;

    @Value("${kis.app-key}") private String appKey;
    @Value("${kis.app-secret}") private String appSecret;
    @Value("${kis.account-no}") private String accountNo;
    @Value("${kis.mode}") private String kisMode; // ✅ virtual / real

    private final LinkedList<Double> priceHistory = new LinkedList<>();
    private final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm:ss");
    private final KisRateLimiter limiter = new KisRateLimiter(); // ✅ 요청 제어 유틸

    private String token;
    private String stockName;
    private double avgBuyPrice = 0;
    private int qty = 0;
    private long lastOrderTime = 0L;

    // 거래 종목
    private static final String SYMBOL = "000660"; // SK하이닉스

    // 수수료 / 세금
    private static final double COMMISSION_RATE = 0.0015;
    private static final double TAX_RATE = 0.0015;

    // ================================================
    // ✅ 1. 종목명 초기화
    // ================================================
    private void initStockName() {
        if (stockName != null) return;
        try {
            limiter.waitForNext();
            JSONObject info = priceClient.getStockInfo(token, appKey, appSecret, SYMBOL);
            stockName = info.optString("prdt_name", getFallbackName(SYMBOL));
        } catch (Exception e) {
            System.out.println("❌ [KIS API] 종목 정보 조회 실패: " + e.getMessage());
            stockName = getFallbackName(SYMBOL);
        }
    }

    private String getFallbackName(String code) {
        return switch (code) {
            case "000660" -> "SK하이닉스";
            case "005930" -> "삼성전자";
            case "035420" -> "NAVER";
            case "035720" -> "카카오";
            case "051910" -> "LG화학";
            case "373220" -> "LG에너지솔루션";
            default -> "알 수 없는 종목(" + code + ")";
        };
    }

    // ================================================
    // ✅ 2. 메인 자동매매 루프
    // ================================================
    @Scheduled(cron = "*/5 * 9-18 * * MON-FRI") // 장중 15초마다 실행
    public void autoTrade() throws JSONException {
        try {
            if (token == null) token = authClient.getAccessToken(appKey, appSecret);
            limiter.setMode(kisMode);
            initStockName();

            // 🧾 잔고 조회
            limiter.waitForNext();
            JSONObject balance = balanceClient.getBalance(token, appKey, appSecret, accountNo);
            if (balance == null) {
                System.out.println("⚠️ 잔고 조회 실패 - 응답 null");
                return;
            }

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
            priceHistory.add(price);
            if (priceHistory.size() > 20) priceHistory.removeFirst();

            if (priceHistory.size() < 6) {
                System.out.printf("📉 [%s] 시세 누적 중... (%d/6)\n", stockName, priceHistory.size());
                return;
            }

            // 계산
            double slope = calculateSlope(priceHistory);
            double accel = calculateAcceleration(priceHistory);
            double momentum = (price - priceHistory.get(priceHistory.size() - 2)) / price * 100;
            double grossProfit = avgBuyPrice > 0 ? (price - avgBuyPrice) / avgBuyPrice * 100 : 0;
            double netProfit = grossProfit - (COMMISSION_RATE * 200 + TAX_RATE * 100);

            String time = LocalDateTime.now().format(fmt);
            System.out.println("\n=================== 📊 " + stockName + " (" + SYMBOL + ") — " + time + " ===================");
            System.out.printf("현재가: %,.0f원 | 보유수량: %d | 평균단가: %,.0f원\n", price, qty, avgBuyPrice);
            System.out.printf("📈 slope=%.5f / accel=%.5f / momentum=%.3f%%\n", slope, accel, momentum);

            // 최근 거래 30초 이내면 스킵
            if (System.currentTimeMillis() - lastOrderTime < 30000) {
                System.out.println("⏳ 최근 거래 이후 30초 미만 — 대기 중...");
                return;
            }

            // 기준값
            final double TAKE_PROFIT_TARGET = 0.7; // +0.7% 이상 익절
            final double STOP_LOSS_LIMIT = -0.4;   // -0.4% 이하 손절

            boolean strongUp = slope > 0.001 && accel > 0 && momentum > 0.04;
            boolean rebound = slope < 0 && accel > 0 && momentum > 0.03;
            boolean steadyRise = slope > 0 && accel >= 0 && momentum > 0;

            // =====================================
            // 🟢 매수 로직
            // =====================================
            if (qty == 0 && (rebound || strongUp || steadyRise)) {
                limiter.waitForNext();
                tradeClient.buyStock(token, appKey, appSecret, accountNo, SYMBOL, 1, (int) price);
                lastOrderTime = System.currentTimeMillis();
                avgBuyPrice = price;
                qty = 1;

                System.out.println("🟢 [AI 매수]");
                System.out.println("   ├─ 이유: " + (rebound ? "📈 반등 전환" : strongUp ? "🚀 강한 상승 추세" : "🔹 완만한 상승세"));
                System.out.printf("   └─ 매수가: %,.0f원\n", price);
                return;
            }

            // =====================================
            // 🔴 매도 로직
            // =====================================
            if (qty > 0) {
                boolean takeProfit = netProfit >= TAKE_PROFIT_TARGET;
                boolean stopLoss = netProfit <= STOP_LOSS_LIMIT;
                boolean trendReversal = slope < 0 && accel < 0 && momentum < -0.03;

                if (takeProfit || stopLoss || trendReversal) {
                    limiter.waitForNext();

                    double profitPerStock = (price - avgBuyPrice) * (1 - COMMISSION_RATE - TAX_RATE);
                    double totalProfit = profitPerStock * qty;

                    // ✅ 시장가 매도
                    tradeClient.sellStock(token, appKey, appSecret, accountNo, SYMBOL, qty, 0);
                    lastOrderTime = System.currentTimeMillis();

                    String emoji = netProfit > 0 ? "💰익절" : "💔손절";
                    System.out.println("🔴 [AI 매도]");
                    System.out.println("   ├─ 결과: " + emoji);
                    System.out.printf("   ├─ 수익률(순): %.2f%%\n", netProfit);
                    System.out.printf("   ├─ 매도가(시장가): %,.0f원\n", price);
                    System.out.printf("   ├─ 총수익(세후): %,.0f원\n", totalProfit);
                    System.out.printf("   └─ 주당수익(세후): %,.0f원\n", profitPerStock);

                    qty = 0;
                    avgBuyPrice = 0;

                    // ✅ 5초 대기 후 잔고 재조회
                    try {
                        Thread.sleep(5000);
                        limiter.waitForNext();
                        JSONObject updatedBalance = balanceClient.getBalance(token, appKey, appSecret, accountNo);
                        holdings = updatedBalance.optJSONArray("output1");
                        if (holdings != null) {
                            for (int i = 0; i < holdings.length(); i++) {
                                var stock = holdings.getJSONObject(i);
                                if (SYMBOL.equals(stock.optString("pdno"))) {
                                    qty = stock.optInt("hldg_qty", 0);
                                    avgBuyPrice = stock.optDouble("pchs_avg_pric", 0);
                                }
                            }
                        }
                        System.out.printf("📊 [동기화 완료] 보유수량: %d, 평균단가: %,.0f원\n", qty, avgBuyPrice);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                } else {
                    System.out.printf("⏳ [보유 유지] 수익률(순): %.3f%% | 추세 관망 중...\n", netProfit);
                }
            }
        } catch (Exception e) {
            System.out.println("❌ [AutoTrade 오류] " + e.getMessage());
        }
    }

    // ================================================
    // 📉 기울기 계산
    // ================================================
    private double calculateSlope(LinkedList<Double> prices) {
        int n = prices.size();
        double sumX = 0, sumY = 0, sumXY = 0, sumXX = 0;
        for (int i = 0; i < n; i++) {
            sumX += i;
            sumY += prices.get(i);
            sumXY += i * prices.get(i);
            sumXX += i * i;
        }
        return (n * sumXY - sumX * sumY) / (n * sumXX - sumX * sumX);
    }

    // ================================================
    // 📈 가속도 계산
    // ================================================
    private double calculateAcceleration(LinkedList<Double> prices) {
        if (prices.size() < 4) return 0;
        double slope1 = prices.get(prices.size() - 1) - prices.get(prices.size() - 2);
        double slope2 = prices.get(prices.size() - 2) - prices.get(prices.size() - 3);
        return slope1 - slope2;
    }
}

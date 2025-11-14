package com.ljw.sockautobot.service;

import org.springframework.stereotype.Component;
import java.util.LinkedList;

@Component
public class TradeCalculator {

    public enum Trend {
        BULL, BEAR, SIDE
    }

    public Trend getTrend() {
        double shortMA = getShortMA();
        double longMA = getLongMA();

        if (shortMA > longMA * 1.0003) return Trend.BULL;
        if (shortMA < longMA * 0.9997) return Trend.BEAR;
        return Trend.SIDE; // 박스권
    }


    private final LinkedList<Double> priceHistory = new LinkedList<>();

    // 수수료 & 세금
    private static final double COMMISSION_RATE = 0.0015; // 매수/매도 각각 0.15%
    private static final double TAX_RATE = 0.0015;         // 매도세 0.15%

    private final LinkedList<Double> minuteAvgHistory = new LinkedList<>();
    private long lastMinute = -1;

    public void addPrice(double price) {
        priceHistory.add(price);
        if (priceHistory.size() > 5000) priceHistory.removeFirst();

        // 1분 단위 평균 업데이트
        long nowMinute = System.currentTimeMillis() / 60000;
        if (lastMinute != nowMinute) {
            double avg = priceHistory.stream()
                    .skip(Math.max(0, priceHistory.size() - 12)) // 1분(5초*12틱) 평균
                    .mapToDouble(Double::doubleValue)
                    .average().orElse(price);
            minuteAvgHistory.add(avg);
            if (minuteAvgHistory.size() > 360) minuteAvgHistory.removeFirst(); // 하루 6시간 * 60분
            lastMinute = nowMinute;
        }
    }

    // 💰 현실적인 순수익률 계산
    public double calculateNetProfit(double currentPrice, double avgPrice) {
        if (avgPrice <= 0) return 0;
        double profit = currentPrice - avgPrice;

        // 수수료 & 세금 원단위 반영
        double commission = (currentPrice + avgPrice) * COMMISSION_RATE;
        double tax = currentPrice * TAX_RATE;
        double net = profit - commission - tax;

        return (net / avgPrice) * 100; // 수익률(%)
    }

    // 📈 단순 기울기 (최근 가격 변화 추세)
    public double calculateSlope() {
        int n = priceHistory.size();
        if (n < 2) return 0;

        double sumX = 0, sumY = 0, sumXY = 0, sumXX = 0;
        for (int i = 0; i < n; i++) {
            sumX += i;
            sumY += priceHistory.get(i);
            sumXY += i * priceHistory.get(i);
            sumXX += i * i;
        }
        return (n * sumXY - sumX * sumY) / (n * sumXX - sumX * sumX);
    }

    // 📉 가속도 (상승 속도의 증가/감소)
    public double calculateAcceleration() {
        if (priceHistory.size() < 4) return 0;
        double slope1 = priceHistory.get(priceHistory.size() - 1) - priceHistory.get(priceHistory.size() - 2);
        double slope2 = priceHistory.get(priceHistory.size() - 2) - priceHistory.get(priceHistory.size() - 3);
        return slope1 - slope2;
    }

    // ⚡ 모멘텀 (이전 가격 대비 즉시 상승률)
    public double calculateMomentum() {
        if (priceHistory.size() < 2) return 0;
        double price = priceHistory.getLast();
        double prev = priceHistory.get(priceHistory.size() - 2);
        return (price - prev) / prev * 100; // ← prev 기준으로 변경 (더 직관적)
    }

    public boolean shouldBuy(double slope, double accel, double momentum) {
        Trend trend = getTrend();

        // 하락 추세는 금지
        if (trend == Trend.BEAR) return false;

        // 과열 조건 조금 완화
        if (accel > 0.03 || momentum > 0.5 || slope > 0.015) return false;

        // 상승 추세일 때
        if (trend == Trend.BULL) {
            boolean strongBuy =
                    slope > 0.002 &&   // 0.004 → 0.002 로 완화
                            accel > 0.008 &&   // 0.015 → 0.008
                            momentum > 0.05;   // 0.10 → 0.05

            boolean reversalBuy =
                    slope > 0 &&
                            accel > 0 &&
                            momentum > 0;

            return strongBuy || reversalBuy;
        }

        // 박스권일 때도 진입 허용 범위 조금 넓혀줌
        return slope > 0 && accel > 0 && momentum > 0.03;
    }


    // 🔴 매도 조건 (익절 + 손절 둘 다 초단기형)
    public boolean shouldSell(double netProfit, double slope, double accel, double momentum) {
        final double TAKE_PROFIT = 0.35;  // 익절 +0.35%
        final double STOP_LOSS = -0.25;   // 손절 -0.25%

        boolean takeProfit = netProfit >= TAKE_PROFIT;
        boolean stopLoss = netProfit <= STOP_LOSS;
        boolean reversal = slope < 0 && accel < 0 && momentum < -0.015; // 반전 감지

        return takeProfit || stopLoss || reversal;
    }

    // 📌 단기 20틱 이동평균 (shortMA)
    public double getShortMA() {
        int size = priceHistory.size();
        if (size < 20) return priceHistory.getLast();

        return priceHistory
                .subList(size - 20, size)
                .stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(priceHistory.getLast());
    }

    // 📌 중기 120틱 이동평균 (longMA)
    public double getLongMA() {
        int size = priceHistory.size();
        if (size < 120) return priceHistory.getLast();

        return priceHistory
                .subList(size - 120, size)
                .stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(priceHistory.getLast());
    }

    public String getSellReason(double netProfit, double slope, double accel, double momentum) {
        final double TAKE_PROFIT = 0.35;  // 익절 기준
        final double STOP_LOSS = -0.25;   // 손절 기준

        if (netProfit >= TAKE_PROFIT) return "익절 기준 도달";
        if (netProfit <= STOP_LOSS) return "손절 기준 도달";
        if (slope < 0 && accel < 0 && momentum < -0.01) return "상승 반전 → 하락 전환";
        if (getShortMA() < getLongMA()) return "추세 이탈 감지 (longMA 아래)";

        return "기타 조건 충족";
    }

    // 🔥 과열 상태 판단 (매수 신호 강해도 너무 과열이면 진입 금지)
    public boolean isOverheated(double slope, double accel, double momentum) {
        return accel > 0.03 || momentum > 0.5 || slope > 0.015;
    }

}

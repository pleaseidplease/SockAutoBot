package com.ljw.sockautobot.service;

import org.springframework.stereotype.Component;
import java.util.LinkedList;

@Component
public class TradeCalculatorHybrid {

    private final LinkedList<Double> priceHistory = new LinkedList<>();
    private final LinkedList<Integer> volumeHistory = new LinkedList<>();
    private final LinkedList<Double> kospiHistory = new LinkedList<>();
    private final LinkedList<Double> trHistory = new LinkedList<>();

    private double tickStrength = 100;
    private int bidSum = 0;
    private int askSum = 0;

    private double todayOpen = -1;
    private double todayHigh = -1;
    private double todayLow = Double.MAX_VALUE;
    private double prevClose = -1;

    private static final int SHORT_MA = 10;   // 현실적인 단기선 (2~3분 기준)
    private static final int LONG_MA = 30;    // 현실적인 장기선 (6~9분 기준)

    private double kospi = 0;

    public int getVolume() {
        if (volumeHistory.isEmpty()) return 0;
        return volumeHistory.getLast();
    }

    public double getTickStrength() {
        return tickStrength;
    }

    public int getBidSum() {
        return bidSum;
    }

    public int getAskSum() {
        return askSum;
    }

    public double getKospi() {
        return kospi;
    }




    // ============================================================
    // 초기화 (프로그램 시작 기준)
    // ============================================================
    public void setPrevClose(double pc) {
        this.prevClose = pc;
    }

    public void resetDaily() {
        todayOpen = -1;
        todayHigh = -1;
        todayLow = Double.MAX_VALUE;

        priceHistory.clear();
        volumeHistory.clear();
        kospiHistory.clear();
        trHistory.clear();
    }


    // ============================================================
    // 가격 업데이트
    // ============================================================
    public void addPrice(double price) {

        if (todayOpen < 0) todayOpen = price;

        todayHigh = Math.max(todayHigh, price);
        todayLow = Math.min(todayLow, price);

        priceHistory.add(price);
        if (priceHistory.size() > 3000) priceHistory.removeFirst();

        updateATR(price);
    }


    // ============================================================
    // ATR (정식 TR 기반)
    // ============================================================
    private void updateATR(double price) {
        if (todayHigh < 0 || todayLow == Double.MAX_VALUE) return;

        double prev = (priceHistory.size() > 1)
                ? priceHistory.get(priceHistory.size() - 2)
                : todayOpen;

        double tr = Math.max(
                todayHigh - todayLow,
                Math.max(
                        Math.abs(todayHigh - prev),
                        Math.abs(todayLow - prev)
                )
        );

        trHistory.add(tr);
        if (trHistory.size() > 14) trHistory.removeFirst();
    }

    public double getATR() {
        if (trHistory.isEmpty()) return 0;
        return trHistory.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }


    // ============================================================
    // 이동평균
    // ============================================================
    private double calcMA(int len) {
        if (priceHistory.size() < len) return getLastPrice();

        return priceHistory.subList(priceHistory.size() - len, priceHistory.size())
                .stream().mapToDouble(Double::doubleValue)
                .average().orElse(0);
    }

    public double getShortMA() { return calcMA(SHORT_MA); }
    public double getLongMA() { return calcMA(LONG_MA); }


    // ============================================================
    // 모멘텀 지표
    // ============================================================
    public double getLastPrice() {
        return priceHistory.isEmpty() ? 0 : priceHistory.getLast();
    }

    public double getDailyMomentum(double price) {
        if (prevClose <= 0) return 0;
        return (price - prevClose) / prevClose * 100;
    }


    public double getSlope() {
        int n = priceHistory.size();
        if (n < 2) return 0;

        double y1 = priceHistory.get(n - 1);
        double y2 = priceHistory.get(n - 2);

        if (y2 == 0) return 0;
        return (y1 - y2) / y2;
    }

    public double getAccel() {
        int n = priceHistory.size();
        if (n < 3) return 0;

        double s1 = priceHistory.get(n - 1) - priceHistory.get(n - 2);
        double s2 = priceHistory.get(n - 2) - priceHistory.get(n - 3);

        return s1 - s2;
    }

    public double getInstantMomentum() {
        int n = priceHistory.size();
        if (n < 2) return 0;

        double cur = priceHistory.get(n - 1);
        double prev = priceHistory.get(n - 2);

        return (cur - prev) / prev * 100;
    }


    // ============================================================
    // 거래량 지표
    // ============================================================
    public void updateVolume(int v) {
        volumeHistory.add(v);
        if (volumeHistory.size() > 1500) volumeHistory.removeFirst();
    }

    public boolean isVolumeSpike() {
        if (volumeHistory.size() < 30) return false;

        double avg = volumeHistory.subList(volumeHistory.size() - 30, volumeHistory.size())
                .stream().mapToInt(Integer::intValue).average().orElse(0);

        return volumeHistory.getLast() > avg * 1.8;
    }


    // ============================================================
    // 체결강도
    // ============================================================
    public void updateTickStrength(double t) {
        this.tickStrength = t;
    }

    public boolean isStrongBuyPressure() {
        return tickStrength > 110;
    }


    // ============================================================
    // 호가잔량
    // ============================================================
    public void updateOrderBook(int bid1Qty, int ask1Qty) {
        bidSum = Math.max(bid1Qty, 0);
        askSum = Math.max(ask1Qty, 0);
    }

    public boolean isOrderBookBullish() {
        if (askSum <= 0) return false;
        return bidSum > askSum * 1.1;
    }


    // ============================================================
    // 시장지표(KOSPI)
    // ============================================================
    public void updateMarket(double k) {
        this.kospi = k;
        kospiHistory.add(k);
        if (kospiHistory.size() > 500) kospiHistory.removeFirst();
    }

    public boolean isMarketUp() {
        if (kospiHistory.size() < 20) return true;
        double avg = kospiHistory.subList(kospiHistory.size() - 20, kospiHistory.size())
                .stream().mapToDouble(Double::doubleValue).average().orElse(kospi);
        return kospi >= avg * 0.999;
    }


    // ============================================================
    // 매수/매도 조건 (현실화)
    // ============================================================
    public boolean shouldBuyPro(double price) {

        if (!isMarketUp()) return false;
        if (!isVolumeSpike()) return false;
        if (!isStrongBuyPressure()) return false;
        if (!isOrderBookBullish()) return false;

        if (getShortMA() + 1 < getLongMA()) return false;
        if (getSlope() <= 0) return false;
        if (getAccel() <= 0) return false;
        if (getInstantMomentum() < 0.02) return false;

        return true;
    }

    public boolean shouldSellPro(double price, double avg) {
        double profit = (price - avg) / avg * 100;

        if (profit >= 0.5) return true;
        if (profit <= -0.3) return true;

        if (tickStrength < 85) return true;

        if (getShortMA() < getLongMA()) return true;

        if (Math.abs(price - avg) > getATR() * 1.4) return true;

        return false;
    }

    // ==================== 실질 수익률 계산 함수 ====================
    public double calculateNetProfit(double currentPrice, double avgPrice) {
        if (avgPrice <= 0 || currentPrice <= 0) return 0;

        // 수익(원)
        double profit = currentPrice - avgPrice;

        // 실 매매 비용(수수료 + 세금) — 보수적 적용
        double commission = (currentPrice + avgPrice) * 0.0015;  // 왕복 수수료
        double tax = currentPrice * 0.0015;                      // 매도세금(0.15%)

        double net = profit - commission - tax;

        // 최종 수익률(%)
        return (net / avgPrice) * 100;
    }

}


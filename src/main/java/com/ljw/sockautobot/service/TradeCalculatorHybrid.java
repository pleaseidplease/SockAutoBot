package com.ljw.sockautobot.service;

import org.springframework.stereotype.Component;
import java.util.LinkedList;

@Component
public class TradeCalculatorHybrid {

    private final LinkedList<Double> priceHistory = new LinkedList<>();

    private double todayOpen = -1;
    private double todayHigh = -1;
    private double todayLow = Double.MAX_VALUE;
    private double prevClose = -1;

    private static final int SHORT_MA = 20;
    private static final int LONG_MA = 60;

    private final LinkedList<Double> trHistory = new LinkedList<>();


    // ============================================================
    // Getter 추가 (컨트롤러용)
    // ============================================================
    public double getLastPrice() {
        if (priceHistory.isEmpty()) return 0;
        return priceHistory.getLast();
    }

    public double getTodayOpen() { return todayOpen; }
    public double getTodayHigh() { return todayHigh; }
    public double getTodayLow() { return todayLow; }
    public double getPrevClose() { return prevClose; }


    // ============================================================
    // 초기화
    // ============================================================
    public void setPrevClose(double pc) {
        this.prevClose = pc;
    }

    public void resetDaily() {
        todayOpen = -1;
        todayHigh = -1;
        todayLow = Double.MAX_VALUE;
        priceHistory.clear();
        trHistory.clear();
    }


    // ============================================================
    // 가격 입력
    // ============================================================
    public void addPrice(double price) {

        if (todayOpen < 0) todayOpen = price;

        priceHistory.add(price);
        if (priceHistory.size() > 5000) priceHistory.removeFirst();

        todayHigh = Math.max(todayHigh, price);
        todayLow = Math.min(todayLow, price);

        updateATR(price);
    }


    // ============================================================
    // ATR
    // ============================================================
    private void updateATR(double price) {
        if (todayHigh < 0 || todayLow == Double.MAX_VALUE) return;

        double tr = Math.max(
                todayHigh - todayLow,
                Math.max(Math.abs(todayHigh - price), Math.abs(todayLow - price))
        );

        trHistory.add(tr);

        if (trHistory.size() > 14)
            trHistory.removeFirst();
    }

    public double getATR() {
        if (trHistory.isEmpty()) return 0;
        return trHistory.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }


    // ============================================================
    // 이동평균
    // ============================================================
    private double calcMA(int len) {
        if (priceHistory.isEmpty()) return 0;
        if (priceHistory.size() < len) return getLastPrice();

        return priceHistory.subList(priceHistory.size() - len, priceHistory.size())
                .stream().mapToDouble(Double::doubleValue)
                .average().orElse(getLastPrice());
    }

    public double getShortMA() { return calcMA(SHORT_MA); }
    public double getLongMA() { return calcMA(LONG_MA); }


    // ============================================================
    // 하루 단위 모멘텀
    // ============================================================
    public double getDailyMomentum(double price) {
        if (prevClose <= 0) return 0;
        return (price - prevClose) / prevClose * 100;
    }


    // ============================================================
    // 순간 추세 지표
    // ============================================================
    public double getSlope() {
        int n = priceHistory.size();
        if (n < 2) return 0;

        double y1 = priceHistory.get(n - 1);
        double y2 = priceHistory.get(n - 2);

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
    // 하이브리드 매수 조건
    // ============================================================
    public boolean shouldBuyHybrid(double price) {

        boolean dailyUp =
                price > todayOpen &&
                        price > prevClose &&
                        getShortMA() > getLongMA();

        if (!dailyUp) return false;

        double slope = getSlope();
        double accel = getAccel();
        double mom = getInstantMomentum();

        boolean instantGood =
                slope > 0 &&
                        accel > 0 &&
                        mom > 0.02;

        return instantGood;
    }


    // ============================================================
    // 하이브리드 매도 조건
    // ============================================================
    public boolean shouldSellHybrid(double price, double avgPrice) {

        double profit = (price - avgPrice) / avgPrice * 100;

        if (price < todayOpen) return true;
        if (price < todayLow) return true;
        if (getShortMA() < getLongMA()) return true;

        if (Math.abs(price - avgPrice) > getATR() * 1.5) return true;

        if (profit >= 0.5) return true;
        if (profit <= -0.4) return true;

        return false;
    }

    public double calculateNetProfit(double currentPrice, double avgPrice) {
        if (avgPrice <= 0) return 0;

        double profit = currentPrice - avgPrice;

        double commission = (currentPrice + avgPrice) * 0.0015;
        double tax = currentPrice * 0.0015;

        double net = profit - commission - tax;

        return (net / avgPrice) * 100;  // %
    }

}

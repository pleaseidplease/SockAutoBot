package com.ljw.sockautobot.service;

import org.springframework.stereotype.Component;
import java.util.LinkedList;

@Component
public class TradeCalculator {

    private final LinkedList<Double> priceHistory = new LinkedList<>();
    private static final double COMMISSION_RATE = 0.0015;
    private static final double TAX_RATE = 0.0015;

    public void addPrice(double price) {
        priceHistory.add(price);
        if (priceHistory.size() > 20) priceHistory.removeFirst();
    }

    public double calculateNetProfit(double currentPrice, double avgPrice) {
        if (avgPrice <= 0) return 0;
        double gross = (currentPrice - avgPrice) / avgPrice * 100;
        return gross - ((COMMISSION_RATE * 2 + TAX_RATE) * 100);
    }

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

    public double calculateAcceleration() {
        if (priceHistory.size() < 4) return 0;
        double slope1 = priceHistory.get(priceHistory.size() - 1) - priceHistory.get(priceHistory.size() - 2);
        double slope2 = priceHistory.get(priceHistory.size() - 2) - priceHistory.get(priceHistory.size() - 3);
        return slope1 - slope2;
    }

    public double calculateMomentum() {
        if (priceHistory.size() < 2) return 0;
        double price = priceHistory.getLast();
        double prev = priceHistory.get(priceHistory.size() - 2);
        return (price - prev) / price * 100;
    }

    public boolean shouldBuy(double slope, double accel, double momentum) {
        return (slope > 0 && accel > 0 && momentum > 0.03);
    }

    public boolean shouldSell(double netProfit, double slope, double accel, double momentum) {
        return netProfit >= 0.7 || netProfit <= -0.4 || (slope < 0 && accel < 0 && momentum < -0.03);
    }
}

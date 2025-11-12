package com.ljw.sockautobot.service;

import org.json.JSONObject;
import org.springframework.stereotype.Component;

@Component
public class ProfitTracker {

    private double baseBalance = 0;     // 프로그램 시작 시점 잔고
    private double lastBalance = 0;     // 최근 잔고
    private double totalProfit = 0;     // 프로그램 돌리는 동안 누적 수익
    private boolean initialized = false;

    // 프로그램 실행 시 잔고 세팅 및 변화 추적
    public void trackBalance(JSONObject balanceResponse) {
        if (balanceResponse == null) return;

        var output2 = balanceResponse.optJSONArray("output2"); // 한국투자 API 응답 구조
        if (output2 == null || output2.length() == 0) return;

        double nowBalance = output2.getJSONObject(0).optDouble("tot_evlu_amt", 0);

        if (!initialized) {
            baseBalance = nowBalance;
            lastBalance = nowBalance;
            initialized = true;
            System.out.printf("💵 프로그램 시작 시점 잔고: %,.0f원\n", baseBalance);
            return;
        }

        double diff = nowBalance - lastBalance;
        if (diff != 0) {
            String sign = diff > 0 ? "▲" : "▼";
            System.out.printf("💰 현재 잔고: %,.0f원 (%s%,.0f원 변화)\n", nowBalance, sign, Math.abs(diff));
        }
        lastBalance = nowBalance;
    }

    // 거래 시 수익 누적
    public void recordProfit(double sellPrice, double buyPrice, int qty) {
        double commission = (sellPrice + buyPrice) * 0.0015 * qty;
        double tax = sellPrice * 0.0015 * qty;
        double netProfit = (sellPrice - buyPrice) * qty - commission - tax;

        totalProfit += netProfit;
        System.out.printf("📈 이번 거래 수익: %,.0f원 | 누적 수익: %,.0f원\n", netProfit, totalProfit);
    }

    // 현재 전체 요약 출력
    public void printSummary() {
        if (!initialized) return;

        double totalChange = lastBalance - baseBalance;
        String sign = totalChange >= 0 ? "▲" : "▼";
        System.out.println("\n==================== 📊 프로그램 수익 요약 ====================");
        System.out.printf("📌 시작 잔고: %,.0f원\n", baseBalance);
        System.out.printf("📌 현재 잔고: %,.0f원\n", lastBalance);
        System.out.printf("📌 총 누적 수익: %,.0f원\n", totalProfit);
        System.out.printf("📌 잔고 변화량: %s%,.0f원\n", sign, Math.abs(totalChange));
        System.out.println("============================================================\n");
    }
}

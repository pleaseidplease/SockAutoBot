package com.ljw.sockautobot.service;

import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ProfitTracker {

    private double baseBalance = 0;
    private double lastBalance = 0;
    private double totalProfit = 0;
    private boolean initialized = false;

    private final List<String> tradeLogs = new ArrayList<>();


    public double getBaseBalance() {return baseBalance;}
    public double getCurrentBalance() {return lastBalance;}
    public double getTotalProfit() {return totalProfit;}
    // 기준 잔고 대비 변화액
    public double getBalanceChange() {
        if (!initialized) { return 0;}
        return lastBalance - baseBalance;
    }
    // 기준 잔고 대비 변화율(%)
    public double getBalanceChangeRate() {
        if(!initialized || baseBalance == 0) { return 0;}
        return (lastBalance - baseBalance) / baseBalance * 100.0;
    }

    /** 잔고 추적 */
    public void trackBalance(JSONObject balanceResponse, boolean showChange) {
        if (balanceResponse == null) return;

        var output2 = balanceResponse.optJSONArray("output2");
        if (output2 == null || output2.length() == 0) return;

        double nowBalance = output2.getJSONObject(0).optDouble("tot_evlu_amt", 0);

        if (!initialized) {
            baseBalance = nowBalance;
            lastBalance = nowBalance;
            initialized = true;

            tradeLogs.add("프로그램 시작 잔고: " + nowBalance);
            return;
        }

        double diff = nowBalance - lastBalance;

        if (showChange && diff != 0) {
            String sign = diff > 0 ? "+" : "-";

            String log = "잔고 변화: " + sign + Math.abs(diff) + "원";
            tradeLogs.add(log);
        }

        lastBalance = nowBalance;
    }


    /** 거래 수익 누적 */
    public void recordProfit(double sellPrice, double buyPrice, int qty) {

        if (qty <= 0) return;

        double commission = (sellPrice + buyPrice) * 0.0015 * qty;
        double tax = sellPrice * 0.0015 * qty;

        double netProfit = (sellPrice - buyPrice) * qty - commission - tax;
        totalProfit += netProfit;

        tradeLogs.add("거래 수익: " + netProfit + "원 (누적: " + totalProfit + "원)");
    }


    /** 로그 반환 (프론트) */
    public List<String> getLogs() {
        return tradeLogs;
    }


    /** 요약 정보 반환 (프론트) */
    public JSONObject getProfitSummary() {

        JSONObject res = new JSONObject();
        res.put("baseBalance", baseBalance);
        res.put("currentBalance", lastBalance);
        res.put("totalProfit", totalProfit);
        res.put("balanceChange", lastBalance - baseBalance);

        return res;
    }
}

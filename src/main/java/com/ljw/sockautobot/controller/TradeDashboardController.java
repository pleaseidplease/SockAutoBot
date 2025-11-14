package com.ljw.sockautobot.controller;

import com.ljw.sockautobot.service.AutoTradeService;
import com.ljw.sockautobot.service.TradeCalculatorHybrid;
import com.ljw.sockautobot.service.ProfitTracker;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/dashboard")
public class TradeDashboardController {

    private final AutoTradeService autoTradeService;
    private final TradeCalculatorHybrid calculator;
    private final ProfitTracker profitTracker;

    @GetMapping("/status")
    public Map<String, Object> getStatus() {

        Map<String, Object> res = new HashMap<>();

        double price = calculator.getLastPrice();
        double avg = autoTradeService.getAvgBuyPrice();

        res.put("price", price);
        res.put("qty", autoTradeService.getQty());
        res.put("avgBuyPrice", avg);

        res.put("profitRate", calculator.calculateNetProfit(price, avg));

        res.put("slope", calculator.getSlope());
        res.put("accel", calculator.getAccel());
        res.put("momentum", calculator.getInstantMomentum());

        res.put("shortMA", calculator.getShortMA());
        res.put("longMA", calculator.getLongMA());
        res.put("atr", calculator.getATR());

        res.put("dailyMomentum", calculator.getDailyMomentum(price));

        return res;
    }

    @GetMapping("/logs")
    public Object getLogs() {
        return profitTracker.getLogs();
    }

    @GetMapping("/profit")
    public Object getProfit() {
        return profitTracker.getProfitSummary();
    }
}

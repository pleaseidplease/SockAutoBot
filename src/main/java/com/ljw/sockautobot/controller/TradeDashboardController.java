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

        res.put("symbol", autoTradeService.getSymbol());
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

        // 🔹  손익/잔고 현황
        res.put("baseBalance", profitTracker.getBaseBalance());          // 시작 잔고
        res.put("currentBalance", profitTracker.getCurrentBalance());    // 현재 잔고
        res.put("totalProfit", profitTracker.getTotalProfit());          // 누적 수익
        res.put("balanceChange", profitTracker.getBalanceChange());      // 잔고 변화액
        res.put("balanceChangeRate", profitTracker.getBalanceChangeRate()); // 변화율 %

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


    // 주식 종목 변경
    @PostMapping("/updateSymbol")
    public Map<String, Object> updateSymbol(@RequestBody Map<String, String> body) {
        String symbol = body.get("symbol");
        autoTradeService.updateSymbol(symbol);

        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("symbol", autoTradeService.getSymbol());
        return res;
    }

}

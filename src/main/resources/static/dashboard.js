
if (!window.LightweightCharts) {
    console.error("❌ LightweightCharts 라이브러리가 로드되지 않았습니다!");
}

// ======================================================
// 1) Lightweight Charts 초기화
// ======================================================
const chartDiv = document.getElementById("chart");
// 🔥 반드시 DOM 요소를 넣어서 createChart 실행해야 한다
const chart = LightweightCharts.createChart(chartDiv, {
    width: chartDiv.clientWidth,
    height: 350,
    layout: {
        background: { color: "#ffffff" },
        textColor: "#333"
    },
    grid: {
        vertLines: { color: "#eee" },
        horzLines: { color: "#eee" }
    },
    rightPriceScale: {
        mode: LightweightCharts.PriceScaleMode.Normal,  // 기본
        autoScale: true, // 🔥 자동 스케일 켜기 (가장 중요)
        alignLabels: true
    },
    timeScale: {
        timeVisible: true,
        secondsVisible: true
    }
});

console.log("chart =", chart);
console.log("chart keys =", Object.keys(chart));

// 🔥 이게 이제 정상적으로 동작한다
const lineSeries = chart.addLineSeries({
    color: "#2962FF",
    lineWidth: 2,
});

// 테스트 데이터
lineSeries.setData([
    { time: "2024-01-01", value: 100 },
    { time: "2024-01-02", value: 120 },
    { time: "2024-01-03", value: 90 },
]);


function updateChart(price) {
    if (!price || price <= 0 || isNaN(price)) return;

    lineSeries.update({
        time: Math.floor(Date.now() / 1000),
        value: price
    });
}

// 리사이즈 대응
window.addEventListener("resize", () => {
    chart.applyOptions({ width: chartDiv.clientWidth });
});


// ======================================================
// 2) 종목 변경 버튼
// ======================================================
document.getElementById("updateSymbolBtn").addEventListener("click", async () => {
    const symbol = document.getElementById("updateSymbol").value.trim();
    if (!symbol) {
        alert("종목코드를 입력해주세요");
        return;
    }

    const res = await fetch("/api/dashboard/updateSymbol", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ symbol })
    });

    const data = await res.json();
    if (data.success) {
        alert(data.symbol + "으로 종목 변경 완료!");
        updateStatus();
    }
});


// ======================================================
// 공통 숫자 포맷
// ======================================================
function formatNumber(n) {
    if (n === null || n === undefined || isNaN(n)) return "-";
    return Number(n).toLocaleString();
}


// ======================================================
// 3) 실시간 상태 호출
// ======================================================
function updateStatus() {
    fetch("/api/dashboard/status")
        .then(res => res.json())
        .then(d => {

            // 값 존재 여부 체크
            const price = d.price || 0;
            const momentum = d.momentum || 0;

            // ------------------------------------
            // 실시간 시세 렌더링
            // ------------------------------------
            document.getElementById("symbol").textContent = d.symbol || "-";
            document.getElementById("price").textContent = formatNumber(price);
            document.getElementById("qty").textContent = d.qty || 0;
            document.getElementById("avg").textContent = formatNumber(d.avgBuyPrice || 0);

            const profitRate = d.profitRate || 0;
            const profitEl = document.getElementById("profitRate");
            profitEl.textContent = profitRate.toFixed(3) + " %";

            // 색상 처리
            profitEl.className = "";
            document.getElementById("price").className = "";

            if (profitRate > 0) profitEl.classList.add("up");
            else if (profitRate < 0) profitEl.classList.add("down");

            if (momentum > 0) document.getElementById("price").classList.add("up");
            else if (momentum < 0) document.getElementById("price").classList.add("down");

            document.getElementById("volume").textContent =
                formatNumber(d.volume || 0);

            document.getElementById("tickStrength").textContent =
                (d.tickStrength || 0).toFixed(2) + " %";

            document.getElementById("bidQty").textContent =
                formatNumber(d.bidQty || 0);

            document.getElementById("askQty").textContent =
                formatNumber(d.askQty || 0);

            document.getElementById("kospi").textContent =
                d.kospi ? d.kospi.toFixed(2) : "-";


            // ------------------------------------
            // 실시간 차트 업데이트
            // ------------------------------------
            updateChart(price);

            // ------------------------------------
            // AI 상태 표시
            // ------------------------------------
            const ai = `
                 <div>📈 기울기(slope): ${d.slope.toFixed(5)}</div>
                <div>⚡ 가속도(accel): ${d.accel.toFixed(5)}</div>
                <div>🔥 순간 모멘텀: ${momentum.toFixed(3)} %</div>
                <div>📊 단기 MA: ${d.shortMA.toFixed(2)}</div>
                <div>📉 장기 MA: ${d.longMA.toFixed(2)}</div>
                <div>📡 ATR: ${d.atr.toFixed(3)}</div>
                <div>📅 일간 모멘텀: ${d.dailyMomentum.toFixed(3)} %</div>
            
                <!-- 🔥 새 항목들 -->
                <hr>
                <div>💹 거래량: ${formatNumber(d.volume)}</div>
                <div>📡 체결강도: ${(d.tickStrength || 0).toFixed(2)} %</div>
                <div>🟦 매수 잔량(bid1): ${formatNumber(d.bidQty)}</div>
                <div>🟥 매도 잔량(ask1): ${formatNumber(d.askQty)}</div>
                <div>🌏 KOSPI 지수: ${d.kospi ? d.kospi.toFixed(2) : "-"}</div>
            `;
            document.getElementById("ai-status").innerHTML = ai;

            // ------------------------------------
            // AI 추세 배너
            // ------------------------------------
            const banner = document.getElementById("trend-banner");

            if (momentum > 0.1 && d.slope > 0 && d.accel > 0)
                banner.textContent = "🚀 강한 상승 추세 유지 중!";
            else if (momentum < -0.1 && d.slope < 0)
                banner.textContent = "📉 하락 경고 — 주의 필요";
            else
                banner.textContent = "AI 상태 분석 중...";

        })
        .catch(err => console.error("status 오류:", err));
}


// ======================================================
// 4) 잔고 상태
// ======================================================
function updateProfit() {
    fetch("/api/dashboard/profit")
        .then(res => res.json())
        .then(d => {
            document.getElementById("baseBalance").textContent =
                formatNumber(d.baseBalance) + " 원";

            document.getElementById("currentBalance").textContent =
                formatNumber(d.currentBalance) + " 원";

            document.getElementById("totalProfit").textContent =
                formatNumber(d.totalProfit) + " 원";

            const diff = d.balanceChange || 0;
            const rate = d.balanceChangeRate || 0;

            const diffText = (diff >= 0 ? "+" : "") + formatNumber(diff) + " 원";
            const rateText = (rate >= 0 ? "+" : "") + rate.toFixed(3) + " %";

            document.getElementById("balanceChange").textContent = diffText;
            document.getElementById("balanceChangeRate").textContent = rateText;
        })
        .catch(err => console.error("profit 오류:", err));
}


// ======================================================
// 5) 로그 업데이트
// ======================================================
function updateLogs() {
    fetch("/api/dashboard/logs")
        .then(res => res.json())
        .then(list => {
            const ul = document.getElementById("log-list");
            ul.innerHTML = "";

            list.slice(-30).forEach(line => {
                const li = document.createElement("li");
                li.textContent = line;
                ul.appendChild(li);
            });
        })
        .catch(err => console.error("logs 오류:", err));
}


// ======================================================
// 6) 1.5초마다 주기적으로 갱신
// ======================================================
setInterval(() => {
    updateStatus();
    updateProfit();
    updateLogs();
}, 1500);


// 첫 1회 즉시 실행
updateStatus();
updateProfit();
updateLogs();

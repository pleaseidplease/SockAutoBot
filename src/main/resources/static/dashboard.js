function formatNumber(n) {
    if (n === null || n === undefined || isNaN(n)) return "-";
    return Number(n).toLocaleString();
}

function updateStatus() {
    fetch("/api/dashboard/status")
        .then(res => res.json())
        .then(d => {
            const priceEl = document.getElementById("price");
            const profitEl = document.getElementById("profitRate");

            // 숫자들 표시
            priceEl.textContent = formatNumber(d.price);
            document.getElementById("qty").textContent = d.qty;
            document.getElementById("avg").textContent = formatNumber(d.avgBuyPrice);

            // 수익률
            const profitRate = d.profitRate || 0;
            profitEl.textContent = profitRate.toFixed(3) + " %";

            // 색상 처리
            priceEl.className = "";
            profitEl.className = "";
            if (d.momentum > 0) {
                priceEl.classList.add("up");
            } else if (d.momentum < 0) {
                priceEl.classList.add("down");
            }

            if (profitRate > 0) {
                profitEl.classList.add("up");
            } else if (profitRate < 0) {
                profitEl.classList.add("down");
            }

            // AI 추세 영역
            const aiHtml = `
                <div>📈 slope: ${d.slope.toFixed(5)}</div>
                <div>⚡ accel: ${d.accel.toFixed(5)}</div>
                <div>🔥 순간 모멘텀: ${d.momentum.toFixed(3)} %</div>
                <div>📊 단기 MA: ${d.shortMA.toFixed(2)}</div>
                <div>📉 장기 MA: ${d.longMA.toFixed(2)}</div>
                <div>📡 ATR: ${d.atr.toFixed(3)}</div>
                <div>📅 일간 모멘텀: ${d.dailyMomentum.toFixed(3)} %</div>
            `;
            document.getElementById("ai-status").innerHTML = aiHtml;
        })
        .catch(err => {
            console.error("status 호출 오류:", err);
        });
}

function updateProfit() {
    fetch("/api/dashboard/profit")
        .then(res => res.json())
        .then(d => {
            document.getElementById("baseBalance").textContent = formatNumber(d.baseBalance) + " 원";
            document.getElementById("currentBalance").textContent = formatNumber(d.currentBalance) + " 원";
            document.getElementById("totalProfit").textContent = formatNumber(d.totalProfit) + " 원";

            const diff = d.balanceChange || 0;
            const rate = d.balanceChangeRate || 0;

            const diffText = (diff >= 0 ? "+" : "") + formatNumber(diff) + " 원";
            const rateText = (rate >= 0 ? "+" : "") + rate.toFixed(3) + " %";

            document.getElementById("balanceChange").textContent = diffText;
            document.getElementById("balanceChangeRate").textContent = rateText;
        })
        .catch(err => {
            console.error("profit 호출 오류:", err);
        });
}

function updateLogs() {
    fetch("/api/dashboard/logs")
        .then(res => res.json())
        .then(list => {
            const ul = document.getElementById("log-list");
            ul.innerHTML = "";

            // 최근 로그 30개까지만 역순으로
            const sliced = list.slice(-30);

            sliced.forEach(line => {
                const li = document.createElement("li");
                li.textContent = line;
                ul.appendChild(li);
            });
        })
        .catch(err => {
            console.error("logs 호출 오류:", err);
        });
}

// 주기적으로 갱신 (1.5초마다)
setInterval(() => {
    updateStatus();
    updateProfit();
    updateLogs();
}, 1500);

// 첫 로딩 시 즉시 한 번 호출
updateStatus();
updateProfit();
updateLogs();

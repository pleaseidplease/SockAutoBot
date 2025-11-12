package com.ljw.sockautobot.service;

public class KisRateLimiter {

    private long lastRequestTime = 0L;
    private long interval = 1000; // 기본 1초

    /**
     * 모드 설정 (virtual = 모의투자, real = 실거래)
     */
    public void setMode(String mode) {
        if ("virtual".equalsIgnoreCase(mode)) {
            interval = 2500; // ✅ 모의투자: 최소 2.5초 간격 (KIS 권장)
        } else {
            interval = 300;  // ✅ 실거래: 0.3초 (초당 3회 이하)
        }
    }

    /**
     * 다음 요청까지 대기 (API 초당 제한 방지)
     */
    public synchronized void waitForNext() {
        long now = System.currentTimeMillis();

        // 첫 호출이라면 바로 통과
        if (lastRequestTime == 0L) {
            lastRequestTime = now;
            return;
        }

        long nextAllowedTime = lastRequestTime + interval;
        long waitTime = nextAllowedTime - now;

        if (waitTime > 0) {
            try {
                Thread.sleep(waitTime);
            } catch (InterruptedException e) {
                System.err.println("⚠️ [KIS RateLimiter] sleep 중 인터럽트 발생");
                Thread.currentThread().interrupt();
            }
        }

        // ✅ 대기가 끝난 직후, 실제 요청 시점 기준으로 갱신
        lastRequestTime = System.currentTimeMillis();
    }
}

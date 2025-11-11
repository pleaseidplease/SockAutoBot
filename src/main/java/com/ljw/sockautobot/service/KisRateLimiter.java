package com.ljw.sockautobot.service;

// ✅ KisRateLimiter.java (안정화 버전)
public class KisRateLimiter {

    private long lastRequestTime = System.currentTimeMillis();
    private long interval = 1000; // 기본 1초

    /**
     * 모드 설정 (virtual = 모의투자, real = 실거래)
     */
    public void setMode(String mode) {
        if ("virtual".equalsIgnoreCase(mode)) {
            interval = 3000; // ✅ 모의투자: 3초 간격 유지 (안전 여유 포함)
        } else {
            interval = 300;  // ✅ 실거래: 0.3초 간격 (초당 3회 이하)
        }
    }

    /**
     * 다음 요청까지 대기 (API 초당 제한 방지)
     */
    public synchronized void waitForNext() {
        long now = System.currentTimeMillis();
        long diff = now - lastRequestTime;

        if (diff < interval) {
            try {
                Thread.sleep(interval - diff);
            } catch (InterruptedException e) {
                System.err.println("⚠️ [KIS RateLimiter] sleep 중 인터럽트 발생");
                Thread.currentThread().interrupt();
            }
        }

        lastRequestTime = System.currentTimeMillis();
    }
}

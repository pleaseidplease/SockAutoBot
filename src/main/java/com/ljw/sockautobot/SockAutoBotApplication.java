package com.ljw.sockautobot;

import com.ljw.sockautobot.service.AutoTradeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SockAutoBotApplication implements CommandLineRunner {


    @Autowired
            private AutoTradeService tradeService;

    public static void main(String[] args) {
        SpringApplication.run(SockAutoBotApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("자동매매 봇 시작...");
        tradeService.autoTrade();
    }
}

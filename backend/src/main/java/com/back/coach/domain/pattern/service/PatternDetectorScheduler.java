package com.back.coach.domain.pattern.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PatternDetectorScheduler {

    private static final Logger log = LoggerFactory.getLogger(PatternDetectorScheduler.class);

    private final PatternDetectorService service;

    public PatternDetectorScheduler(PatternDetectorService service) {
        this.service = service;
    }

    @Scheduled(cron = "0 0 0 * * *", zone = "UTC")
    public void runDailyDetection() {
        try {
            service.runFullScan();
        } catch (Exception e) {
            log.error("[PatternDetector] daily scan failed", e);
        }
    }
}

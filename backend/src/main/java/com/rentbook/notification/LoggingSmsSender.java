package com.rentbook.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** The default: writes texts to the log, with the number masked. Set rentbook.sms.provider=twilio to send. */
@Component
@ConditionalOnProperty(name = "rentbook.sms.provider", havingValue = "log", matchIfMissing = true)
class LoggingSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingSmsSender.class);

    @Override
    public String send(String toE164, String text) {
        String masked = toE164.length() > 4 ? "…" + toE164.substring(toE164.length() - 4) : toE164;
        log.info("SMS to {}: {}", masked, text);
        return "logged";
    }
}

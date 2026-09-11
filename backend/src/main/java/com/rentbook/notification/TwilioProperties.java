package com.rentbook.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("rentbook.twilio")
public record TwilioProperties(String accountSid, String authToken, String from) {
}

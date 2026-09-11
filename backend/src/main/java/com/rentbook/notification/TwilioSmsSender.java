package com.rentbook.notification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Twilio's Messages API. Trial accounts only reach verified numbers, and Indian DLT rules can filter
 * unregistered senders, so email remains the dependable channel.
 */
@Component
@ConditionalOnProperty(name = "rentbook.sms.provider", havingValue = "twilio")
class TwilioSmsSender implements SmsSender {

    private final TwilioProperties twilio;
    private final RestClient client;

    TwilioSmsSender(TwilioProperties twilio) {
        this.twilio = twilio;
        this.client = RestClient.builder()
                .baseUrl("https://api.twilio.com/2010-04-01")
                .defaultHeaders(headers -> headers.setBasicAuth(twilio.accountSid(), twilio.authToken()))
                .build();
    }

    @Override
    public String send(String toE164, String text) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("To", toE164);
        form.add("From", twilio.from());
        form.add("Body", text);
        Map<?, ?> response = client.post()
                .uri("/Accounts/{sid}/Messages.json", twilio.accountSid())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(Map.class);
        return response == null ? null : String.valueOf(response.get("sid"));
    }
}

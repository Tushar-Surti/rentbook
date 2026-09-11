package com.rentbook.notification;

import com.rentbook.config.RentbookProperties;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** Plain-text email over SMTP: Mailpit locally, the SendGrid SMTP relay in production. */
@Component
public class EmailSender {

    private final JavaMailSender mail;
    private final RentbookProperties properties;

    EmailSender(JavaMailSender mail, RentbookProperties properties) {
        this.mail = mail;
        this.properties = properties;
    }

    /** SMTP returns no message id, so there is none to record. */
    public String send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.mail().from());
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        mail.send(message);
        return null;
    }
}

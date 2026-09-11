package com.rentbook.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import com.rentbook.notification.TwilioProperties;
import com.rentbook.payment.RazorpayProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties({RentbookProperties.class, TwilioProperties.class, RazorpayProperties.class})
public class AppConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}

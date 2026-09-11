package com.rentbook.notification;

/** Sends one text message and returns the provider's id for it. */
public interface SmsSender {

    String send(String toE164, String text);
}

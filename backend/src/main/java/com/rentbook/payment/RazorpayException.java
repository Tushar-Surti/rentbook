package com.rentbook.payment;

/** Razorpay answered with an error. The body is kept for the logs; it is never shown to users. */
public class RazorpayException extends RuntimeException {

    private final int status;

    public RazorpayException(int status, String body) {
        super("Razorpay returned " + status + ": " + body);
        this.status = status;
    }

    public int status() {
        return status;
    }

    /** Razorpay refused this server's key id and secret: a misconfiguration, not anything the user did. */
    public boolean isAuthFailure() {
        return status == 401;
    }
}

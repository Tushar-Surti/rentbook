package com.rentbook.common;

/**
 * Formats paise as rupees with Indian digit grouping: 18400000 paise as "₹1,84,000", 1250050 as
 * "₹12,500.50". Java's en-IN number format groups in threes throughout ("184,000"), so this is done by hand.
 */
public final class Rupees {

    private Rupees() {
    }

    public static String format(long paise) {
        return (paise < 0 ? "-₹" : "₹") + figures(Math.abs(paise));
    }

    /** The figures alone, grouped in thousands, then lakhs and crores; paise only when there are some. */
    public static String figures(long paise) {
        String digits = Long.toString(Math.abs(paise) / 100);
        StringBuilder grouped = new StringBuilder();
        if (digits.length() <= 3) {
            grouped.append(digits);
        } else {
            String head = digits.substring(0, digits.length() - 3);
            for (int end = head.length(); end > 0; end -= 2) {
                int start = Math.max(0, end - 2);
                grouped.insert(0, head, start, end);
                if (start > 0) {
                    grouped.insert(0, ',');
                }
            }
            grouped.append(',').append(digits, digits.length() - 3, digits.length());
        }
        long rest = Math.abs(paise) % 100;
        if (rest > 0) {
            grouped.append('.').append(rest < 10 ? "0" : "").append(rest);
        }
        return grouped.toString();
    }
}

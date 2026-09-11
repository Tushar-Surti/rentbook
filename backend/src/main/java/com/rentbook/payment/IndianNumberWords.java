package com.rentbook.payment;

/**
 * Amounts in words the way Indian rent receipts write them, grouped in crores, lakhs and thousands:
 * 3750050 paise is "Rupees Thirty Seven Thousand Five Hundred and Fifty Paise only".
 */
final class IndianNumberWords {

    private static final String[] ONES = {"", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine",
            "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"};
    private static final String[] TENS = {"", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty",
            "Ninety"};

    private IndianNumberWords() {
    }

    static String rupees(long paise) {
        long rupees = paise / 100;
        long rest = paise % 100;
        String words = "Rupees " + (rupees == 0 ? "Zero" : words(rupees));
        if (rest > 0) {
            words += " and " + words(rest) + " Paise";
        }
        return words + " only";
    }

    static String words(long number) {
        StringBuilder out = new StringBuilder();
        number = group(out, number, 10_000_000L, "Crore");
        number = group(out, number, 100_000L, "Lakh");
        number = group(out, number, 1_000L, "Thousand");
        number = group(out, number, 100L, "Hundred");
        if (number > 0) {
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(belowHundred((int) number));
        }
        return out.toString();
    }

    private static long group(StringBuilder out, long number, long unit, String name) {
        long count = number / unit;
        if (count > 0) {
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(unit == 10_000_000L && count >= 100 ? words(count) : belowHundred((int) count));
            out.append(' ').append(name);
        }
        return number % unit;
    }

    private static String belowHundred(int number) {
        if (number < 20) {
            return ONES[number];
        }
        return TENS[number / 10] + (number % 10 == 0 ? "" : " " + ONES[number % 10]);
    }
}

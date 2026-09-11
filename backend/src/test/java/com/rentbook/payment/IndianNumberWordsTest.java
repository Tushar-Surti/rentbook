package com.rentbook.payment;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IndianNumberWordsTest {

    @Test
    void writesRupeesInLakhsAndCrores() {
        assertThat(IndianNumberWords.rupees(3_750_000)).isEqualTo("Rupees Thirty Seven Thousand Five Hundred only");
        assertThat(IndianNumberWords.rupees(18_400_000)).isEqualTo("Rupees One Lakh Eighty Four Thousand only");
        assertThat(IndianNumberWords.rupees(1_000_000_000L)).isEqualTo("Rupees One Crore only");
        assertThat(IndianNumberWords.rupees(12_34_56_789_00L))
                .isEqualTo("Rupees Twelve Crore Thirty Four Lakh Fifty Six Thousand Seven Hundred Eighty Nine only");
    }

    @Test
    void writesPaiseAndZero() {
        assertThat(IndianNumberWords.rupees(3_750_050))
                .isEqualTo("Rupees Thirty Seven Thousand Five Hundred and Fifty Paise only");
        assertThat(IndianNumberWords.rupees(0)).isEqualTo("Rupees Zero only");
        assertThat(IndianNumberWords.rupees(1_100)).isEqualTo("Rupees Eleven only");
    }
}

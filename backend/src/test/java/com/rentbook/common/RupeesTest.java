package com.rentbook.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RupeesTest {

    @Test
    void groupsInThousandsThenLakhsAndCrores() {
        assertThat(Rupees.format(1_250_000)).isEqualTo("₹12,500");
        assertThat(Rupees.format(18_400_000)).isEqualTo("₹1,84,000");
        assertThat(Rupees.format(123_456_789_000L)).isEqualTo("₹1,23,45,67,890");
        assertThat(Rupees.format(50_000)).isEqualTo("₹500");
        assertThat(Rupees.format(0)).isEqualTo("₹0");
    }

    @Test
    void showsPaiseOnlyWhenThereAreSome() {
        assertThat(Rupees.format(1_250_050)).isEqualTo("₹12,500.50");
        assertThat(Rupees.format(1_250_005)).isEqualTo("₹12,500.05");
    }
}

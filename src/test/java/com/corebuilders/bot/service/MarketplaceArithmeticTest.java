package com.corebuilders.bot.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MarketplaceArithmeticTest {
    @Test
    void calculatesCartTotalsExactly() {
        assertEquals(1_350L, MarketplaceArithmetic.multiply(450L, 3));
        assertEquals(1_800L, MarketplaceArithmetic.add(1_350L, 450L));
    }

    @Test
    void convertsOverflowIntoStableValidationError() {
        MarketplaceException multiply = assertThrows(MarketplaceException.class,
                () -> MarketplaceArithmetic.multiply(Long.MAX_VALUE, 2));
        MarketplaceException add = assertThrows(MarketplaceException.class,
                () -> MarketplaceArithmetic.add(Long.MAX_VALUE, 1));

        assertEquals(MarketplaceException.Code.VALIDATION, multiply.code());
        assertEquals(MarketplaceException.Code.VALIDATION, add.code());
    }
}

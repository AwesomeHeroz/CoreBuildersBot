package com.corebuilders.bot.service;

import static com.corebuilders.bot.service.MarketplaceException.validation;

/** Overflow-safe arithmetic for cart and order totals. */
public final class MarketplaceArithmetic {
    private MarketplaceArithmetic() {}

    public static long multiply(long price, int quantity) {
        try {
            return Math.multiplyExact(price, (long) quantity);
        } catch (ArithmeticException error) {
            throw validation("Cart total is too large.");
        }
    }

    public static long add(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException error) {
            throw validation("Cart total is too large.");
        }
    }
}

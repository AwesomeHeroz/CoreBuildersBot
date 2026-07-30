package com.corebuilders.bot.service;

import java.util.List;

/** Marketplace state names and order completion rules. */
public final class MarketplaceStates {
    public static final String ORDER_HELD = "HELD";
    public static final String ORDER_COMPLETED = "COMPLETED";
    public static final String ORDER_DISPUTED = "DISPUTED";
    public static final String LINE_PENDING = "PENDING_DELIVERY";
    public static final String LINE_DELIVERED = "DELIVERED";
    public static final String LINE_SETTLED = "SETTLED";
    public static final String LINE_CANCELLED = "CANCELLED";
    public static final String LINE_REFUNDED = "REFUNDED";
    public static final String LINE_DISPUTED = "DISPUTED";

    private MarketplaceStates() {}

    public static String orderStatus(List<LineState> lines) {
        if (lines.stream().anyMatch(line -> LINE_DISPUTED.equals(line.status()))) {
            return ORDER_DISPUTED;
        }
        return isComplete(lines) ? ORDER_COMPLETED : ORDER_HELD;
    }

    public static boolean isComplete(List<LineState> lines) {
        return !lines.isEmpty() && lines.stream().allMatch(MarketplaceStates::isComplete);
    }

    private static boolean isComplete(LineState line) {
        return LINE_SETTLED.equals(line.status())
                || LINE_CANCELLED.equals(line.status())
                || LINE_REFUNDED.equals(line.status())
                || (LINE_DELIVERED.equals(line.status()) && line.fundsReleased());
    }

    public record LineState(String status, boolean fundsReleased) {}
}

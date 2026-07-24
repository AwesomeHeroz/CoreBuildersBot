package com.corebuilders.bot.db;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.time.DayOfWeek;
import java.util.UUID;

public final class DbValues {
    private DbValues() {}

    public static String uuid(UUID value) {
        return value == null ? null : value.toString();
    }

    public static UUID uuid(String value) {
        return value == null ? null : UUID.fromString(value);
    }

    public static LocalDateTime time(Instant value) {
        return value == null ? null : LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    public static Instant instant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    public static LocalDateTime startOfCurrentWeek() {
        LocalDateTime now = now();
        return now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toLocalDate().atStartOfDay();
    }
}

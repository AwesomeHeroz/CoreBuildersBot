package com.corebuilders.bot.discord;

import com.corebuilders.bot.model.Models.ApplicationFile;
import com.corebuilders.bot.model.Models.ApplicationRecord;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** Pure formatting policy for application messages, files, and ticket names. */
public final class ApplicationTextFormatter {
    private final String ticketNamePattern;

    public ApplicationTextFormatter(String ticketNamePattern) {
        this.ticketNamePattern = ticketNamePattern == null || ticketNamePattern.isBlank()
                ? "application-{username}-{id}"
                : ticketNamePattern;
    }

    public String ticketName(ApplicationRecord application) {
        String shortId = application.id().toString().substring(0, 8);
        String value = ticketNamePattern
                .replace("{username}", nullToEmpty(application.username()))
                .replace("{id}", shortId)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9-]+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
        return truncate(value.isBlank() ? "application-" + shortId : value, 100);
    }

    public String message(
            String template,
            ApplicationRecord application,
            String reviewerMention,
            String roleName,
            String reason
    ) {
        String ticket = application.ticketChannelId() == null || application.ticketChannelId().isBlank()
                ? ""
                : "<#" + application.ticketChannelId() + ">";
        return nullToEmpty(template)
                .replace("{user}", "<@" + application.discordUserId() + ">")
                .replace("{username}", nullToEmpty(application.username()))
                .replace("{application_id}", application.id().toString())
                .replace("{reason}", nullToEmpty(reason == null ? application.reviewReason() : reason))
                .replace("{ticket}", ticket)
                .replace("{reviewer}", nullToEmpty(reviewerMention))
                .replace("{role}", nullToEmpty(roleName));
    }

    public String formatFiles(List<ApplicationFile> files) {
        if (files == null || files.isEmpty()) return "No files uploaded.";
        return files.stream()
                .map(file -> "[" + escapeMarkdown(file.fileName()) + "](" + file.url() + ") • " + humanBytes(file.sizeBytes()))
                .collect(Collectors.joining("\n"));
    }

    public String humanBytes(long bytes) {
        long safeBytes = Math.max(0, bytes);
        if (safeBytes < 1024) return safeBytes + " B";
        double kib = safeBytes / 1024.0;
        if (kib < 1024) return String.format(Locale.ROOT, "%.1f KiB", kib);
        return String.format(Locale.ROOT, "%.1f MiB", kib / 1024.0);
    }

    public String timestamp(Instant value) {
        return value == null ? "—" : "<t:" + value.getEpochSecond() + ":F>";
    }

    public String safeFileName(String value) {
        String file = nullToEmpty(value).replaceAll("[\\r\\n\\t]", "_");
        return file.isBlank() ? "application-upload" : truncate(file, 200);
    }

    public String escapeMarkdown(String value) {
        return nullToEmpty(value).replace("[", "\\[").replace("]", "\\]");
    }

    public String truncate(String value, int max) {
        return DiscordFormatting.truncate(nullToEmpty(value), max);
    }

    public String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}

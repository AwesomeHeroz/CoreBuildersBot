package com.corebuilders.bot.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Configuration snapshot for the Discord application workflow.
 *
 * Channel/category references may be Discord IDs or exact names; security-sensitive role references use IDs only.
 * Questions are intentionally configuration-driven so the application form can be
 * changed without recompiling the plugin.
 */
public final class ApplicationConfig {
    public enum QuestionType { TEXT, UPLOAD }
    public enum TextStyle { SHORT, PARAGRAPH }

    public record Question(
            String id,
            QuestionType type,
            String label,
            String description,
            String placeholder,
            boolean required,
            TextStyle style,
            int minLength,
            int maxLength,
            int minFiles,
            int maxFiles
    ) {}

    private final boolean enabled;
    private final String pendingChannel;
    private final String acceptedChannel;
    private final String rejectedChannel;
    private final String ticketCategory;
    private final String ticketNamePattern;
    private final Set<String> reviewerRoleIds;
    private final String approvedRoleId;
    private final boolean preventDuplicatePending;
    private final boolean allowReapplyAfterDecision;
    private final boolean deleteTicketOnDecision;
    private final int sessionExpiryMinutes;
    private final long maxUploadFileSizeBytes;
    private final long maxTotalUploadSizeBytes;
    private final List<Question> questions;

    private final String submittedMessage;
    private final String acceptedMessage;
    private final String rejectedMessage;
    private final String ticketCreatedMessage;
    private final String ticketOpeningMessage;

    public ApplicationConfig(FileConfiguration config) {
        this.enabled = config.getBoolean("applications.enabled", false);
        this.pendingChannel = clean(config.getString("applications.channels.pending", "pending-applications"));
        this.acceptedChannel = clean(config.getString("applications.channels.accepted", "accepted-applications"));
        this.rejectedChannel = clean(config.getString("applications.channels.rejected", "rejected-applications"));
        this.ticketCategory = clean(config.getString("applications.tickets.category", "application-tickets"));
        this.ticketNamePattern = defaultIfBlank(
                config.getString("applications.tickets.name-pattern", "application-{username}-{id}"),
                "application-{username}-{id}"
        );
        this.reviewerRoleIds = enabled
                ? parseSnowflakeSet(config.getStringList("applications.reviewer-role-ids"), "applications.reviewer-role-ids")
                : Set.of();
        this.approvedRoleId = enabled
                ? requireSnowflake(config.getString("applications.approval.role-id", ""), "applications.approval.role-id")
                : clean(config.getString("applications.approval.role-id", ""));
        this.preventDuplicatePending = config.getBoolean("applications.prevent-duplicate-pending", true);
        this.allowReapplyAfterDecision = config.getBoolean("applications.allow-reapply-after-decision", true);
        this.deleteTicketOnDecision = config.getBoolean("applications.tickets.delete-on-decision", false);
        this.sessionExpiryMinutes = clamp(config.getInt("applications.session-expiry-minutes", 30), 5, 240);
        int maxUploadFileSizeMb = clamp(config.getInt("applications.uploads.max-file-size-mb", 15), 1, 100);
        int maxTotalUploadSizeMb = clamp(config.getInt("applications.uploads.max-total-size-mb", 50), maxUploadFileSizeMb, 500);
        this.maxUploadFileSizeBytes = maxUploadFileSizeMb * 1024L * 1024L;
        this.maxTotalUploadSizeBytes = maxTotalUploadSizeMb * 1024L * 1024L;
        this.questions = List.copyOf(parseQuestions(config));

        this.submittedMessage = defaultIfBlank(
                config.getString("applications.messages.submitted", "Your application has been submitted successfully."),
                "Your application has been submitted successfully."
        );
        this.acceptedMessage = defaultIfBlank(
                config.getString("applications.messages.accepted", "Your Core Builders application has been accepted. Welcome to the group!"),
                "Your Core Builders application has been accepted. Welcome to the group!"
        );
        this.rejectedMessage = defaultIfBlank(
                config.getString("applications.messages.rejected", "Your Core Builders application has been rejected. Reason: {reason}"),
                "Your Core Builders application has been rejected. Reason: {reason}"
        );
        this.ticketCreatedMessage = defaultIfBlank(
                config.getString("applications.messages.ticket-created", "A private discussion ticket was created for your application: {ticket}"),
                "A private discussion ticket was created for your application: {ticket}"
        );
        this.ticketOpeningMessage = defaultIfBlank(
                config.getString("applications.messages.ticket-opening", "Please use this channel to discuss the application. The applicant and configured reviewer roles can access it."),
                "Please use this channel to discuss the application. The applicant and configured reviewer roles can access it."
        );

        validate();
    }

    private void validate() {
        if (!enabled) return;
        if (pendingChannel.isBlank() || acceptedChannel.isBlank() || rejectedChannel.isBlank()) {
            throw new IllegalStateException("applications.channels.pending/accepted/rejected must be configured when applications are enabled.");
        }
        if (ticketCategory.isBlank()) {
            throw new IllegalStateException("applications.tickets.category must be configured when applications are enabled.");
        }
        if (reviewerRoleIds.isEmpty()) {
            throw new IllegalStateException("applications.reviewer-role-ids must contain at least one Discord role ID.");
        }
        if (questions.isEmpty()) {
            throw new IllegalStateException("applications.questions must contain at least one configured question.");
        }
        Set<String> ids = new LinkedHashSet<>();
        for (Question question : questions) {
            if (!question.id().matches("[A-Za-z0-9_-]{1,60}")) {
                throw new IllegalStateException("Invalid application question id '" + question.id()
                        + "'. Use 1-60 letters, numbers, underscores, or hyphens.");
            }
            if (!ids.add(question.id())) {
                throw new IllegalStateException("Duplicate application question id: " + question.id());
            }
        }
    }

    private static List<Question> parseQuestions(FileConfiguration config) {
        List<Map<?, ?>> rawQuestions = config.getMapList("applications.questions");
        List<Question> result = new ArrayList<>();
        int index = 0;
        for (Map<?, ?> raw : rawQuestions) {
            index++;
            String id = clean(value(raw, "id", "question_" + index));
            String typeValue = clean(value(raw, "type", "text")).toUpperCase(Locale.ROOT);
            QuestionType type;
            try {
                type = QuestionType.valueOf(typeValue);
            } catch (IllegalArgumentException error) {
                throw new IllegalStateException("Unknown application question type '" + typeValue + "' for " + id
                        + ". Supported types: text, upload.");
            }

            String label = defaultIfBlank(value(raw, "label", "Question " + index), "Question " + index);
            String description = clean(value(raw, "description", ""));
            String placeholder = clean(value(raw, "placeholder", ""));
            boolean required = bool(raw, "required", true);

            if (type == QuestionType.TEXT) {
                String styleValue = clean(value(raw, "style", "paragraph")).toUpperCase(Locale.ROOT);
                TextStyle style = "SHORT".equals(styleValue) ? TextStyle.SHORT : TextStyle.PARAGRAPH;
                int maxLength = clamp(integer(raw, "max-length", style == TextStyle.SHORT ? 250 : 4000), 1, 4000);
                int minLength = clamp(integer(raw, "min-length", required ? 1 : 0), 0, maxLength);
                result.add(new Question(
                        id, type, label, description, placeholder, required, style,
                        minLength, maxLength, 0, 0
                ));
            } else {
                int maxFiles = clamp(integer(raw, "max-files", 3), 1, 10);
                int minFiles = clamp(integer(raw, "min-files", required ? 1 : 0), 0, maxFiles);
                result.add(new Question(
                        id, type, label, description, "", required, TextStyle.PARAGRAPH,
                        0, 0, minFiles, maxFiles
                ));
            }
        }
        return result;
    }

    private static Set<String> parseSnowflakeSet(List<String> configured, String path) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (configured != null) {
            for (String item : configured) {
                String value = clean(item);
                if (value.isBlank()) continue;
                if (!value.matches("\\d{15,22}")) {
                    throw new IllegalStateException(path + " must contain Discord role IDs only; invalid value: " + value);
                }
                result.add(value);
            }
        }
        return Set.copyOf(result);
    }

    private static String requireSnowflake(String value, String path) {
        String cleaned = clean(value);
        if (!cleaned.matches("\\d{15,22}")) {
            throw new IllegalStateException(path + " must be a Discord role ID.");
        }
        return cleaned;
    }

    private static String value(Map<?, ?> map, String key, String defaultValue) {
        Object raw = map.get(key);
        return raw == null ? defaultValue : String.valueOf(raw);
    }

    private static boolean bool(Map<?, ?> map, String key, boolean defaultValue) {
        Object raw = map.get(key);
        if (raw == null) return defaultValue;
        if (raw instanceof Boolean value) return value;
        return Boolean.parseBoolean(String.valueOf(raw));
    }

    private static int integer(Map<?, ?> map, String key, int defaultValue) {
        Object raw = map.get(key);
        if (raw == null) return defaultValue;
        if (raw instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(String.valueOf(raw));
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static String defaultIfBlank(String value, String fallback) {
        String clean = clean(value);
        return clean.isBlank() ? fallback : clean;
    }

    public boolean isEnabled() { return enabled; }
    public String getPendingChannel() { return pendingChannel; }
    public String getAcceptedChannel() { return acceptedChannel; }
    public String getRejectedChannel() { return rejectedChannel; }
    public String getTicketCategory() { return ticketCategory; }
    public String getTicketNamePattern() { return ticketNamePattern; }
    public Set<String> getReviewerRoleIds() { return reviewerRoleIds; }
    public String getApprovedRoleId() { return approvedRoleId; }
    public boolean isPreventDuplicatePending() { return preventDuplicatePending; }
    public boolean isAllowReapplyAfterDecision() { return allowReapplyAfterDecision; }
    public boolean isDeleteTicketOnDecision() { return deleteTicketOnDecision; }
    public int getSessionExpiryMinutes() { return sessionExpiryMinutes; }
    public long getMaxUploadFileSizeBytes() { return maxUploadFileSizeBytes; }
    public long getMaxTotalUploadSizeBytes() { return maxTotalUploadSizeBytes; }
    public List<Question> getQuestions() { return questions; }
    public String getSubmittedMessage() { return submittedMessage; }
    public String getAcceptedMessage() { return acceptedMessage; }
    public String getRejectedMessage() { return rejectedMessage; }
    public String getTicketCreatedMessage() { return ticketCreatedMessage; }
    public String getTicketOpeningMessage() { return ticketOpeningMessage; }
}

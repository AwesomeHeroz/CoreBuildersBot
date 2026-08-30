package com.corebuilders.bot.discord;

import com.corebuilders.bot.application.ApplicationQuestionPages;
import com.corebuilders.bot.model.ApplicationReviewPolicy;
import com.corebuilders.bot.application.ApplicationSessionStore;
import com.corebuilders.bot.application.ApplicationSessionStore.Session;
import com.corebuilders.bot.config.ApplicationConfig;
import com.corebuilders.bot.config.ApplicationConfig.Question;
import com.corebuilders.bot.config.ApplicationConfig.QuestionType;
import com.corebuilders.bot.model.Domain.ApplicationStatus;
import com.corebuilders.bot.model.Models.ApplicationAnswer;
import com.corebuilders.bot.model.Models.ApplicationFile;
import com.corebuilders.bot.model.Models.ApplicationRecord;
import com.corebuilders.bot.service.ApplicationService;
import com.corebuilders.bot.util.ErrorMessages;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.attachmentupload.AttachmentUpload;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.modals.Modal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/** Coordinates Discord application interactions and delegates persistence, uploads,
 * message publishing, and ticket permissions to focused collaborators. */
public final class ApplicationDiscordListener extends ListenerAdapter implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(ApplicationDiscordListener.class);
    private static final int QUESTIONS_PER_MODAL = 5;

    private final ApplicationService applications;
    private final ApplicationConfig config;
    private final String configuredGuildId;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final ApplicationSessionStore<CollectedAnswer> sessionStore = new ApplicationSessionStore<>();
    private final ApplicationQuestionPages<Question> questionPages;
    private final DiscordResourceResolver resources = new DiscordResourceResolver();
    private final ApplicationTextFormatter textFormatter;
    private final ApplicationUploadPreserver uploadPreserver;
    private final ApplicationMessagePublisher messagePublisher;
    private final ApplicationTicketChannels ticketChannels = new ApplicationTicketChannels();
    private final ConcurrentHashMap<UUID, ReentrantLock> reviewLocks = new ConcurrentHashMap<>();

    public ApplicationDiscordListener(
            ApplicationService applications,
            ApplicationConfig config,
            String configuredGuildId
    ) {
        this.applications = applications;
        this.config = config;
        this.configuredGuildId = configuredGuildId == null ? "" : configuredGuildId.trim();
        this.questionPages = new ApplicationQuestionPages<>(config.getQuestions(), QUESTIONS_PER_MODAL);
        this.textFormatter = new ApplicationTextFormatter(config.getTicketNamePattern());
        this.uploadPreserver = new ApplicationUploadPreserver(
                config.getMaxUploadFileSizeBytes(),
                config.getMaxTotalUploadSizeBytes(),
                textFormatter
        );
        this.messagePublisher = new ApplicationMessagePublisher(textFormatter);
    }


    public Set<String> handledCommandNames() {
        return Set.of("apply", "application");
    }

    /**
     * Validates configured Discord resources early so bad IDs/names are visible in startup logs.
     *
     * Misconfigured application resources intentionally do not stop the rest of CoreBot from
     * starting or registering slash commands. The specific /apply or review action will still
     * fail safely with a clear error until the YAML references are corrected.
     */
    public void validateConfiguration(JDA jda) {
        if (!config.isEnabled()) {
            log.info("Discord application workflow is disabled.");
            return;
        }
        List<Guild> guilds = targetGuilds(jda);
        if (guilds.isEmpty()) {
            log.warn("Applications are enabled but CoreBot is not connected to a target Discord guild.");
            return;
        }
        for (Guild guild : guilds) {
            List<String> problems = new ArrayList<>();
            validateResource(problems, () -> resources.requireTextChannel(
                    guild, config.getPendingChannel(), "applications.channels.pending"
            ));
            validateResource(problems, () -> resources.requireTextChannel(
                    guild, config.getAcceptedChannel(), "applications.channels.accepted"
            ));
            validateResource(problems, () -> resources.requireTextChannel(
                    guild, config.getRejectedChannel(), "applications.channels.rejected"
            ));
            validateResource(problems, () -> resources.requireCategory(
                    guild, config.getTicketCategory(), "applications.tickets.category"
            ));
            if (resources.roles(guild, config.getRecruiterRoleIds()).isEmpty()) {
                problems.add("None of applications.recruiter-role-ids could be resolved: " + config.getRecruiterRoleIds());
            }
            if (resources.roles(guild, config.getLeaderRoleIds()).isEmpty()) {
                problems.add("None of discord.permissions.leadership-role-ids could be resolved: " + config.getLeaderRoleIds());
            }
            validateResource(problems, () -> resources.requireRole(
                    guild, config.getApprovedRoleId(), "applications.approval.role-id"
            ));

            if (problems.isEmpty()) {
                reconcileActiveReviewMessages(guild);
                log.info(
                        "Application workflow ready in guild '{}': pending='{}', accepted='{}', rejected='{}', ticket category='{}'.",
                        guild.getName(), config.getPendingChannel(), config.getAcceptedChannel(),
                        config.getRejectedChannel(), config.getTicketCategory()
                );
            } else {
                log.warn(
                        "Application workflow has {} configuration problem(s) in guild '{}'. Other CoreBot commands will still start: {}",
                        problems.size(), guild.getName(), String.join(" | ", problems)
                );
            }
        }
    }

    private void reconcileActiveReviewMessages(Guild guild) {
        try {
            for (ApplicationRecord application : applications.activeForReview()) {
                String title = application.status() == ApplicationStatus.PENDING
                        ? "Core Builders Application — Recruiter Review"
                        : "Core Builders Application — Leader Review";
                messagePublisher.updatePendingStage(guild, application, title);
            }
        } catch (RuntimeException error) {
            log.warn("Could not reconcile application review buttons during startup.", error);
        }
    }

    private static void validateResource(List<String> problems, Runnable lookup) {
        try {
            lookup.run();
        } catch (RuntimeException error) {
            problems.add(safeMessage(error));
        }
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!("apply".equals(event.getName()) || "application".equals(event.getName()))) return;
        if (!config.isEnabled()) {
            event.reply("Applications are currently disabled.").setEphemeral(true).queue();
            return;
        }
        if (!event.isFromGuild() || event.getGuild() == null || !isAllowedGuild(event.getGuild())) {
            event.reply("Applications can only be used in the configured Core Builders Discord server.")
                    .setEphemeral(true).queue();
            return;
        }

        try {
            if ("apply".equals(event.getName())) {
                startApplication(event);
            } else if ("status".equals(event.getSubcommandName())) {
                applicationStatus(event);
            } else {
                event.reply("Unknown application command.").setEphemeral(true).queue();
            }
        } catch (Exception error) {
            replyError(event, error);
        }
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String id = event.getComponentId();
        if (id == null || !id.startsWith("app:")) return;
        if (!config.isEnabled()) {
            event.reply("Applications are currently disabled.").setEphemeral(true).queue();
            return;
        }
        if (!event.isFromGuild() || event.getGuild() == null || !isAllowedGuild(event.getGuild())) {
            event.reply("This application action cannot be used here.").setEphemeral(true).queue();
            return;
        }

        try {
            String[] parts = id.split(":", 4);
            String action = parts.length > 1 ? parts[1] : "";
            switch (action) {
                case "apply" -> startApplication(event);
                case "continue" -> continueApplication(event, parts);
                case "cancel" -> cancelApplication(event, parts);
                case "recruiter-approve", "approve" -> approveFirstLevelApplication(event, parts);
                case "recruiter-reject", "reject" -> openRejectModal(event, parts, ApplicationStatus.PENDING);
                case "leader-approve" -> approveFinalApplication(event, parts);
                case "leader-reject" -> openRejectModal(event, parts, ApplicationStatus.LEADER_REVIEW);
                case "ticket" -> createDiscussionTicket(event, parts);
                default -> event.reply("Unknown application action.").setEphemeral(true).queue();
            }
        } catch (Exception error) {
            replyError(event, error);
        }
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        String id = event.getModalId();
        if (id == null || !id.startsWith("app:")) return;
        if (!config.isEnabled()) {
            event.reply("Applications are currently disabled.").setEphemeral(true).queue();
            return;
        }
        if (!event.isFromGuild() || event.getGuild() == null || !isAllowedGuild(event.getGuild())) {
            event.reply("This application form cannot be used here.").setEphemeral(true).queue();
            return;
        }

        try {
            String[] parts = id.split(":", 4);
            if (parts.length < 3) throw new IllegalArgumentException("Invalid application modal.");
            if ("form".equals(parts[1])) {
                handleFormPage(event, parts);
            } else if ("reject-recruiter".equals(parts[1]) || "reject".equals(parts[1])) {
                handleRejectModal(event, parts, ApplicationStatus.PENDING);
            } else if ("reject-leader".equals(parts[1])) {
                handleRejectModal(event, parts, ApplicationStatus.LEADER_REVIEW);
            }
        } catch (Exception error) {
            replyError(event, error);
        }
    }

    private void startApplication(ButtonInteractionEvent event) {
        event.replyModal(buildApplicationModal(createApplicationSession(event.getUser()), 0)).queue();
    }

    private void startApplication(SlashCommandInteractionEvent event) {
        event.replyModal(buildApplicationModal(createApplicationSession(event.getUser()), 0)).queue();
    }

    private Session<CollectedAnswer> createApplicationSession(User user) {
        String userId = user.getId();
        sessionStore.cleanupExpired();

        if (config.isPreventDuplicatePending() && applications.pendingForUser(userId).isPresent()) {
            throw new IllegalStateException(
                    "You already have a pending application. Use `/application status` to check it."
            );
        }
        if (!config.isAllowReapplyAfterDecision() && applications.latestForUser(userId).isPresent()) {
            throw new IllegalStateException("You have already submitted an application and re-applications are disabled.");
        }

        return sessionStore.create(
                userId,
                user.getName(),
                Duration.ofMinutes(config.getSessionExpiryMinutes())
        );
    }

    private void applicationStatus(SlashCommandInteractionEvent event) {
        Optional<ApplicationRecord> latest = applications.latestForUser(event.getUser().getId());
        if (latest.isEmpty()) {
            event.reply("You have not submitted an application yet. Use `/apply` to begin.")
                    .setEphemeral(true).queue();
            return;
        }
        ApplicationRecord application = latest.get();
        event.replyEmbeds(messagePublisher.header(application, "Your Core Builders Application"))
                .setEphemeral(true)
                .queue();
    }

    private Modal buildApplicationModal(Session<CollectedAnswer> session, int page) {
        List<Question> questions = pageQuestions(page);
        int pages = totalPages();
        Modal.Builder modal = Modal.create(
                "app:form:" + session.id() + ":" + page,
                truncate("Core Builders Application " + (page + 1) + "/" + pages, 45)
        );

        for (Question question : questions) {
            String customId = "q:" + question.id();
            if (question.type() == QuestionType.TEXT) {
                TextInput input = TextInput.create(
                                customId,
                                question.style() == ApplicationConfig.TextStyle.SHORT
                                        ? TextInputStyle.SHORT
                                        : TextInputStyle.PARAGRAPH
                        )
                        .setRequired(question.required())
                        .setMinLength(question.minLength())
                        .setMaxLength(question.maxLength())
                        .setPlaceholder(textFormatter.blankToNull(truncate(question.placeholder(), 100)))
                        .build();
                modal.addComponents(label(question, input));
            } else {
                AttachmentUpload upload = AttachmentUpload.create(customId)
                        .setRequired(question.required())
                        .setMinValues(question.minFiles())
                        .setMaxValues(question.maxFiles())
                        .build();
                modal.addComponents(label(question, upload));
            }
        }
        return modal.build();
    }

    private static Label label(Question question, net.dv8tion.jda.api.components.label.LabelChildComponent child) {
        String label = truncate(question.label(), 45);
        String description = truncate(question.description(), 100);
        return description.isBlank() ? Label.of(label, child) : Label.of(label, description, child);
    }

    private void handleFormPage(ModalInteractionEvent event, String[] parts) {
        if (parts.length != 4) throw new IllegalArgumentException("Invalid application form page.");
        UUID sessionId = parseUuid(parts[2], "application session");
        int page = parseInt(parts[3], "application page");
        Session<CollectedAnswer> session = sessionStore.findValid(sessionId, event.getUser().getId())
                .orElseThrow(() -> new IllegalStateException("This application session expired. Run `/apply` again."));

        for (Question question : pageQuestions(page)) {
            ModalMapping mapping = event.getValue("q:" + question.id());
            if (question.type() == QuestionType.TEXT) {
                String text = mapping == null ? "" : nullToEmpty(mapping.getAsOptionalString()).trim();
                session.values().put(question.id(), new CollectedAnswer(question, text, List.of()));
            } else {
                List<Message.Attachment> files = mapping == null ? List.of() : List.copyOf(mapping.getAsAttachmentList());
                session.values().put(question.id(), new CollectedAnswer(question, "", files));
            }
        }

        int nextPage = page + 1;
        if (nextPage < totalPages()) {
            event.reply("Application page **" + (page + 1) + "/" + totalPages()
                            + "** saved. Continue to the next page.")
                    .addComponents(ActionRow.of(
                            Button.primary("app:continue:" + sessionId + ":" + nextPage, "Continue"),
                            Button.danger("app:cancel:" + sessionId, "Cancel application")
                    ))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        event.deferReply(true).queue(hook -> executor.submit(() -> {
            try {
                ApplicationRecord application = finalizeApplication(event.getGuild(), event.getUser(), session);
                sessionStore.remove(session);
                String message = formatMessage(config.getSubmittedMessage(), application, null, null, null);
                hook.editOriginal(message + "\nApplication ID: `" + application.id() + "`").queue();
            } catch (Exception error) {
                hook.editOriginal("❌ " + safeMessage(error)).queue();
            }
        }));
    }

    private void continueApplication(ButtonInteractionEvent event, String[] parts) {
        if (parts.length != 4) throw new IllegalArgumentException("Invalid continue action.");
        UUID sessionId = parseUuid(parts[2], "application session");
        int page = parseInt(parts[3], "application page");
        Session<CollectedAnswer> session = sessionStore.findValid(sessionId, event.getUser().getId())
                .orElseThrow(() -> new IllegalStateException("This application session expired. Run `/apply` again."));
        event.replyModal(buildApplicationModal(session, page)).queue();
    }

    private void cancelApplication(ButtonInteractionEvent event, String[] parts) {
        if (parts.length < 3) throw new IllegalArgumentException("Invalid cancel action.");
        UUID sessionId = parseUuid(parts[2], "application session");
        boolean cancelled = sessionStore.removeIfValid(sessionId, event.getUser().getId());
        String message = cancelled
                ? "Application cancelled."
                : "This application form was already cancelled or expired.";
        event.editMessage(message).setComponents(List.of()).queue();
    }

    private ApplicationRecord finalizeApplication(Guild guild, User user, Session<CollectedAnswer> session) throws Exception {
        if (config.isPreventDuplicatePending() && applications.pendingForUser(user.getId()).isPresent()) {
            throw new IllegalStateException("You already have a pending application.");
        }
        if (!config.isAllowReapplyAfterDecision() && applications.latestForUser(user.getId()).isPresent()) {
            throw new IllegalStateException("Re-applications are disabled.");
        }

        TextChannel pending = resources.requireTextChannel(guild, config.getPendingChannel(), "applications.channels.pending");
        long declaredUploadBytes = session.values().values().stream()
                .filter(java.util.Objects::nonNull)
                .flatMap(value -> value.attachments.stream())
                .mapToLong(Message.Attachment::getSize)
                .sum();
        if (declaredUploadBytes > config.getMaxTotalUploadSizeBytes()) {
            throw new IllegalArgumentException(
                    "Application uploads exceed the configured total limit of "
                            + textFormatter.humanBytes(config.getMaxTotalUploadSizeBytes()) + "."
            );
        }
        List<ApplicationAnswer> answers = new ArrayList<>();
        List<ApplicationFile> preservedFiles = new ArrayList<>();
        boolean persisted = false;
        try {
            for (Question question : config.getQuestions()) {
                CollectedAnswer collected = session.values().get(question.id());
                if (question.type() == QuestionType.TEXT) {
                    String text = collected == null ? "" : collected.text;
                    answers.add(new ApplicationAnswer(question.id(), question.label(), "TEXT", text, List.of()));
                } else {
                    List<Message.Attachment> sourceFiles = collected == null ? List.of() : collected.attachments;
                    List<ApplicationFile> savedFiles = uploadPreserver.preserve(pending, session.id(), question, sourceFiles);
                    preservedFiles.addAll(savedFiles);
                    answers.add(new ApplicationAnswer(question.id(), question.label(), "UPLOAD", "", savedFiles));
                }
            }

            ApplicationRecord application = applications.create(session.id(), user.getId(), user.getName(), answers);
            persisted = true;
            Message main = messagePublisher.publishPending(pending, application);
            application = applications.setPendingMessage(application.id(), pending.getId(), main.getId());
            messagePublisher.sendAnswers(pending, application);
            return application;
        } catch (Exception error) {
            if (!persisted) cleanupPreservedEvidence(pending, preservedFiles);
            throw error;
        }
    }

    private void cleanupPreservedEvidence(TextChannel pending, List<ApplicationFile> files) {
        files.stream()
                .map(ApplicationFile::messageId)
                .filter(java.util.Objects::nonNull)
                .filter(id -> !id.isBlank())
                .distinct()
                .forEach(messageId -> {
                    try {
                        pending.deleteMessageById(messageId).complete();
                    } catch (Exception cleanupError) {
                        log.warn("Could not clean up orphaned application evidence message {}.", messageId, cleanupError);
                    }
                });
    }

    private void approveFirstLevelApplication(ButtonInteractionEvent event, String[] parts) {
        if (parts.length < 3) throw new IllegalArgumentException("Invalid recruiter approval action.");
        requireRecruiter(event.getMember());
        UUID applicationId = parseUuid(parts[2], "application ID");

        event.deferReply(true).queue(hook -> executor.submit(() -> {
            try {
                Guild guild = event.getGuild();
                ApplicationRecord advanced = applications.approveFirstLevel(applicationId, event.getUser().getId());

                messagePublisher.updatePendingStage(
                        guild, advanced, "Application Passed Recruiter Review — Awaiting Leader Approval"
                );
                notifyApplicant(
                        guild.getJDA(),
                        advanced.discordUserId(),
                        formatMessage(config.getFirstLevelAcceptedMessage(), advanced, event.getUser(), null, null)
                );
                notifyTicketProgress(
                        guild, advanced,
                        "✅ This application passed **recruiter review** by " + event.getUser().getAsMention()
                                + " and is now waiting for final leader approval."
                );
                hook.editOriginal("✅ Recruiter review accepted. The application is now waiting for final leader approval.").queue();
            } catch (Exception error) {
                hook.editOriginal("❌ " + safeMessage(error)).queue();
            }
        }));
    }

    private void approveFinalApplication(ButtonInteractionEvent event, String[] parts) {
        if (parts.length < 3) throw new IllegalArgumentException("Invalid final approval action.");
        requireLeader(event.getMember());
        UUID applicationId = parseUuid(parts[2], "application ID");

        event.deferReply(true).queue(hook -> executor.submit(() -> {
            ReentrantLock lock = reviewLocks.computeIfAbsent(applicationId, ignored -> new ReentrantLock());
            lock.lock();
            try {
                Guild guild = event.getGuild();
                ApplicationRecord pending = applications.get(applicationId);
                ApplicationReviewPolicy.requireLeaderReview(pending.status());

                Member applicant = guild.retrieveMemberById(pending.discordUserId()).complete();
                Role approvedRole = resources.requireRole(guild, config.getApprovedRoleId(), "applications.approval.role-id");
                boolean alreadyHadApprovedRole = applicant.getRoles().stream()
                        .anyMatch(role -> role.getId().equals(approvedRole.getId()));
                if (!alreadyHadApprovedRole) {
                    guild.addRoleToMember(applicant, approvedRole).complete();
                }

                ApplicationRecord accepted;
                try {
                    accepted = applications.approveFinal(
                            applicationId,
                            event.getUser().getId(),
                            "Final approval by leader after recruiter acceptance."
                    );
                } catch (Exception databaseError) {
                    // If another bot instance completed the same final approval, do not remove a valid member role.
                    ApplicationRecord latest;
                    try {
                        latest = applications.get(applicationId);
                    } catch (Exception verificationError) {
                        databaseError.addSuppressed(verificationError);
                        throw new IllegalStateException(
                                "The final database update failed and the saved application state could not be verified. "
                                        + "The member role was left unchanged; verify the application status before retrying.",
                                databaseError
                        );
                    }
                    if (latest.status() == ApplicationStatus.ACCEPTED) {
                        hook.editOriginal("✅ This application was already finally accepted. The member role is present.").queue();
                        return;
                    }
                    if (!alreadyHadApprovedRole) {
                        try {
                            guild.removeRoleFromMember(applicant, approvedRole).complete();
                        } catch (Exception rollbackError) {
                            databaseError.addSuppressed(rollbackError);
                        }
                    }
                    throw databaseError;
                }

                String postDecisionWarning = null;
                try {
                    TextChannel acceptedChannel = resources.requireTextChannel(
                            guild, config.getAcceptedChannel(), "applications.channels.accepted"
                    );
                    messagePublisher.sendPacket(acceptedChannel, accepted, "Accepted Core Builders Application");
                    messagePublisher.updatePendingDecision(guild, accepted, "Application Accepted — Final Leader Approval");
                    notifyApplicant(
                            guild.getJDA(),
                            accepted.discordUserId(),
                            formatMessage(config.getAcceptedMessage(), accepted, event.getUser(), approvedRole, null)
                    );
                    notifyTicketDecision(
                            guild, accepted,
                            "✅ This application received **final leader approval** from " + event.getUser().getAsMention() + "."
                    );
                } catch (Exception postDecisionError) {
                    postDecisionWarning = safeMessage(postDecisionError);
                    log.warn("Application {} was accepted, but a post-decision Discord update failed.",
                            accepted.id(), postDecisionError);
                }

                String roleResult = alreadyHadApprovedRole
                        ? "already had role **" + approvedRole.getName() + "**"
                        : "received role **" + approvedRole.getName() + "**";
                String result = "✅ Final approval complete. <@" + accepted.discordUserId() + "> " + roleResult + ".";
                if (postDecisionWarning != null) {
                    result += "\n⚠️ The decision is saved, but a notification/channel update failed: " + postDecisionWarning;
                }
                hook.editOriginal(result).queue();
            } catch (Exception error) {
                hook.editOriginal("❌ " + safeMessage(error)).queue();
            } finally {
                lock.unlock();
            }
        }));
    }

    private void openRejectModal(
            ButtonInteractionEvent event,
            String[] parts,
            ApplicationStatus reviewStage
    ) {
        if (parts.length < 3) throw new IllegalArgumentException("Invalid rejection action.");
        requireRoleForStage(event.getMember(), reviewStage);
        UUID applicationId = parseUuid(parts[2], "application ID");
        requireReviewStage(applications.get(applicationId), reviewStage);

        TextInput reason = TextInput.create("reason", TextInputStyle.PARAGRAPH)
                .setRequired(true)
                .setMinLength(2)
                .setMaxLength(1000)
                .setPlaceholder("Explain why the application is being rejected.")
                .build();
        String modalAction = reviewStage == ApplicationStatus.PENDING ? "reject-recruiter" : "reject-leader";
        String title = reviewStage == ApplicationStatus.PENDING ? "Recruiter Reject Application" : "Leader Reject Application";
        Modal modal = Modal.create("app:" + modalAction + ":" + applicationId, title)
                .addComponents(Label.of("Rejection reason", reason))
                .build();
        event.replyModal(modal).queue();
    }

    private void handleRejectModal(
            ModalInteractionEvent event,
            String[] parts,
            ApplicationStatus reviewStage
    ) {
        if (parts.length < 3) throw new IllegalArgumentException("Invalid rejection modal.");
        requireRoleForStage(event.getMember(), reviewStage);
        UUID applicationId = parseUuid(parts[2], "application ID");
        String reason = Optional.ofNullable(event.getValue("reason"))
                .map(ModalMapping::getAsOptionalString)
                .orElse("");
        if (reason.isBlank()) throw new IllegalArgumentException("A rejection reason is required.");

        event.deferReply(true).queue(hook -> executor.submit(() -> {
            try {
                Guild guild = event.getGuild();
                ApplicationRecord rejected = reviewStage == ApplicationStatus.PENDING
                        ? applications.rejectFirstLevel(applicationId, event.getUser().getId(), reason)
                        : applications.rejectFinalLevel(applicationId, event.getUser().getId(), reason);

                String reviewLabel = reviewStage == ApplicationStatus.PENDING ? "Recruiter" : "Leader";
                String postDecisionWarning = null;
                try {
                    TextChannel rejectedChannel = resources.requireTextChannel(
                            guild, config.getRejectedChannel(), "applications.channels.rejected"
                    );
                    messagePublisher.sendPacket(rejectedChannel, rejected,
                            "Rejected Core Builders Application — " + reviewLabel + " Review");
                    messagePublisher.updatePendingDecision(guild, rejected, "Application Rejected — " + reviewLabel + " Review");
                    notifyApplicant(
                            guild.getJDA(),
                            rejected.discordUserId(),
                            formatMessage(config.getRejectedMessage(), rejected, event.getUser(), null, reason)
                    );
                    notifyTicketDecision(
                            guild, rejected,
                            "❌ This application was **rejected during " + reviewLabel.toLowerCase(Locale.ROOT) + " review** by "
                                    + event.getUser().getAsMention() + ".\n**Reason:** " + reason
                    );
                } catch (Exception postDecisionError) {
                    postDecisionWarning = safeMessage(postDecisionError);
                    log.warn("Application {} was rejected, but a post-decision Discord update failed.",
                            rejected.id(), postDecisionError);
                }

                String result = "Application rejected during " + reviewLabel.toLowerCase(Locale.ROOT) + " review.";
                if (postDecisionWarning == null) {
                    result += " The applicant has been notified.";
                } else {
                    result += "\n⚠️ The rejection is saved, but a notification/channel update failed: " + postDecisionWarning;
                }
                hook.editOriginal(result).queue();
            } catch (Exception error) {
                hook.editOriginal("❌ " + safeMessage(error)).queue();
            }
        }));
    }

    private void createDiscussionTicket(ButtonInteractionEvent event, String[] parts) {
        if (parts.length < 3) throw new IllegalArgumentException("Invalid discussion-ticket action.");
        requireAnyReviewer(event.getMember());
        UUID applicationId = parseUuid(parts[2], "application ID");

        event.deferReply(true).queue(hook -> executor.submit(() -> {
            try {
                Guild guild = event.getGuild();
                ApplicationRecord application = applications.get(applicationId);
                requireActive(application);

                if (application.ticketChannelId() != null && !application.ticketChannelId().isBlank()) {
                    TextChannel existing = guild.getTextChannelById(application.ticketChannelId());
                    if (existing != null) {
                        hook.editOriginal("A discussion ticket already exists: " + existing.getAsMention()).queue();
                        return;
                    }
                }

                Category category = resources.requireCategory(guild, config.getTicketCategory(), "applications.tickets.category");
                Member applicant = guild.retrieveMemberById(application.discordUserId()).complete();
                List<Role> reviewers = resources.roles(guild, config.getAllReviewRoleIds());
                if (reviewers.isEmpty()) {
                    throw new IllegalStateException("No configured reviewer roles could be found.");
                }

                String channelName = textFormatter.ticketName(application);
                TextChannel ticket = ticketChannels.createPrivate(
                        category, channelName, applicant, reviewers, guild.getSelfMember()
                );
                try {
                    application = applications.setTicketChannel(application.id(), ticket.getId(), event.getUser().getId());
                } catch (Exception setupError) {
                    try {
                        ticket.delete().complete();
                    } catch (Exception cleanupError) {
                        setupError.addSuppressed(cleanupError);
                    }
                    throw setupError;
                }

                String reviewerMentions = reviewers.stream().map(Role::getAsMention).collect(Collectors.joining(" "));
                ticket.sendMessage(
                                applicant.getAsMention() + " " + reviewerMentions + "\n"
                                        + formatMessage(config.getTicketOpeningMessage(), application, event.getUser(), null, null)
                        )
                        .complete();
                messagePublisher.sendPacket(ticket, application, "Application Discussion");

                messagePublisher.updatePendingTicket(guild, application);
                notifyApplicant(
                        guild.getJDA(),
                        application.discordUserId(),
                        formatMessage(config.getTicketCreatedMessage(), application, event.getUser(), null, null)
                );
                hook.editOriginal("Discussion ticket created: " + ticket.getAsMention()).queue();
            } catch (Exception error) {
                hook.editOriginal("❌ " + safeMessage(error)).queue();
            }
        }));
    }

    private void notifyTicketProgress(Guild guild, ApplicationRecord application, String message) {
        if (application.ticketChannelId() == null || application.ticketChannelId().isBlank()) return;
        TextChannel ticket = guild.getTextChannelById(application.ticketChannelId());
        if (ticket != null) ticket.sendMessage(message).queue();
    }

    private void notifyTicketDecision(Guild guild, ApplicationRecord application, String message) {
        if (application.ticketChannelId() == null || application.ticketChannelId().isBlank()) return;
        TextChannel ticket = guild.getTextChannelById(application.ticketChannelId());
        if (ticket == null) return;
        if (config.isDeleteTicketOnDecision()) {
            ticket.sendMessage(message + "\nThis ticket is configured to close after the decision.")
                    .queue(ignored -> ticket.delete().queue());
        } else {
            ticket.sendMessage(message).queue();
        }
    }

    private void requireRecruiter(Member member) {
        requireConfiguredRole(member, config.getRecruiterRoleIds(),
                "Only configured first-level recruiter roles may perform this action.");
    }

    private void requireLeader(Member member) {
        requireConfiguredRole(member, config.getLeaderRoleIds(),
                "Only configured leader-level roles may perform this final review action.");
    }

    private void requireAnyReviewer(Member member) {
        requireConfiguredRole(member, config.getAllReviewRoleIds(),
                "Only configured recruiter or leader review roles may perform this action.");
    }

    private void requireRoleForStage(Member member, ApplicationStatus reviewStage) {
        if (reviewStage == ApplicationStatus.PENDING) {
            requireRecruiter(member);
            return;
        }
        if (reviewStage == ApplicationStatus.LEADER_REVIEW) {
            requireLeader(member);
            return;
        }
        throw new IllegalArgumentException("Invalid application review stage.");
    }

    private static void requireConfiguredRole(Member member, Set<String> configured, String errorMessage) {
        if (member == null) throw new IllegalStateException("Could not resolve the reviewing Discord member.");
        boolean allowed = member.getRoles().stream().anyMatch(role -> configured.contains(role.getId()));
        if (!allowed) throw new SecurityException(errorMessage);
    }

    private List<Guild> targetGuilds(JDA jda) {
        if (!configuredGuildId.isBlank()) {
            Guild guild = jda.getGuildById(configuredGuildId);
            return guild == null ? List.of() : List.of(guild);
        }
        return jda.getGuilds();
    }

    private boolean isAllowedGuild(Guild guild) {
        return configuredGuildId.isBlank() || configuredGuildId.equals(guild.getId());
    }

    private String formatMessage(
            String template,
            ApplicationRecord application,
            User reviewer,
            Role role,
            String reason
    ) {
        return textFormatter.message(
                template,
                application,
                reviewer == null ? "" : reviewer.getAsMention(),
                role == null ? "" : role.getName(),
                reason
        );
    }

    private static void notifyApplicant(JDA jda, String userId, String message) {
        if (jda == null || userId == null || userId.isBlank() || message == null || message.isBlank()) return;

        User cached = jda.getUserById(userId);
        if (cached != null) {
            sendApplicantDm(cached, message);
            return;
        }

        jda.retrieveUserById(userId).queue(
                user -> sendApplicantDm(user, message),
                error -> log.warn("Could not resolve application user {} for DM: {}", userId, error.getMessage())
        );
    }

    private static void sendApplicantDm(User user, String message) {
        user.openPrivateChannel()
                .flatMap(channel -> channel.sendMessage(message))
                .queue(
                        ignored -> { },
                        error -> log.warn("Could not DM application update to {}: {}", user.getId(), error.getMessage())
                );
    }

    private List<Question> pageQuestions(int page) {
        return questionPages.page(page);
    }

    private int totalPages() {
        return questionPages.pageCount();
    }

    private static void requireActive(ApplicationRecord application) {
        if (!ApplicationReviewPolicy.isActive(application.status())) {
            throw new IllegalStateException("This application has already been "
                    + application.status().name().toLowerCase(Locale.ROOT) + ".");
        }
    }

    private static void requireReviewStage(ApplicationRecord application, ApplicationStatus reviewStage) {
        if (reviewStage == ApplicationStatus.PENDING) {
            ApplicationReviewPolicy.requireRecruiterReview(application.status());
        } else if (reviewStage == ApplicationStatus.LEADER_REVIEW) {
            ApplicationReviewPolicy.requireLeaderReview(application.status());
        } else {
            throw new IllegalArgumentException("Invalid application review stage.");
        }
    }

    private static UUID parseUuid(String value, String label) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Invalid " + label + ".");
        }
    }

    private static int parseInt(String value, String label) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Invalid " + label + ".");
        }
    }

    private static String safeMessage(Throwable error) {
        return ErrorMessages.safe(error);
    }

    private static String truncate(String value, int max) {
        String safe = nullToEmpty(value);
        return safe.length() <= max ? safe : safe.substring(0, Math.max(0, max - 1)) + "…";
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static void replyError(SlashCommandInteractionEvent event, Throwable error) {
        event.reply("❌ " + safeMessage(error)).setEphemeral(true).queue();
    }

    private static void replyError(ButtonInteractionEvent event, Throwable error) {
        event.reply("❌ " + safeMessage(error)).setEphemeral(true).queue();
    }

    private static void replyError(ModalInteractionEvent event, Throwable error) {
        event.reply("❌ " + safeMessage(error)).setEphemeral(true).queue();
    }

    @Override
    public void close() {
        executor.shutdownNow();
        sessionStore.clear();
        reviewLocks.clear();
    }

    private record CollectedAnswer(Question question, String text, List<Message.Attachment> attachments) {}
}

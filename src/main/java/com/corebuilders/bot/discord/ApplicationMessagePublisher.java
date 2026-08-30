package com.corebuilders.bot.discord;

import com.corebuilders.bot.model.ApplicationReviewPolicy;
import com.corebuilders.bot.model.Domain.ApplicationStatus;
import com.corebuilders.bot.model.Models.ApplicationAnswer;
import com.corebuilders.bot.model.Models.ApplicationRecord;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/** Builds and publishes application review messages. */
public final class ApplicationMessagePublisher {
    private static final Logger log = LoggerFactory.getLogger(ApplicationMessagePublisher.class);
    private final ApplicationTextFormatter formatter;

    public ApplicationMessagePublisher(ApplicationTextFormatter formatter) {
        this.formatter = formatter;
    }

    public Message publishPending(TextChannel channel, ApplicationRecord application) {
        return channel.sendMessageEmbeds(headerBuilder(application, "New Core Builders Application — Recruiter Review").build())
                .addComponents(ActionRow.of(reviewButtons(application)))
                .complete();
    }

    public void sendPacket(TextChannel channel, ApplicationRecord application, String title) {
        channel.sendMessageEmbeds(headerBuilder(application, title).build()).complete();
        sendAnswers(channel, application);
    }

    public void sendAnswers(TextChannel channel, ApplicationRecord application) {
        for (MessageEmbed embed : answerEmbeds(application)) {
            channel.sendMessageEmbeds(embed).complete();
        }
    }

    public void updatePendingTicket(Guild guild, ApplicationRecord application) {
        updatePending(guild, application, titleForStage(application, true), true);
    }

    public void updatePendingStage(Guild guild, ApplicationRecord application, String title) {
        updatePending(guild, application, title, true);
    }

    public void updatePendingDecision(Guild guild, ApplicationRecord application, String title) {
        updatePending(guild, application, title, false);
    }

    private void updatePending(Guild guild, ApplicationRecord application, String title, boolean keepReviewControls) {
        if (application.pendingChannelId() == null || application.pendingMessageId() == null) return;
        TextChannel channel = guild.getTextChannelById(application.pendingChannelId());
        if (channel == null) return;
        channel.retrieveMessageById(application.pendingMessageId()).queue(
                message -> {
                    var edit = message.editMessageEmbeds(headerBuilder(application, title).build());
                    if (keepReviewControls && ApplicationReviewPolicy.isActive(application.status())) {
                        edit.setComponents(List.of(ActionRow.of(reviewButtons(application)))).queue();
                    } else {
                        edit.setComponents(List.of()).queue();
                    }
                },
                error -> log.warn(
                        "Could not update pending application message {}: {}",
                        application.pendingMessageId(), error.getMessage()
                )
        );
    }

    public MessageEmbed header(ApplicationRecord application, String title) {
        return headerBuilder(application, title).build();
    }

    private List<MessageEmbed> answerEmbeds(ApplicationRecord application) {
        List<MessageEmbed> result = new ArrayList<>();
        List<ApplicationAnswer> answers = application.answers();
        for (int start = 0; start < answers.size(); start += 6) {
            EmbedBuilder embed = new EmbedBuilder().setTitle("Application Answers " + (start / 6 + 1));
            int end = Math.min(answers.size(), start + 6);
            for (int index = start; index < end; index++) {
                ApplicationAnswer answer = answers.get(index);
                String value = "UPLOAD".equalsIgnoreCase(answer.type())
                        ? formatter.formatFiles(answer.files())
                        : answer.text() == null || answer.text().isBlank() ? "—" : answer.text();
                embed.addField(formatter.truncate(answer.questionLabel(), 256), formatter.truncate(value, 1024), false);
            }
            embed.setFooter("Application ID: " + application.id());
            result.add(embed.build());
        }
        return result;
    }

    private EmbedBuilder headerBuilder(ApplicationRecord application, String title) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(title)
                .addField("Applicant", "<@" + application.discordUserId() + "> (`" + application.discordUserId() + "`)", false)
                .addField("Username", application.username(), true)
                .addField("Status", ApplicationReviewPolicy.displayStatus(application.status()), true)
                .addField("Submitted", formatter.timestamp(application.createdAt()), true)
                .setFooter("Application ID: " + application.id());

        if (application.firstReviewerDiscordId() != null && !application.firstReviewerDiscordId().isBlank()) {
            embed.addField("Recruiter review", "<@" + application.firstReviewerDiscordId() + ">", true);
        }
        if (application.firstReviewedAt() != null) {
            embed.addField("Recruiter reviewed", formatter.timestamp(application.firstReviewedAt()), true);
        }
        if (application.reviewerDiscordId() != null && !application.reviewerDiscordId().isBlank()) {
            embed.addField("Decision by", "<@" + application.reviewerDiscordId() + ">", true);
        }
        if (application.reviewedAt() != null) {
            embed.addField("Decision time", formatter.timestamp(application.reviewedAt()), true);
        }
        if (application.reviewReason() != null && !application.reviewReason().isBlank()) {
            embed.addField("Decision reason", formatter.truncate(application.reviewReason(), 1024), false);
        }
        if (application.ticketChannelId() != null && !application.ticketChannelId().isBlank()) {
            embed.addField("Discussion ticket", "<#" + application.ticketChannelId() + ">", false);
        }
        return embed;
    }

    static List<Button> reviewButtons(ApplicationRecord application) {
        String id = application.id().toString();
        if (application.status() == ApplicationStatus.PENDING) {
            return List.of(
                    Button.success("app:recruiter-approve:" + id, "Recruiter Accept"),
                    Button.danger("app:recruiter-reject:" + id, "Recruiter Reject"),
                    Button.primary("app:ticket:" + id, "Create Discussion Ticket")
            );
        }
        if (application.status() == ApplicationStatus.LEADER_REVIEW) {
            return List.of(
                    Button.success("app:leader-approve:" + id, "Final Approve"),
                    Button.danger("app:leader-reject:" + id, "Final Reject"),
                    Button.primary("app:ticket:" + id, "Create Discussion Ticket")
            );
        }
        return List.of();
    }

    private static String titleForStage(ApplicationRecord application, boolean discussionOpen) {
        String suffix = discussionOpen ? " — Discussion Open" : "";
        return switch (application.status()) {
            case PENDING -> "Core Builders Application — Recruiter Review" + suffix;
            case LEADER_REVIEW -> "Core Builders Application — Leader Review" + suffix;
            case ACCEPTED -> "Core Builders Application — Accepted";
            case REJECTED -> "Core Builders Application — Rejected";
        };
    }
}

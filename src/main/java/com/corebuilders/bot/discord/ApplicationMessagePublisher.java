package com.corebuilders.bot.discord;

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
import java.util.UUID;

/** Builds and publishes application review messages. */
public final class ApplicationMessagePublisher {
    private static final Logger log = LoggerFactory.getLogger(ApplicationMessagePublisher.class);
    private final ApplicationTextFormatter formatter;

    public ApplicationMessagePublisher(ApplicationTextFormatter formatter) {
        this.formatter = formatter;
    }

    public Message publishPending(TextChannel channel, ApplicationRecord application) {
        return channel.sendMessageEmbeds(headerBuilder(application, "New Core Builders Application").build())
                .addComponents(ActionRow.of(reviewButtons(application.id())))
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
        if (application.pendingChannelId() == null || application.pendingMessageId() == null) return;
        TextChannel channel = guild.getTextChannelById(application.pendingChannelId());
        if (channel == null) return;
        channel.retrieveMessageById(application.pendingMessageId()).queue(
                message -> message.editMessageEmbeds(
                                headerBuilder(application, "Core Builders Application — Discussion Open").build()
                        ).queue(),
                error -> log.warn(
                        "Could not update pending application message {} after ticket creation: {}",
                        application.pendingMessageId(), error.getMessage()
                )
        );
    }

    public void updatePendingDecision(Guild guild, ApplicationRecord application, String title) {
        if (application.pendingChannelId() == null || application.pendingMessageId() == null) return;
        TextChannel channel = guild.getTextChannelById(application.pendingChannelId());
        if (channel == null) return;
        channel.retrieveMessageById(application.pendingMessageId()).queue(message ->
                message.editMessageEmbeds(headerBuilder(application, title).build())
                        .setComponents(List.of())
                        .queue()
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
                .addField("Status", application.status().name(), true)
                .addField("Submitted", formatter.timestamp(application.createdAt()), true)
                .setFooter("Application ID: " + application.id());

        if (application.reviewerDiscordId() != null && !application.reviewerDiscordId().isBlank()) {
            embed.addField("Reviewed by", "<@" + application.reviewerDiscordId() + ">", true);
        }
        if (application.reviewedAt() != null) {
            embed.addField("Reviewed", formatter.timestamp(application.reviewedAt()), true);
        }
        if (application.reviewReason() != null && !application.reviewReason().isBlank()) {
            embed.addField("Decision reason", formatter.truncate(application.reviewReason(), 1024), false);
        }
        if (application.ticketChannelId() != null && !application.ticketChannelId().isBlank()) {
            embed.addField("Discussion ticket", "<#" + application.ticketChannelId() + ">", false);
        }
        return embed;
    }

    private static List<Button> reviewButtons(UUID applicationId) {
        String id = applicationId.toString();
        return List.of(
                Button.success("app:approve:" + id, "Approve"),
                Button.danger("app:reject:" + id, "Reject"),
                Button.primary("app:ticket:" + id, "Create Discussion Ticket")
        );
    }
}

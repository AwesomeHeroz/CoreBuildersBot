package com.corebuilders.bot.discord;

import com.corebuilders.bot.config.BotProperties;
import com.corebuilders.bot.model.Models.Achievement;
import com.corebuilders.bot.model.Models.Contribution;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.components.buttons.Button;

import javax.swing.*;
import java.util.List;

public final class DiscordNotifier {
    private final BotProperties properties;

    public DiscordNotifier(BotProperties properties) {
        this.properties = properties;
    }

    public void contributionPending(Guild guild, Contribution c) {
        channel(guild, properties.getStaffApprovalChannel()).ifPresent(ch ->
                ch.sendMessageEmbeds(new EmbedBuilder()
                                .setTitle("New Contribution Request")
                                .setDescription(c.description())
                                .addField("Member", "<@" + c.discordUserId() + ">", true)
                                .addField("Category", c.category().display(), true)
                                .addField("Suggested Reward",
                                        c.suggestedCxp() + " points • " + c.suggestedCredits() + " coins", true)
                                .addField("Project", value(c.projectName()), false)
                                .addField("Evidence", value(c.evidenceUrl()), false)
                                .setFooter("Contribution ID: " + c.id())
                                .build())
                        .addComponents(
                                ActionRow.of( Button.success("contrib:approve:" + c.id(), "Approve"),
                                        Button.danger("contrib:reject:" + c.id(), "Reject"))
                        )
                        .queue()
        );
    }

    public void contributionReviewed(Guild guild, Contribution c) {
        channel(guild, properties.getContributionLogChannel()).ifPresent(ch ->
                ch.sendMessageEmbeds(new EmbedBuilder()
                        .setTitle("Contribution " + c.status().name())
                        .addField("Member", "<@" + c.discordUserId() + ">", true)
                        .addField("Category", c.category().display(), true)
                        .addField("Reward",
                                valueNumber(c.awardedCxp()) + " points • " + valueNumber(c.awardedCredits()) + " coins", true)
                        .setDescription(c.description())
                        .setFooter("Contribution ID: " + c.id())
                        .build()).queue()
        );
    }

    public void achievements(Guild guild, String discordUserId, List<Achievement> achievements) {
        if (achievements.isEmpty()) return;
        channel(guild, properties.getAchievementChannel()).ifPresent(ch -> {
            for (Achievement achievement : achievements) {
                ch.sendMessageEmbeds(new EmbedBuilder()
                        .setTitle("🏆 Achievement Unlocked")
                        .setDescription("<@" + discordUserId + "> unlocked **" + achievement.name() + "**")
                        .addField("Requirement", achievement.description(), false)
                        .addField("Reward",
                                achievement.rewardCxp() + " points • " + achievement.rewardCredits() + " coins", false)
                        .build()).queue();
            }
        });
    }

    public void economyLog(Guild guild, String title, String description) {
        channel(guild, properties.getEconomyLogChannel()).ifPresent(ch ->
                ch.sendMessageEmbeds(new EmbedBuilder()
                        .setTitle(title)
                        .setDescription(description)
                        .build()).queue()
        );
    }

    private java.util.Optional<TextChannel> channel(Guild guild, String name) {
        if (guild == null || name == null || name.isBlank()) return java.util.Optional.empty();
        return guild.getTextChannelsByName(name, true).stream().findFirst();
    }

    private static String value(String s) { return s == null || s.isBlank() ? "—" : s; }
    private static long valueNumber(Long n) { return n == null ? 0 : n; }
}

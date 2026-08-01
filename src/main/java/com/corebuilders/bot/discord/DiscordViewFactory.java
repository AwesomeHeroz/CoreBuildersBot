package com.corebuilders.bot.discord;

import com.corebuilders.bot.model.Domain.TaskStatus;
import com.corebuilders.bot.model.Models.*;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

import java.util.List;
import java.util.stream.Collectors;

import static com.corebuilders.bot.discord.DiscordFormatting.*;

/** Stateless Discord embed builder for Core Builders domain results. */
public final class DiscordViewFactory {

    public MessageEmbed projectList(List<ProjectSummary> projects) {
        String body = projects.stream()
                .map(summary -> {
                    Project project = summary.project();
                    return "**" + project.name() + "** — `" + project.id() + "`\n"
                            + project.status() + " • Lead: <@" + project.leadDiscordId() + "> • "
                            + summary.memberCount() + " member(s)";
                })
                .collect(Collectors.joining("\n\n"));
        return new EmbedBuilder()
                .setTitle("Active Core Builders Projects")
                .setDescription(truncate(body.isBlank() ? "No active projects." : body, 4000))
                .build();
    }

    public MessageEmbed projectView(Project project, List<ProjectTask> tasks, int memberCount) {
        String taskText = tasks.isEmpty() ? "No tasks yet." : tasks.stream()
                .map(task -> (task.status() == TaskStatus.COMPLETED ? "✅" : "⬜")
                        + " `" + task.id() + "` **" + task.title() + "**"
                        + (task.assignedDiscordId() == null ? "" : " — <@" + task.assignedDiscordId() + ">")
                        + " (" + task.rewardCxp() + " points / " + task.rewardCredits() + " coins)")
                .collect(Collectors.joining("\n"));
        return new EmbedBuilder()
                .setTitle(project.name())
                .setDescription(project.description())
                .addField("Status", project.status().name(), true)
                .addField("Lead", "<@" + project.leadDiscordId() + ">", true)
                .addField("Members", String.valueOf(memberCount), true)
                .addField("Project ID", project.id().toString(), false)
                .addField("Tasks", truncate(taskText, 1024), false)
                .build();
    }

    public MessageEmbed project(Project project) {
        return new EmbedBuilder()
                .setTitle("Project — " + project.name())
                .setDescription(project.description())
                .addField("Status", project.status().name(), true)
                .addField("Lead", "<@" + project.leadDiscordId() + ">", true)
                .addField("Project ID", project.id().toString(), false)
                .build();
    }

    public MessageEmbed projectTask(ProjectTask task) {
        return new EmbedBuilder()
                .setTitle("Project Task Created")
                .setDescription(task.title())
                .addField("Task ID", task.id().toString(), false)
                .addField("Project ID", task.projectId().toString(), false)
                .addField("Assignee", task.assignedDiscordId() == null ? "Unassigned" : "<@" + task.assignedDiscordId() + ">", true)
                .addField("Reward", task.rewardCxp() + " points • " + task.rewardCredits() + " coins", true)
                .build();
    }

    public MessageEmbed missionList(List<MissionSummary> missions) {
        String body = missions.stream()
                .map(summary -> {
                    Mission mission = summary.mission();
                    return "**" + mission.name() + "** — `" + mission.id() + "`\n"
                            + mission.status() + " • " + summary.memberCount() + "/"
                            + (mission.maxSlots() == 0 ? "∞" : mission.maxSlots()) + " slots • "
                            + mission.rewardCxp() + " points / " + mission.rewardCredits() + " coins";
                })
                .collect(Collectors.joining("\n\n"));
        return new EmbedBuilder()
                .setTitle("Active Core Builders Missions")
                .setDescription(truncate(body.isBlank() ? "No active missions." : body, 4000))
                .build();
    }

    public MessageEmbed missionView(Mission mission, List<Member> participants) {
        String people = participants.isEmpty() ? "No participants yet."
                : participants.stream().map(member -> "<@" + member.discordUserId() + ">").collect(Collectors.joining(", "));
        return new EmbedBuilder()
                .setTitle(mission.name())
                .setDescription(mission.description())
                .addField("Status", mission.status().name(), true)
                .addField("Reward", mission.rewardCxp() + " points • " + mission.rewardCredits() + " coins", true)
                .addField("Slots", participants.size() + " / " + (mission.maxSlots() == 0 ? "∞" : mission.maxSlots()), true)
                .addField("Deadline", mission.deadline() == null ? "None" : mission.deadline().toString(), false)
                .addField("Participants", truncate(people, 1024), false)
                .addField("Mission ID", mission.id().toString(), false)
                .build();
    }

    public MessageEmbed mission(Mission mission) {
        return new EmbedBuilder()
                .setTitle("Mission — " + mission.name())
                .setDescription(mission.description())
                .addField("Reward", mission.rewardCxp() + " points • " + mission.rewardCredits() + " coins", true)
                .addField("Slots", mission.maxSlots() == 0 ? "Unlimited" : String.valueOf(mission.maxSlots()), true)
                .addField("Deadline", mission.deadline() == null ? "None" : mission.deadline().toString(), false)
                .addField("Mission ID", mission.id().toString(), false)
                .build();
    }

    public MessageEmbed pendingContributions(List<Contribution> contributions) {
        String body = contributions.stream()
                .map(contribution -> "`" + contribution.id() + "`\n<@" + contribution.discordUserId() + "> • **"
                        + contribution.category().display() + "** • " + contribution.suggestedCxp() + " points / "
                        + contribution.suggestedCredits() + " coins\n" + truncate(contribution.description(), 180))
                .collect(Collectors.joining("\n\n"));
        return new EmbedBuilder()
                .setTitle("Pending Contributions")
                .setDescription(truncate(body.isBlank() ? "No pending contributions." : body, 4000))
                .build();
    }

    public MessageEmbed contributionReviewed(Contribution contribution) {
        return new EmbedBuilder()
                .setTitle("Contribution " + contribution.status().name())
                .setDescription(contribution.description())
                .addField("Member", "<@" + contribution.discordUserId() + ">", true)
                .addField("Category", contribution.category().display(), true)
                .addField("Award", number(contribution.awardedCxp()) + " points • "
                        + number(contribution.awardedCredits()) + " coins", true)
                .setFooter("Contribution ID: " + contribution.id())
                .build();
    }

    public MessageEmbed order(String title, ShopOrder order) {
        return new EmbedBuilder()
                .setTitle(title)
                .addField("Item", order.itemName(), true)
                .addField("Price", formatNumber(order.price()) + " coins", true)
                .addField("Status", order.status().name(), true)
                .addField("Order ID", order.id().toString(), false)
                .addField("Note", value(order.fulfillmentNote()), false)
                .build();
    }

    public MessageEmbed orders(String title, List<ShopOrder> orders) {
        String body = orders.stream()
                .map(order -> "`" + order.id() + "`\n<@" + order.discordUserId() + "> • **"
                        + order.itemName() + "** • " + order.price() + " coins • " + order.status())
                .collect(Collectors.joining("\n\n"));
        return new EmbedBuilder()
                .setTitle(title)
                .setDescription(truncate(body.isBlank() ? "No orders found." : body, 4000))
                .build();
    }

    public MessageEmbed simple(String title, String description) {
        return new EmbedBuilder().setTitle(title).setDescription(description).build();
    }

    public record ProjectSummary(Project project, int memberCount) {}
    public record MissionSummary(Mission mission, int memberCount) {}
}

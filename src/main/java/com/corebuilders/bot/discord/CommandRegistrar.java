package com.corebuilders.bot.discord;

import com.corebuilders.bot.config.BotProperties;
import com.corebuilders.bot.discord.command.CommandCoverage;
import com.corebuilders.bot.model.Domain.ContributionCategory;
import com.corebuilders.bot.model.Domain.Reputation;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class CommandRegistrar {
    private static final Logger log = LoggerFactory.getLogger(CommandRegistrar.class);
    private final JDA jda;
    private final BotProperties properties;
    private final Set<String> handledCommandNames;

    public CommandRegistrar(JDA jda, BotProperties properties, Set<String> handledCommandNames) {
        this.jda = jda;
        this.properties = properties;
        this.handledCommandNames = Set.copyOf(handledCommandNames);
    }

    /** Registers commands synchronously in the one configured Core Builders guild. */
    public void registerCommands() {
        List<CommandData> commands = commands();
        verifyHandlerCoverage(commands);

        String guildId = properties.getGuildId();
        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            String connectedGuilds = jda.getGuilds().stream()
                    .map(g -> g.getName() + " (" + g.getId() + ")")
                    .toList()
                    .toString();
            throw new IllegalStateException(
                    "Configured Discord guild ID " + guildId
                            + " is not accessible by the bot. Connected guilds: " + connectedGuilds
            );
        }
        registerGuildCommands(guild, commands);
    }

    private void verifyHandlerCoverage(List<CommandData> commands) {
        Set<String> registeredNames = commands.stream()
                .map(CommandData::getName)
                .collect(java.util.stream.Collectors.toSet());
        CommandCoverage.requireExact(registeredNames, handledCommandNames);
    }

    private void registerGuildCommands(Guild guild, List<CommandData> commands) {
        log.info(
                "Registering {} Discord slash-command roots in guild '{}' ({}).",
                commands.size(), guild.getName(), guild.getId()
        );

        List<Command> registered = guild.updateCommands().addCommands(commands).complete();
        verifyCoreCommand("guild '" + guild.getName() + "' (" + guild.getId() + ")", registered, commands.size());
    }

    private void verifyCoreCommand(String target, List<Command> registered, int expectedCount) {
        boolean hasCore = registered.stream().anyMatch(command -> "core".equals(command.getName()));
        if (!hasCore) {
            throw new IllegalStateException(
                    "Discord command registration completed for " + target
                            + " but /core was not returned by Discord."
            );
        }

        String names = registered.stream()
                .map(Command::getName)
                .sorted()
                .toList()
                .toString();
        log.info(
                "Discord command registration verified for {}: {}/{} command roots registered. Commands: {}",
                target, registered.size(), expectedCount, names
        );
    }

    private List<CommandData> commands() {
        List<CommandData> commands = new ArrayList<>();

        commands.add(Commands.slash("apply", "Apply to join Core Builders using the configured application form."));
        commands.add(Commands.slash("application", "Check your Core Builders membership application.")
                .addSubcommands(new SubcommandData("status", "Show the status of your latest application.")));

        // Unified Discord entry point matching the Minecraft /core command.
        // Existing top-level slash commands remain registered for compatibility.
        commands.add(Commands.slash("core", "Core Builders member commands on Discord.")
                .addSubcommands(
                        new SubcommandData("profile", "Show a Core Builders member profile.")
                                .addOption(OptionType.USER, "member", "Member to view.", false),
                        new SubcommandData("balance", "Show your coin balance.")
                                .addOption(OptionType.USER, "member", "Member to view; staff only for other users.", false),
                        new SubcommandData("leaderboard", "Show Core Builders rankings.")
                                .addOptions(leaderboardTypeOption()),
                        new SubcommandData("achievements", "Show unlocked achievements.")
                                .addOption(OptionType.USER, "member", "Member to view.", false),
                        new SubcommandData("link", "Generate a one-time code to link your Minecraft account."),
                        new SubcommandData("web-login", "Verify a one-time website login code through Discord.")
                                .addOption(OptionType.STRING, "code", "Code shown on the Core Builders website.", true),
                        new SubcommandData("transactions", "Show recent points and coin transactions.")
                                .addOption(OptionType.USER, "member", "Member to view; staff only for other users.", false)
                                .addOption(OptionType.INTEGER, "limit", "Number of transactions, 1-25.", false),
                        new SubcommandData("stats", "Show overall Core Builders progression statistics."),
                        new SubcommandData("newplayers", "Fetch new/recent player data for a supported Minecraft server.")
                                .addOption(OptionType.STRING, "server", "Server name, for example 2b2t.", true)
                                .addOption(OptionType.INTEGER, "size", "Players per API page, 1-50. Default: 5.", false)
                                .addOption(OptionType.INTEGER, "page", "API page number, starting at 1. Default: 1.", false)
                ));

        commands.add(Commands.slash("music", "Join a voice channel and play music.")
                .addSubcommands(
                        new SubcommandData("join", "Join your current voice channel."),
                        new SubcommandData("play", "Play a YouTube search or supported HTTPS music URL.")
                                .addOption(OptionType.STRING, "query", "Search text or supported music URL.", true),
                        new SubcommandData("queue", "Show the current music queue."),
                        new SubcommandData("nowplaying", "Show the current track."),
                        new SubcommandData("skip", "Skip the current track."),
                        new SubcommandData("pause", "Pause playback."),
                        new SubcommandData("resume", "Resume playback."),
                        new SubcommandData("stop", "Stop playback and clear the queue."),
                        new SubcommandData("leave", "Leave voice and clear the queue."),
                        new SubcommandData("volume", "Set playback volume from 0 to 150 percent.")
                                .addOption(OptionType.INTEGER, "percent", "Volume percentage, 0-150.", true)
                ));

        commands.add(Commands.slash("profile", "Show a Core Builders member profile.")
                .addOption(OptionType.USER, "member", "Member to view.", false));
        commands.add(Commands.slash("balance", "Show your coin balance.")
                .addOption(OptionType.USER, "member", "Member to view; staff only for other users.", false));
        commands.add(Commands.slash("leaderboard", "Show Core Builders rankings.")
                .addOptions(leaderboardTypeOption()));
        commands.add(Commands.slash("achievements", "Show unlocked achievements.")
                .addOption(OptionType.USER, "member", "Member to view.", false));
        commands.add(Commands.slash("link", "Generate a one-time code to link your Minecraft account."));

        commands.add(Commands.slash("stats", "Show overall Core Builders progression statistics."));
        commands.add(Commands.slash("transactions", "Show recent points and coin transactions.")
                .addOption(OptionType.USER, "member", "Member to view; staff only for other users.", false)
                .addOption(OptionType.INTEGER, "limit", "Number of transactions, 1-25.", false));

        commands.add(Commands.slash("contribute", "Submit a contribution for staff review.")
                .addOptions(categoryOption("category", true))
                .addOption(OptionType.STRING, "description", "What did you contribute?", true)
                .addOption(OptionType.STRING, "project", "Related project name.", false)
                .addOption(OptionType.STRING, "evidence", "Screenshot/message URL or other evidence.", false));

        commands.add(Commands.slash("shop", "Show the coin shop."));
        commands.add(Commands.slash("buy", "Purchase a Core Builders shop item.")
                .addOption(OptionType.STRING, "item", "Shop item code shown by /shop.", true));

        commands.add(Commands.slash("order", "View and manage shop orders.")
                .addSubcommands(
                        new SubcommandData("mine", "Show your recent shop orders."),
                        new SubcommandData("pending", "Show pending orders; staff only."),
                        new SubcommandData("complete", "Mark an order fulfilled; staff only.")
                                .addOption(OptionType.STRING, "id", "Order UUID.", true)
                                .addOption(OptionType.STRING, "note", "Fulfillment note.", false),
                        new SubcommandData("refund", "Cancel and refund a pending order; admin only.")
                                .addOption(OptionType.STRING, "id", "Order UUID.", true)
                                .addOption(OptionType.STRING, "reason", "Refund reason.", true)
                ));

        commands.add(Commands.slash("marketplace-dispute", "Review and resolve player marketplace disputes.")
                .addSubcommands(
                        new SubcommandData("list", "List unresolved marketplace disputes; staff only."),
                        new SubcommandData("resolve", "Resolve a marketplace dispute; admin only.")
                                .addOption(OptionType.STRING, "id", "Marketplace order-line UUID.", true)
                                .addOption(OptionType.STRING, "resolution", "REFUND_BUYER or RELEASE_SELLER.", true)
                                .addOption(OptionType.STRING, "reason", "Audited resolution reason.", true)
                ));

        commands.add(Commands.slash("project", "Core Builders project management.")
                .addSubcommands(
                        new SubcommandData("list", "List active projects."),
                        new SubcommandData("view", "View a project and its tasks.")
                                .addOption(OptionType.STRING, "id", "Project UUID.", true),
                        new SubcommandData("join", "Join a project.")
                                .addOption(OptionType.STRING, "id", "Project UUID.", true),
                        new SubcommandData("leave", "Leave a project.")
                                .addOption(OptionType.STRING, "id", "Project UUID.", true),
                        new SubcommandData("create", "Create a project; admin only.")
                                .addOption(OptionType.STRING, "name", "Project name.", true)
                                .addOption(OptionType.STRING, "description", "Project description.", true)
                                .addOption(OptionType.USER, "lead", "Project lead.", false),
                        new SubcommandData("task-add", "Add a rewarded project task; staff only.")
                                .addOption(OptionType.STRING, "project_id", "Project UUID.", true)
                                .addOption(OptionType.STRING, "title", "Task title.", true)
                                .addOption(OptionType.INTEGER, "points", "Point reward.", true)
                                .addOption(OptionType.INTEGER, "coins", "Coin reward.", true)
                                .addOption(OptionType.USER, "assignee", "Assigned member.", false),
                        new SubcommandData("task-complete", "Complete a project task.")
                                .addOption(OptionType.STRING, "project_id", "Project UUID.", true)
                                .addOption(OptionType.STRING, "task_id", "Task UUID.", true),
                        new SubcommandData("complete", "Close a completed project; admin only.")
                                .addOption(OptionType.STRING, "id", "Project UUID.", true)
                ));

        commands.add(Commands.slash("mission", "Core Builders mission management.")
                .addSubcommands(
                        new SubcommandData("list", "List active missions."),
                        new SubcommandData("view", "View a mission.")
                                .addOption(OptionType.STRING, "id", "Mission UUID.", true),
                        new SubcommandData("join", "Join a mission.")
                                .addOption(OptionType.STRING, "id", "Mission UUID.", true),
                        new SubcommandData("create", "Create a mission; staff only.")
                                .addOption(OptionType.STRING, "name", "Mission name.", true)
                                .addOption(OptionType.STRING, "description", "Mission description.", true)
                                .addOption(OptionType.INTEGER, "points", "Point reward per participant.", true)
                                .addOption(OptionType.INTEGER, "coins", "Coin reward per participant.", true)
                                .addOption(OptionType.INTEGER, "slots", "Maximum slots; 0 for unlimited.", true)
                                .addOption(OptionType.INTEGER, "deadline_days", "Days until deadline; omit for none.", false),
                        new SubcommandData("complete", "Complete and reward a mission; staff only.")
                                .addOption(OptionType.STRING, "id", "Mission UUID.", true)
                ));

        commands.add(Commands.slash("award", "Award rank points and coins; staff only.")
                .addOption(OptionType.USER, "member", "Member to reward.", true)
                .addOptions(categoryOption("category", true))
                .addOption(OptionType.INTEGER, "points", "Point amount; may be zero.", true)
                .addOption(OptionType.INTEGER, "coins", "Coin amount; may be zero.", true)
                .addOption(OptionType.STRING, "reason", "Reason for the award.", true));

        commands.add(Commands.slash("contribution", "Review contribution submissions; staff only.")
                .addSubcommands(
                        new SubcommandData("pending", "List pending submissions."),
                        new SubcommandData("approve", "Approve a submission.")
                                .addOption(OptionType.STRING, "id", "Contribution UUID.", true)
                                .addOption(OptionType.INTEGER, "points", "Point reward; default uses suggestion.", false)
                                .addOption(OptionType.INTEGER, "coins", "Coin reward; default uses suggestion.", false)
                                .addOption(OptionType.STRING, "reason", "Review note.", false),
                        new SubcommandData("reject", "Reject a submission.")
                                .addOption(OptionType.STRING, "id", "Contribution UUID.", true)
                                .addOption(OptionType.STRING, "reason", "Rejection reason.", true)
                ));

        commands.add(Commands.slash("points", "Administrative rank-point adjustments.")
                .addSubcommands(
                        adjustmentSubcommand("add", "Add points."),
                        adjustmentSubcommand("remove", "Remove points.")
                ));

        commands.add(Commands.slash("coins", "Administrative coin adjustments.")
                .addSubcommands(
                        creditAdjustmentSubcommand("add", "Add coins."),
                        creditAdjustmentSubcommand("remove", "Remove coins.")
                ));

        SubcommandData reputationSet = new SubcommandData("set", "Set a member reputation; leadership only.")
                .addOption(OptionType.USER, "member", "Member to update.", true)
                .addOptions(reputationOption())
                .addOption(OptionType.STRING, "reason", "Reason for the change.", true);
        commands.add(Commands.slash("reputation", "Manage Core Builders trust reputation.")
                .addSubcommands(reputationSet));

        commands.add(Commands.slash("member", "Manage Core Builders member profiles; admin only.")
                .addSubcommands(
                        new SubcommandData("role", "Set a member's primary functional role.")
                                .addOption(OptionType.USER, "member", "Member to update.", true)
                                .addOption(OptionType.STRING, "role", "Example: Builder, Spawn Helper, Developer.", true),
                        new SubcommandData("activate", "Mark a member profile active.")
                                .addOption(OptionType.USER, "member", "Member to activate.", true),
                        new SubcommandData("deactivate", "Mark a member profile inactive.")
                                .addOption(OptionType.USER, "member", "Member to deactivate.", true)
                ));

        commands.add(Commands.slash("audit", "View recent Core Bot audit records; admin only.")
                .addOption(OptionType.USER, "member", "Filter by member.", false)
                .addOption(OptionType.INTEGER, "limit", "Number of records, 1-25.", false));

        commands.add(Commands.slash("setup", "Create standard Core Builders Discord roles and channels.")
                .addSubcommands(
                        new SubcommandData("roles", "Create missing progression rank roles."),
                        new SubcommandData("channels", "Create missing bot workflow channels.")
                ));

        return commands;
    }

    private OptionData categoryOption(String name, boolean required) {
        OptionData option = new OptionData(OptionType.STRING, name, "Contribution category.", required);
        for (ContributionCategory category : ContributionCategory.values()) {
            if (category == ContributionCategory.BONUS) continue;
            option.addChoice(category.display(), category.name());
        }
        return option;
    }

    private OptionData leaderboardTypeOption() {
        OptionData option = new OptionData(OptionType.STRING, "type", "Leaderboard type.", false)
                .addChoice("Overall", "OVERALL")
                .addChoice("Weekly", "WEEKLY");
        for (ContributionCategory category : ContributionCategory.values()) {
            if (category == ContributionCategory.BONUS) continue;
            option.addChoice(category.display(), category.name());
        }
        return option;
    }

    private OptionData reputationOption() {
        OptionData option = new OptionData(OptionType.STRING, "level", "New reputation level.", true);
        for (Reputation reputation : Reputation.values()) {
            option.addChoice(reputation.display(), reputation.name());
        }
        return option;
    }

    private SubcommandData adjustmentSubcommand(String name, String description) {
        return new SubcommandData(name, description)
                .addOption(OptionType.USER, "member", "Member to update.", true)
                .addOption(OptionType.INTEGER, "amount", "Positive adjustment amount.", true)
                .addOptions(categoryOption("category", true))
                .addOption(OptionType.STRING, "reason", "Reason for adjustment.", true);
    }

    private SubcommandData creditAdjustmentSubcommand(String name, String description) {
        return new SubcommandData(name, description)
                .addOption(OptionType.USER, "member", "Member to update.", true)
                .addOption(OptionType.INTEGER, "amount", "Positive adjustment amount.", true)
                .addOption(OptionType.STRING, "reason", "Reason for adjustment.", true);
    }
}

package com.corebuilders.bot.discord;

import com.corebuilders.bot.application.RequestRateLimiter;
import com.corebuilders.bot.config.BotProperties;
import com.corebuilders.bot.discord.command.DiscordCommandRouter;
import com.corebuilders.bot.discord.command.TextCommandParser;
import com.corebuilders.bot.discord.command.TextCommandParser.ParsedCommand;
import com.corebuilders.bot.external.NewPlayersProvider;
import com.corebuilders.bot.external.NewPlayersResponse;
import com.corebuilders.bot.model.Domain.*;
import com.corebuilders.bot.model.Models.*;
import com.corebuilders.bot.model.MarketplaceModels.DisputeResolution;
import com.corebuilders.bot.model.MarketplaceModels.MarketplaceOrderLine;
import com.corebuilders.bot.model.RankDefinition;
import com.corebuilders.bot.service.*;
import com.corebuilders.bot.util.ErrorMessages;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import static com.corebuilders.bot.discord.DiscordFormatting.*;
import static com.corebuilders.bot.discord.command.SlashCommandOptions.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public final class DiscordBotListener extends ListenerAdapter {
    private final MemberService members;
    private final LedgerService ledger;
    private final ContributionService contributions;
    private final AchievementService achievements;
    private final ProjectService projects;
    private final MissionService missions;
    private final ShopService shop;
    private final MarketplaceDisputeOperations marketplaceDisputes;
    private final AuditService audit;
    private final LinkService links;
    private final DiscordWebLoginService discordWebLogin;
    private final PermissionService permissions;
    private final RankRoleService rankRoles;
    private final DiscordNotifier notifier;
    private final BotProperties properties;
    private final NewPlayersProvider newPlayersProvider;
    private final RequestRateLimiter newPlayersRateLimiter;
    private final RequestRateLimiter discordWebLoginRateLimiter;
    private final DiscordCommandRouter<SlashCommandInteractionEvent, MessageEmbed> commandRouter;
    private final TextCommandParser textCommandParser;
    private final NewPlayersEmbedFactory newPlayersEmbedFactory = new NewPlayersEmbedFactory();
    private final DiscordViewFactory views = new DiscordViewFactory();

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public DiscordBotListener(
            MemberService members,
            LedgerService ledger,
            ContributionService contributions,
            AchievementService achievements,
            ProjectService projects,
            MissionService missions,
            ShopService shop,
            MarketplaceDisputeOperations marketplaceDisputes,
            AuditService audit,
            LinkService links,
            DiscordWebLoginService discordWebLogin,
            PermissionService permissions,
            RankRoleService rankRoles,
            DiscordNotifier notifier,
            BotProperties properties,
            NewPlayersProvider newPlayersProvider
    ) {
        this.members = members;
        this.ledger = ledger;
        this.contributions = contributions;
        this.achievements = achievements;
        this.projects = projects;
        this.missions = missions;
        this.shop = shop;
        this.marketplaceDisputes = Objects.requireNonNull(marketplaceDisputes, "marketplaceDisputes");
        this.audit = audit;
        this.links = links;
        this.discordWebLogin = Objects.requireNonNull(discordWebLogin, "discordWebLogin");
        this.permissions = permissions;
        this.rankRoles = rankRoles;
        this.notifier = notifier;
        this.properties = properties;
        this.newPlayersProvider = Objects.requireNonNull(newPlayersProvider, "newPlayersProvider");
        this.discordWebLoginRateLimiter = new RequestRateLimiter(
                java.time.Duration.ofSeconds(3),
                120,
                java.time.Duration.ofMinutes(1)
        );
        this.newPlayersRateLimiter = new RequestRateLimiter(
                java.time.Duration.ofSeconds(properties.getHyperglidingUserCooldownSeconds()),
                properties.getHyperglidingGlobalRequestsPerMinute(),
                java.time.Duration.ofMinutes(1)
        );
        this.textCommandParser = new TextCommandParser(properties.getTextCommandPrefix());
        this.commandRouter = DiscordCommandRouter.<SlashCommandInteractionEvent, MessageEmbed>builder()
                .register("core", this::core)
                .register("profile", this::profile)
                .register("balance", this::balance)
                .register("leaderboard", this::leaderboard)
                .register("achievements", this::achievementList)
                .register("link", this::linkMinecraft)
                .register("stats", this::stats)
                .register("transactions", this::transactions)
                .register("contribute", this::contribute)
                .register("shop", this::shop)
                .register("buy", this::buy)
                .register("order", this::order)
                .register("marketplace-dispute", this::marketplaceDispute)
                .register("project", this::project)
                .register("mission", this::mission)
                .register("award", this::award)
                .register("contribution", this::contributionReview)
                .register("xp", this::xpAdjustment)
                .register("credits", this::creditAdjustment)
                .register("reputation", this::reputation)
                .register("member", this::memberManagement)
                .register("audit", this::audit)
                .register("setup", this::setup)
                .build();
    }


    public Set<String> handledCommandNames() {
        return commandRouter.commandNames();
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if ("apply".equals(event.getName())
                || "application".equals(event.getName())
                || "music".equals(event.getName())) {
            return;
        }

        if (!event.isFromGuild() || event.getGuild() == null || !isAllowedGuild(event.getGuild())) {
            event.reply("CoreBot commands can only be used inside the configured Core Builders server.")
                    .setEphemeral(true).queue();
            return;
        }

        boolean ephemeral = isEphemeral(event);
        event.deferReply(ephemeral).queue(hook -> executor.submit(() -> {
            try {
                members.ensureMember(event.getUser().getId(), event.getUser().getName());
                if (isNewPlayersCommand(event)) {
                    List<MessageEmbed> responses = newPlayers(event);
                    hook.editOriginalEmbeds(responses.get(0)).queue(ignored -> {
                        for (int index = 1; index < responses.size(); index++) {
                            hook.sendMessageEmbeds(responses.get(index)).queue();
                        }
                    });
                } else {
                    MessageEmbed response = dispatch(event);
                    hook.editOriginalEmbeds(response).queue();
                }
            } catch (Exception error) {
                hook.editOriginal("❌ " + safeMessage(error)).queue();
            }
        }));
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (!properties.isTextCommandsEnabled()
                || !event.isFromGuild()
                || !isAllowedGuild(event.getGuild())
                || event.getAuthor().isBot()
                || event.getMessage().isWebhookMessage()) {
            return;
        }

        ParsedCommand parsed = textCommandParser.parse(event.getMessage().getContentRaw().trim());
        if (parsed == null) {
            return;
        }

        executor.submit(() -> {
            try {
                User user = event.getAuthor();
                members.ensureMember(user.getId(), user.getName());
                MessageEmbed response = dispatchTextCommand(user, parsed);
                event.getChannel().sendMessageEmbeds(response).queue();
            } catch (Exception error) {
                event.getChannel().sendMessage("❌ " + safeMessage(error)).queue();
            }
        });
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String id = event.getComponentId();
        if (!id.startsWith("contrib:")) return;

        if (!event.isFromGuild() || event.getGuild() == null || !isAllowedGuild(event.getGuild())) {
            event.reply("This action can only be used in the configured Core Builders server.").setEphemeral(true).queue();
            return;
        }

        event.deferEdit().queue(hook -> executor.submit(() -> {
            try {
                permissions.requireTrustedStaff(event.getMember());
                String[] parts = id.split(":", 3);
                if (parts.length != 3) throw new IllegalArgumentException("Invalid contribution action.");
                UUID contributionId = uuid(parts[2], "contribution ID");
                Contribution result;
                if ("approve".equals(parts[1])) {
                    Contribution pending = contributions.get(contributionId);
                    result = contributions.approve(
                            contributionId,
                            pending.suggestedCxp(),
                            pending.suggestedCredits(),
                            event.getUser().getId(),
                            "Approved through staff review button."
                    );
                    afterContributionReward(event.getGuild(), result);
                } else if ("reject".equals(parts[1])) {
                    result = contributions.reject(
                            contributionId,
                            event.getUser().getId(),
                            "Rejected through staff review button. Use /contribution reject for a detailed reason."
                    );
                    notifier.contributionReviewed(event.getGuild(), result);
                } else {
                    throw new IllegalArgumentException("Unknown contribution action.");
                }

                hook.editOriginalEmbeds(views.contributionReviewed(result)).queue();
                hook.editOriginalComponents().queue();
                event.getHook().sendMessage("Review saved.").setEphemeral(true).queue();
            } catch (Exception error) {
                event.getHook().sendMessage("❌ " + safeMessage(error)).setEphemeral(true).queue();
            }
        }));
    }

    private MessageEmbed dispatch(SlashCommandInteractionEvent event) {
        return commandRouter.dispatch(event.getName(), event);
    }

    /**
     * Discord-side equivalent of the Minecraft /core command.
     *
     * The legacy top-level slash commands are intentionally kept for backwards
     * compatibility, while /core groups the common member workflows in one place.
     * Both paths call the same QueryDSL-backed services.
     */
    private MessageEmbed core(SlashCommandInteractionEvent event) {
        return switch (requiredSubcommand(event)) {
            case "profile" -> profile(event);
            case "balance" -> balance(event);
            case "leaderboard" -> leaderboard(event);
            case "achievements" -> achievementList(event);
            case "link" -> linkMinecraft(event);
            case "web-login" -> verifyWebsiteLogin(event);
            case "transactions" -> transactions(event);
            case "stats" -> stats(event);
            case "newplayers" -> throw new IllegalStateException("/core newplayers is handled as a multi-embed response.");
            default -> throw new IllegalArgumentException("Unknown /core subcommand.");
        };
    }

    private List<MessageEmbed> newPlayers(SlashCommandInteractionEvent event) {
        RequestRateLimiter.Decision decision = newPlayersRateLimiter.tryAcquire(event.getUser().getId());
        if (!decision.allowed()) {
            throw new IllegalStateException(
                    "Please wait " + decision.retryAfterSeconds() + " second(s) before requesting player data again."
            );
        }
        String server = requiredString(event, "server").trim();
        int size = boundedInt(event, "size", 5, 1, 50);
        int page = boundedInt(event, "page", 1, 1, Integer.MAX_VALUE);
        NewPlayersResponse response = newPlayersProvider.fetchNewPlayers(server, page, size);
        return newPlayersEmbedFactory.create(server, page, size, response);
    }

    private static boolean isNewPlayersCommand(SlashCommandInteractionEvent event) {
        return "core".equals(event.getName()) && "newplayers".equals(event.getSubcommandName());
    }

    private MessageEmbed dispatchTextCommand(User user, ParsedCommand command) {
        return switch (command.command()) {
            case "profile" -> profileFor(user);
            case "leaderboard", "top" -> leaderboardFor(command.argument(0, "OVERALL"));
            case "achievements", "achievement" -> achievementListFor(user);
            case "stats" -> statsEmbed();
            case "link" -> views.simple(
                    "Link your Minecraft account",
                    "Use the private Discord slash command `/core link` (or `/link`) so your one-time link code is not posted publicly."
            );
            case "help" -> textCommandHelp();
            default -> views.simple(
                    "Unknown Core Builders command",
                    "Use `" + properties.getTextCommandPrefix() + " help` to see available Discord text commands."
            );
        };
    }

    private MessageEmbed textCommandHelp() {
        String prefix = properties.getTextCommandPrefix();
        return new EmbedBuilder()
                .setTitle("Core Builders Discord Commands")
                .setDescription(
                        "`" + prefix + " profile` — View your profile\n"
                                + "`" + prefix + " leaderboard [overall|weekly|category]` — View rankings\n"
                                + "`" + prefix + " achievements` — View your achievements\n"
                                + "`" + prefix + " stats` — View group statistics\n\n"
                                + "All full staff and management workflows are available as Discord slash commands."
                )
                .build();
    }

    private MessageEmbed linkMinecraft(SlashCommandInteractionEvent event) {
        return linkMinecraftFor(event.getUser());
    }

    private MessageEmbed verifyWebsiteLogin(SlashCommandInteractionEvent event) {
        RequestRateLimiter.Decision decision = discordWebLoginRateLimiter.tryAcquire(event.getUser().getId());
        if (!decision.allowed()) {
            throw new IllegalStateException(
                    "Too many login attempts. Try again in " + decision.retryAfterSeconds() + " second(s)."
            );
        }
        DiscordWebLoginChallengeRepository.VerificationResult result = discordWebLogin.verifyFromDiscord(
                requiredString(event, "code"),
                event.getUser().getId(),
                event.getUser().getEffectiveName(),
                event.getUser().getEffectiveAvatarUrl()
        );
        String message = switch (result.status()) {
            case VERIFIED -> "Website login verified. Return to your browser and confirm this Discord account.";
            case ALREADY_VERIFIED -> "This website login code was already verified. Return to your browser.";
            case INVALID -> "Invalid website login code. Generate a new Discord Bot login code on the website.";
            case EXPIRED -> "That website login code expired. Generate a new code on the website.";
            case USED -> "That website login code has already been used.";
            case INACTIVE -> "Your Core Builders profile is inactive.";
        };
        return views.simple("Discord Bot Website Login", message);
    }

    private MessageEmbed linkMinecraftFor(User user) {
        LinkService.LinkCode code = links.createCode(user.getId(), properties.getLinkCodeExpiryMinutes());
        return new EmbedBuilder()
                .setTitle("Link your Minecraft account")
                .setDescription("Join the Minecraft server and run `/core link " + code.code() + "`.")
                .addField("One-time code", "`" + code.code() + "`", false)
                .addField("Expires", "<t:" + code.expiresAt().getEpochSecond() + ":R>", false)
                .setFooter("Never share this code. It links the Minecraft account that redeems it to your Discord profile.")
                .build();
    }

    private MessageEmbed profile(SlashCommandInteractionEvent event) {
        return profileFor(user(event, "member", event.getUser()));
    }

    private MessageEmbed profileFor(User target) {
        members.ensureMember(target.getId(), target.getName());
        ProfileSnapshot p = members.snapshot(target.getId(), achievements);

        RankDefinition next = p.nextRank();
        String progress = next == null
                ? "Maximum rank reached"
                : p.totalXp() + " / " + next.minimumXp() + " CXP to " + next.display();

        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("Core Builders Profile — " + p.member().username())
                .setDescription("**" + p.rank().display() + " • Level " + p.level() + "**\n" + progress)
                .addField("Core XP", formatNumber(p.totalXp()) + " CXP", true)
                .addField("Core Credits", formatNumber(p.credits()) + " CC", true)
                .addField("Weekly XP", formatNumber(p.weeklyXp()) + " CXP", true)
                .addField("Reputation", p.member().reputation().display(), true)
                .addField("Primary Role", value(p.member().primaryRole()), true)
                .addBlankField(true);

        for (ContributionCategory category : visibleCategories()) {
            long amount = p.categoryXp().getOrDefault(category, 0L);
            eb.addField(category.display(), formatNumber(amount) + " CXP", true);
        }

        String unlocked = p.achievements().isEmpty()
                ? "No achievements unlocked yet."
                : p.achievements().stream().map(a -> "🏅 " + a.name()).collect(Collectors.joining("\n"));
        eb.addField("Achievements", truncate(unlocked, 1024), false)
                .setFooter("Discord ID: " + p.member().discordUserId());
        return eb.build();
    }

    private MessageEmbed balance(SlashCommandInteractionEvent event) {
        User target = user(event, "member", event.getUser());
        if (!target.getId().equals(event.getUser().getId())) {
            permissions.requireTrustedStaff(event.getMember());
        }
        return balanceFor(target);
    }

    private MessageEmbed balanceFor(User target) {
        Member member = members.ensureMember(target.getId(), target.getName());
        long balance = ledger.creditBalance(member.id());
        return new EmbedBuilder()
                .setTitle("Core Credit Account")
                .setDescription("<@" + target.getId() + ">")
                .addField("Current Balance", formatNumber(balance) + " CC", false)
                .build();
    }

    private MessageEmbed leaderboard(SlashCommandInteractionEvent event) {
        return leaderboardFor(string(event, "type", "OVERALL"));
    }

    private MessageEmbed leaderboardFor(String requestedType) {
        String type = requestedType == null || requestedType.isBlank() ? "OVERALL" : requestedType.trim();
        List<LeaderboardEntry> entries;
        String title;
        if ("WEEKLY".equalsIgnoreCase(type)) {
            entries = ledger.leaderboardWeekly(10);
            title = "Weekly Core Leaderboard";
        } else if ("OVERALL".equalsIgnoreCase(type)) {
            entries = ledger.leaderboardOverall(10);
            title = "Lifetime Core Leaderboard";
        } else {
            ContributionCategory category = ContributionCategory.parse(type);
            entries = ledger.leaderboardCategory(category, 10);
            title = category.display() + " Leaderboard";
        }

        StringBuilder body = new StringBuilder();
        for (int i = 0; i < entries.size(); i++) {
            LeaderboardEntry entry = entries.get(i);
            body.append(medal(i))
                    .append(" **").append(i + 1).append(".** <@").append(entry.discordUserId()).append(">")
                    .append(" — **").append(formatNumber(entry.score())).append(" CXP**\n");
        }
        if (entries.isEmpty()) body.append("No members have earned CXP yet.");

        return new EmbedBuilder()
                .setTitle("🏆 " + title)
                .setDescription(body.toString())
                .build();
    }

    private MessageEmbed achievementList(SlashCommandInteractionEvent event) {
        return achievementListFor(user(event, "member", event.getUser()));
    }

    private MessageEmbed achievementListFor(User target) {
        Member member = members.ensureMember(target.getId(), target.getName());
        List<Achievement> list = achievements.unlocked(member.id());
        String body = list.isEmpty()
                ? "No achievements unlocked yet."
                : list.stream()
                .map(a -> "🏅 **" + a.name() + "**\n" + a.description())
                .collect(Collectors.joining("\n\n"));
        return new EmbedBuilder()
                .setTitle("Achievements — " + member.username())
                .setDescription(truncate(body, 4000))
                .setFooter(list.size() + " unlocked")
                .build();
    }

    private MessageEmbed stats(SlashCommandInteractionEvent event) {
        return statsEmbed();
    }

    private MessageEmbed statsEmbed() {
        GroupStats stats = ledger.groupStats();
        List<LeaderboardEntry> leaders = ledger.leaderboardOverall(3);
        String top = leaders.isEmpty() ? "No ranked members yet." : leaders.stream()
                .map(e -> "<@" + e.discordUserId() + "> — **" + formatNumber(e.score()) + " CXP**")
                .collect(Collectors.joining("\n"));
        return new EmbedBuilder()
                .setTitle("Core Builders Statistics")
                .addField("Active Members", formatNumber(stats.members()), true)
                .addField("Active This Week", formatNumber(stats.activeThisWeek()), true)
                .addField("Approved Contributions", formatNumber(stats.approvedContributions()), true)
                .addField("Projects", stats.completedProjects() + " / " + stats.totalProjects() + " completed", true)
                .addField("Lifetime Net CXP", formatNumber(stats.totalCxpAwarded()), true)
                .addField("CC in Circulation", formatNumber(stats.creditsInCirculation()), true)
                .addField("Top Contributors", top, false)
                .build();
    }

    private MessageEmbed transactions(SlashCommandInteractionEvent event) {
        User targetUser = user(event, "member", event.getUser());
        if (!targetUser.getId().equals(event.getUser().getId())) {
            permissions.requireTrustedStaff(event.getMember());
        }
        Long requested = nullableLong(event, "limit");
        int limit = requested == null ? 15 : (int) Math.max(1, Math.min(25, requested));
        return transactionsFor(targetUser, limit);
    }

    private MessageEmbed transactionsFor(User targetUser, int limit) {
        Member target = members.ensureMember(targetUser.getId(), targetUser.getName());
        int safeLimit = Math.max(1, Math.min(25, limit));
        List<LedgerEntry> entries = ledger.recentTransactions(target.id(), safeLimit);
        String body = entries.stream()
                .map(tx -> "`" + tx.createdAt() + "` "
                        + (tx.amount() >= 0 ? "**+" : "**") + formatNumber(tx.amount()) + " " + tx.type() + "**"
                        + (tx.category() == null ? "" : " • " + tx.category())
                        + "\n" + truncate(tx.reason(), 220))
                .collect(Collectors.joining("\n\n"));
        return new EmbedBuilder()
                .setTitle("Recent Transactions — " + target.username())
                .setDescription(truncate(body.isBlank() ? "No transactions found." : body, 4000))
                .build();
    }

    private MessageEmbed contribute(SlashCommandInteractionEvent event) {
        Member actor = members.ensureMember(event.getUser().getId(), event.getUser().getName());
        ContributionCategory category = ContributionCategory.parse(requiredString(event, "category"));
        Contribution c = contributions.submit(
                actor,
                category,
                requiredString(event, "description"),
                string(event, "project", null),
                string(event, "evidence", null)
        );
        notifier.contributionPending(event.getGuild(), c);
        return new EmbedBuilder()
                .setTitle("Contribution Submitted")
                .setDescription("Your contribution is waiting for staff review.")
                .addField("Category", c.category().display(), true)
                .addField("Suggested Reward", c.suggestedCxp() + " CXP • " + c.suggestedCredits() + " CC", true)
                .addField("Contribution ID", c.id().toString(), false)
                .build();
    }

    private MessageEmbed shop(SlashCommandInteractionEvent event) {
        List<ShopItem> items = shop.activeItems();
        String body = items.stream()
                .map(i -> "**" + i.code() + "** — " + i.name()
                        + "\n" + i.description()
                        + "\n💰 " + formatNumber(i.price()) + " CC"
                        + (i.stock() == null ? "" : " • Stock: " + i.stock()))
                .collect(Collectors.joining("\n\n"));
        return new EmbedBuilder()
                .setTitle("Core Builders Shop")
                .setDescription(truncate(body.isBlank() ? "The shop is empty." : body, 4000))
                .setFooter("Use /buy item:<code>")
                .build();
    }

    private MessageEmbed buy(SlashCommandInteractionEvent event) {
        Member actor = members.ensureMember(event.getUser().getId(), event.getUser().getName());
        ShopOrder order = shop.buy(actor, requiredString(event, "item"));
        notifier.economyLog(event.getGuild(), "New Shop Order",
                "<@" + actor.discordUserId() + "> purchased **" + order.itemName() + "** for "
                        + formatNumber(order.price()) + " CC.\nOrder: `" + order.id() + "`");
        return views.order("Purchase Successful", order);
    }

    private MessageEmbed order(SlashCommandInteractionEvent event) {
        String sub = requiredSubcommand(event);
        return switch (sub) {
            case "mine" -> {
                Member actor = members.ensureMember(event.getUser().getId(), event.getUser().getName());
                yield views.orders("Your Recent Orders", shop.memberOrders(actor.id(), 10));
            }
            case "pending" -> {
                permissions.requireTrustedStaff(event.getMember());
                yield views.orders("Pending Shop Orders", shop.orders(OrderStatus.PENDING, 20));
            }
            case "complete" -> {
                permissions.requireTrustedStaff(event.getMember());
                ShopOrder result = shop.complete(
                        uuid(requiredString(event, "id"), "order ID"),
                        event.getUser().getId(),
                        string(event, "note", null)
                );
                yield views.order("Order Completed", result);
            }
            case "refund" -> {
                permissions.requireAdmin(event.getMember());
                ShopOrder result = shop.cancelAndRefund(
                        uuid(requiredString(event, "id"), "order ID"),
                        event.getUser().getId(),
                        requiredString(event, "reason")
                );
                yield views.order("Order Refunded", result);
            }
            default -> throw new IllegalArgumentException("Unknown order action.");
        };
    }

    private MessageEmbed marketplaceDispute(SlashCommandInteractionEvent event) {
        return switch (requiredSubcommand(event)) {
            case "list" -> {
                permissions.requireTrustedStaff(event.getMember());
                List<MarketplaceOrderLine> disputes = marketplaceDisputes.disputes(20);
                String body = disputes.stream()
                        .map(line -> "**" + line.itemName() + "** — " + formatNumber(line.lineTotal()) + " CC"
                                + "\nBuyer: " + line.buyerUsername()
                                + "\nLine: `" + line.id() + "`"
                                + "\nReason: " + value(line.disputeReason()))
                        .collect(Collectors.joining("\n\n"));
                yield new EmbedBuilder()
                        .setTitle("Unresolved Marketplace Disputes")
                        .setDescription(truncate(body.isBlank() ? "No unresolved marketplace disputes." : body, 4000))
                        .build();
            }
            case "resolve" -> {
                permissions.requireAdmin(event.getMember());
                MarketplaceOrderLine line = marketplaceDisputes.resolveDispute(
                        uuid(requiredString(event, "id"), "marketplace order-line ID"),
                        DisputeResolution.parse(requiredString(event, "resolution")),
                        event.getUser().getId(),
                        requiredString(event, "reason")
                );
                yield new EmbedBuilder()
                        .setTitle("Marketplace Dispute Resolved")
                        .setDescription("**" + line.itemName() + "** was resolved as **"
                                + value(line.resolution()).replace('_', ' ') + "**.")
                        .addField("Line ID", line.id().toString(), false)
                        .addField("Amount", formatNumber(line.lineTotal()) + " CC", true)
                        .addField("Reason", value(line.resolutionNote()), false)
                        .build();
            }
            default -> throw new IllegalArgumentException("Unknown marketplace dispute action.");
        };
    }

    private MessageEmbed project(SlashCommandInteractionEvent event) {
        String sub = requiredSubcommand(event);
        return switch (sub) {
            case "list" -> projectList();
            case "view" -> projectView(uuid(requiredString(event, "id"), "project ID"));
            case "join" -> {
                Member actor = members.ensureMember(event.getUser().getId(), event.getUser().getName());
                UUID id = uuid(requiredString(event, "id"), "project ID");
                projects.join(id, actor);
                yield views.simple("Project Joined", "You joined **" + projects.get(id).name() + "**.");
            }
            case "leave" -> {
                Member actor = members.ensureMember(event.getUser().getId(), event.getUser().getName());
                UUID id = uuid(requiredString(event, "id"), "project ID");
                projects.leave(id, actor);
                yield views.simple("Project Left", "You left project `" + id + "`.");
            }
            case "create" -> {
                permissions.requireAdmin(event.getMember());
                User leadUser = user(event, "lead", event.getUser());
                members.ensureMember(leadUser.getId(), leadUser.getName());
                Project created = projects.create(
                        requiredString(event, "name"),
                        requiredString(event, "description"),
                        leadUser.getId(),
                        event.getUser().getId()
                );
                yield views.project(created);
            }
            case "task-add" -> {
                permissions.requireTrustedStaff(event.getMember());
                UUID projectId = uuid(requiredString(event, "project_id"), "project ID");
                User assigneeUser = user(event, "assignee", null);
                Member assignee = assigneeUser == null ? null
                        : members.ensureMember(assigneeUser.getId(), assigneeUser.getName());
                ProjectTask task = projects.addTask(
                        projectId,
                        requiredString(event, "title"),
                        assignee,
                        requiredLong(event, "cxp"),
                        requiredLong(event, "credits"),
                        event.getUser().getId()
                );
                yield views.projectTask(task);
            }
            case "task-complete" -> {
                Member actor = members.ensureMember(event.getUser().getId(), event.getUser().getName());
                UUID projectId = uuid(requiredString(event, "project_id"), "project ID");
                UUID taskId = uuid(requiredString(event, "task_id"), "task ID");
                Member rewarded = projects.completeTask(
                        projectId,
                        taskId,
                        actor,
                        permissions.isTrustedStaff(event.getMember())
                );
                List<Achievement> unlocked = achievements.evaluate(rewarded, event.getUser().getId());
                rankRoles.sync(event.getGuild(), rewarded.discordUserId());
                notifier.achievements(event.getGuild(), rewarded.discordUserId(), unlocked);
                yield views.simple("Project Task Completed",
                        "Task completed and rewards were applied to <@" + rewarded.discordUserId() + ">.");
            }
            case "complete" -> {
                permissions.requireAdmin(event.getMember());
                Project done = projects.complete(
                        uuid(requiredString(event, "id"), "project ID"),
                        event.getUser().getId()
                );
                yield views.project(done);
            }
            default -> throw new IllegalArgumentException("Unknown project action.");
        };
    }

    private MessageEmbed mission(SlashCommandInteractionEvent event) {
        String sub = requiredSubcommand(event);
        return switch (sub) {
            case "list" -> missionList();
            case "view" -> missionView(uuid(requiredString(event, "id"), "mission ID"));
            case "join" -> {
                Member actor = members.ensureMember(event.getUser().getId(), event.getUser().getName());
                UUID id = uuid(requiredString(event, "id"), "mission ID");
                missions.join(id, actor);
                yield views.simple("Mission Joined", "You joined **" + missions.get(id).name() + "**.");
            }
            case "create" -> {
                permissions.requireTrustedStaff(event.getMember());
                Long days = nullableLong(event, "deadline_days");
                Instant deadline = days == null ? null : Instant.now().plus(days, ChronoUnit.DAYS);
                Mission created = missions.create(
                        requiredString(event, "name"),
                        requiredString(event, "description"),
                        requiredLong(event, "cxp"),
                        requiredLong(event, "credits"),
                        Math.toIntExact(requiredLong(event, "slots")),
                        deadline,
                        event.getUser().getId()
                );
                yield views.mission(created);
            }
            case "complete" -> {
                permissions.requireTrustedStaff(event.getMember());
                UUID id = uuid(requiredString(event, "id"), "mission ID");
                Mission mission = missions.get(id);
                List<Member> rewarded = missions.complete(id, event.getUser().getId());
                for (Member member : rewarded) {
                    List<Achievement> unlocked = achievements.evaluate(member, event.getUser().getId());
                    rankRoles.sync(event.getGuild(), member.discordUserId());
                    notifier.achievements(event.getGuild(), member.discordUserId(), unlocked);
                }
                yield views.simple("Mission Completed",
                        "**" + mission.name() + "** rewarded " + rewarded.size() + " participant(s) with "
                                + mission.rewardCxp() + " CXP and " + mission.rewardCredits() + " CC each.");
            }
            default -> throw new IllegalArgumentException("Unknown mission action.");
        };
    }

    private MessageEmbed award(SlashCommandInteractionEvent event) {
        permissions.requireTrustedStaff(event.getMember());
        User targetUser = requiredUser(event, "member");
        Member target = members.ensureMember(targetUser.getId(), targetUser.getName());
        ContributionCategory category = ContributionCategory.parse(requiredString(event, "category"));
        long cxp = requiredLong(event, "cxp");
        long credits = requiredLong(event, "credits");
        String reason = requiredString(event, "reason");
        if (cxp < 0 || credits < 0) throw new IllegalArgumentException("Use /xp remove or /credits remove for deductions.");
        if (cxp == 0 && credits == 0) throw new IllegalArgumentException("At least one reward must be greater than zero.");

        if (cxp > 0) ledger.addXp(target.id(), cxp, category, SourceType.STAFF_AWARD, null, reason, event.getUser().getId());
        if (credits > 0) ledger.addCredits(target.id(), credits, SourceType.STAFF_AWARD, null, reason, event.getUser().getId());
        audit.log(event.getUser().getId(), "STAFF_AWARD", target.discordUserId(),
                "MEMBER", target.id().toString(), cxp + " CXP, " + credits + " CC. " + reason);

        List<Achievement> unlocked = achievements.evaluate(target, event.getUser().getId());
        rankRoles.sync(event.getGuild(), target.discordUserId());
        notifier.achievements(event.getGuild(), target.discordUserId(), unlocked);
        notifier.economyLog(event.getGuild(), "Member Reward",
                "<@" + target.discordUserId() + "> received **" + cxp + " CXP** and **"
                        + credits + " CC**.\nReason: " + reason);

        return views.simple("Award Applied",
                "<@" + target.discordUserId() + "> received **" + cxp + " CXP** and **" + credits + " CC**.");
    }

    private MessageEmbed contributionReview(SlashCommandInteractionEvent event) {
        permissions.requireTrustedStaff(event.getMember());
        String sub = requiredSubcommand(event);
        return switch (sub) {
            case "pending" -> views.pendingContributions(contributions.pending(20));
            case "approve" -> {
                UUID id = uuid(requiredString(event, "id"), "contribution ID");
                Contribution pending = contributions.get(id);
                Long cxpOption = nullableLong(event, "cxp");
                Long creditOption = nullableLong(event, "credits");
                Contribution result = contributions.approve(
                        id,
                        cxpOption == null ? pending.suggestedCxp() : cxpOption,
                        creditOption == null ? pending.suggestedCredits() : creditOption,
                        event.getUser().getId(),
                        string(event, "reason", "Approved by staff.")
                );
                afterContributionReward(event.getGuild(), result);
                yield views.contributionReviewed(result);
            }
            case "reject" -> {
                Contribution result = contributions.reject(
                        uuid(requiredString(event, "id"), "contribution ID"),
                        event.getUser().getId(),
                        requiredString(event, "reason")
                );
                notifier.contributionReviewed(event.getGuild(), result);
                yield views.contributionReviewed(result);
            }
            default -> throw new IllegalArgumentException("Unknown contribution review action.");
        };
    }

    private MessageEmbed xpAdjustment(SlashCommandInteractionEvent event) {
        permissions.requireAdmin(event.getMember());
        String sub = requiredSubcommand(event);
        User targetUser = requiredUser(event, "member");
        Member target = members.ensureMember(targetUser.getId(), targetUser.getName());
        long amount = requiredLong(event, "amount");
        if (amount <= 0) throw new IllegalArgumentException("Amount must be positive.");
        ContributionCategory category = ContributionCategory.parse(requiredString(event, "category"));
        String reason = requiredString(event, "reason");
        long signed = "remove".equals(sub) ? -amount : amount;
        if (signed < 0 && ledger.totalXp(target.id()) < amount) {
            throw new IllegalStateException("This would reduce the member below 0 total CXP.");
        }

        if (signed < 0) {
            ledger.debitXpIfSufficient(target.id(), -signed, category, SourceType.ADMIN_ADJUSTMENT,
                    null, reason, event.getUser().getId());
        } else {
            ledger.addXp(target.id(), signed, category, SourceType.ADMIN_ADJUSTMENT,
                    null, reason, event.getUser().getId());
        }
        audit.log(event.getUser().getId(), "XP_" + sub.toUpperCase(Locale.ROOT), target.discordUserId(),
                "MEMBER", target.id().toString(), signed + " CXP. " + reason);

        List<Achievement> unlocked = signed > 0 ? achievements.evaluate(target, event.getUser().getId()) : List.of();
        rankRoles.sync(event.getGuild(), target.discordUserId());
        notifier.achievements(event.getGuild(), target.discordUserId(), unlocked);
        return views.simple("CXP Adjustment Applied",
                "<@" + target.discordUserId() + "> " + (signed > 0 ? "+" : "") + signed + " CXP.");
    }

    private MessageEmbed creditAdjustment(SlashCommandInteractionEvent event) {
        permissions.requireAdmin(event.getMember());
        String sub = requiredSubcommand(event);
        User targetUser = requiredUser(event, "member");
        Member target = members.ensureMember(targetUser.getId(), targetUser.getName());
        long amount = requiredLong(event, "amount");
        if (amount <= 0) throw new IllegalArgumentException("Amount must be positive.");
        String reason = requiredString(event, "reason");
        long signed = "remove".equals(sub) ? -amount : amount;
        if (signed < 0 && ledger.creditBalance(target.id()) < amount) {
            throw new IllegalStateException("This would reduce the member below 0 Core Credits.");
        }

        if (signed < 0) {
            ledger.debitIfSufficient(target.id(), -signed, SourceType.ADMIN_ADJUSTMENT,
                    null, reason, event.getUser().getId());
        } else {
            ledger.addCredits(target.id(), signed, SourceType.ADMIN_ADJUSTMENT,
                    null, reason, event.getUser().getId());
        }
        audit.log(event.getUser().getId(), "CREDITS_" + sub.toUpperCase(Locale.ROOT), target.discordUserId(),
                "MEMBER", target.id().toString(), signed + " CC. " + reason);
        notifier.economyLog(event.getGuild(), "Core Credit Adjustment",
                "<@" + target.discordUserId() + "> " + (signed > 0 ? "+" : "") + signed + " CC.\nReason: " + reason);
        return views.simple("Core Credit Adjustment Applied",
                "<@" + target.discordUserId() + "> " + (signed > 0 ? "+" : "") + signed + " CC.");
    }

    private MessageEmbed reputation(SlashCommandInteractionEvent event) {
        permissions.requireLeadership(event.getMember());
        User targetUser = requiredUser(event, "member");
        members.ensureMember(targetUser.getId(), targetUser.getName());
        Reputation level = Reputation.valueOf(requiredString(event, "level"));
        Member updated = members.setReputation(
                targetUser.getId(),
                level,
                event.getUser().getId(),
                requiredString(event, "reason"),
                audit
        );
        return views.simple("Reputation Updated",
                "<@" + updated.discordUserId() + "> is now **" + updated.reputation().display() + "**.");
    }

    private MessageEmbed memberManagement(SlashCommandInteractionEvent event) {
        permissions.requireAdmin(event.getMember());
        String sub = requiredSubcommand(event);
        User targetUser = requiredUser(event, "member");
        members.ensureMember(targetUser.getId(), targetUser.getName());
        return switch (sub) {
            case "role" -> {
                Member updated = members.setPrimaryRole(
                        targetUser.getId(), requiredString(event, "role"), event.getUser().getId(), audit);
                yield views.simple("Primary Role Updated",
                        "<@" + updated.discordUserId() + "> is now tracked as **" + value(updated.primaryRole()) + "**.");
            }
            case "activate" -> {
                Member updated = members.setActive(targetUser.getId(), true, event.getUser().getId(), audit);
                yield views.simple("Member Activated", "<@" + updated.discordUserId() + "> is active again.");
            }
            case "deactivate" -> {
                Member updated = members.setActive(targetUser.getId(), false, event.getUser().getId(), audit);
                yield views.simple("Member Deactivated",
                        "<@" + updated.discordUserId() + "> is excluded from active leaderboards.");
            }
            default -> throw new IllegalArgumentException("Unknown member management action.");
        };
    }

    private MessageEmbed audit(SlashCommandInteractionEvent event) {
        permissions.requireAdmin(event.getMember());
        User target = user(event, "member", null);
        int limit = (int) Math.min(25, Math.max(1, nullableLong(event, "limit") == null ? 15 : nullableLong(event, "limit")));
        List<AuditEntry> entries = audit.recent(target == null ? null : target.getId(), limit);
        String body = entries.stream()
                .map(a -> "`" + a.createdAt().toString() + "` **" + a.action() + "**"
                        + (a.targetDiscordId() == null ? "" : " → <@" + a.targetDiscordId() + ">")
                        + "\n" + truncate(a.details(), 250))
                .collect(Collectors.joining("\n\n"));
        return new EmbedBuilder()
                .setTitle("CoreBot Audit Log")
                .setDescription(truncate(body.isBlank() ? "No audit records found." : body, 4000))
                .build();
    }

    private MessageEmbed setup(SlashCommandInteractionEvent event) {
        permissions.requireAdmin(event.getMember());
        Guild guild = event.getGuild();
        String sub = requiredSubcommand(event);
        if ("roles".equals(sub)) {
            List<String> created = rankRoles.createMissingRoles(guild);
            return views.simple("Progression Roles Ready",
                    created.isEmpty() ? "All progression roles already existed."
                            : "Created: " + String.join(", ", created)
                            + "\n\nMove these roles below the bot's highest role so CoreBot can assign them.");
        }
        if ("channels".equals(sub)) {
            List<String> wanted = List.of(
                    properties.getStaffApprovalChannel(),
                    properties.getContributionLogChannel(),
                    properties.getEconomyLogChannel(),
                    properties.getAchievementChannel(),
                    properties.getLeaderboardChannel(),
                    "core-profiles",
                    "projects",
                    "missions",
                    "core-shop"
            );
            List<String> created = new ArrayList<>();
            for (String name : wanted.stream().filter(Objects::nonNull).filter(s -> !s.isBlank()).distinct().toList()) {
                if (guild.getTextChannelsByName(name, true).isEmpty()) {
                    guild.createTextChannel(name).complete();
                    created.add(name);
                }
            }
            return views.simple("CoreBot Channels Ready",
                    created.isEmpty() ? "All standard channels already existed."
                            : "Created: " + created.stream().map(s -> "#" + s).collect(Collectors.joining(", ")));
        }
        throw new IllegalArgumentException("Unknown setup action.");
    }

    private void afterContributionReward(Guild guild, Contribution result) {
        Member member = members.requireByDiscordId(result.discordUserId());
        List<Achievement> unlocked = achievements.evaluate(member, result.reviewerDiscordId());
        rankRoles.sync(guild, member.discordUserId());
        notifier.contributionReviewed(guild, result);
        notifier.achievements(guild, member.discordUserId(), unlocked);
    }

    private MessageEmbed projectList() {
        List<DiscordViewFactory.ProjectSummary> summaries = projects.listActive(20).stream()
                .map(project -> new DiscordViewFactory.ProjectSummary(project, projects.memberCount(project.id())))
                .toList();
        return views.projectList(summaries);
    }

    private MessageEmbed projectView(UUID id) {
        Project project = projects.get(id);
        return views.projectView(project, projects.tasks(id), projects.memberCount(id));
    }

    private MessageEmbed missionList() {
        List<DiscordViewFactory.MissionSummary> summaries = missions.listActive(20).stream()
                .map(mission -> new DiscordViewFactory.MissionSummary(mission, missions.memberCount(mission.id())))
                .toList();
        return views.missionList(summaries);
    }

    private MessageEmbed missionView(UUID id) {
        Mission mission = missions.get(id);
        return views.missionView(mission, missions.participants(id));
    }

    private boolean isAllowedGuild(Guild guild) {
        return guild != null && guild.getId().equals(properties.getGuildId());
    }

    private static boolean isEphemeral(SlashCommandInteractionEvent event) {
        return switch (event.getName()) {
            case "profile", "leaderboard", "achievements", "stats", "shop" -> false;
            case "core" -> !Set.of("profile", "leaderboard", "achievements", "stats", "newplayers")
                    .contains(event.getSubcommandName());
            case "project" -> !Set.of("list", "view").contains(event.getSubcommandName());
            case "mission" -> !Set.of("list", "view").contains(event.getSubcommandName());
            default -> true;
        };
    }

    private static List<ContributionCategory> visibleCategories() {
        return Arrays.stream(ContributionCategory.values())
                .filter(c -> c != ContributionCategory.BONUS)
                .toList();
    }

    private static String safeMessage(Throwable error) {
        return ErrorMessages.safe(error, 1500);
    }

    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }
}

package com.corebuilders.bot.minecraft;

import com.corebuilders.bot.model.Models.LeaderboardEntry;
import com.corebuilders.bot.model.Models.ProfileSnapshot;
import com.corebuilders.bot.service.AchievementService;
import com.corebuilders.bot.service.LedgerService;
import com.corebuilders.bot.service.LinkService;
import com.corebuilders.bot.service.MemberService;
import com.corebuilders.bot.service.WebLoginChallengeRepository;
import com.corebuilders.bot.service.WebLoginService;
import com.corebuilders.bot.util.ErrorMessages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class CoreCommand implements CommandExecutor, TabCompleter {
    private final JavaPlugin plugin;
    private final LinkService links;
    private final MemberService members;
    private final AchievementService achievements;
    private final LedgerService ledger;
    private final WebLoginService webLogin;

    public CoreCommand(
            JavaPlugin plugin,
            LinkService links,
            MemberService members,
            AchievementService achievements,
            LedgerService ledger,
            WebLoginService webLogin
    ) {
        this.plugin = plugin;
        this.links = links;
        this.members = members;
        this.achievements = achievements;
        this.ledger = ledger;
        this.webLogin = webLogin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command is intended for players.");
            return true;
        }
        if (!player.hasPermission("corebuilders.use")) {
            player.sendMessage("§cYou do not have permission to use Core Builders commands.");
            return true;
        }

        UUID playerId = player.getUniqueId();
        String playerName = player.getName();
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "help" -> help(player, label);
            case "login" -> {
                if (args.length < 2) {
                    player.sendMessage("§eOpen the Core Builders website, choose Minecraft login, then run §f/"
                            + label + " login <code>§e.");
                    return true;
                }
                async(player, () -> loginMessage(webLogin.verifyFromGame(args[1], playerId, playerName)));
            }
            case "link" -> {
                if (args.length < 2) {
                    player.sendMessage("§eUse §f/link §ein the Core Builders Discord, then run §f/"
                            + label + " link <code>§e.");
                    return true;
                }
                async(player, () -> {
                    String discordId = links.consumeCode(args[1], playerId, playerName);
                    return "§aAccounts linked successfully. Discord user ID: §f" + discordId;
                });
            }
            case "unlink" -> async(player, () -> links.unlinkMinecraft(playerId)
                    ? "§aYour Minecraft account has been unlinked from Core Builders."
                    : "§eThis Minecraft account is not linked.");
            case "profile" -> async(player, () -> {
                String discordId = requireLinked(playerId);
                ProfileSnapshot profile = members.snapshot(discordId, achievements);
                return "§6§lCore Builders Profile\n"
                        + "§fMember: §e" + profile.member().username() + "\n"
                        + "§fRank: §e" + profile.rank().display() + " §7(Level " + profile.level() + ")\n"
                        + "§fCore XP: §b" + profile.totalXp() + " CXP\n"
                        + "§fCore Credits: §a" + profile.credits() + " CC\n"
                        + "§fWeekly XP: §d" + profile.weeklyXp() + " CXP\n"
                        + "§fReputation: §e" + profile.member().reputation().display();
            });
            case "balance" -> async(player, () -> {
                String discordId = requireLinked(playerId);
                ProfileSnapshot profile = members.snapshot(discordId, achievements);
                return "§6Core Builders §8» §fBalance: §a" + profile.credits()
                        + " CC §8| §fCXP: §b" + profile.totalXp();
            });
            case "leaderboard", "top" -> async(player, () -> formatLeaderboard(
                    ledger.leaderboardOverall(plugin.getConfig().getInt("minecraft.leaderboard-size", 10))));
            default -> help(player, label);
        }
        return true;
    }

    private static String loginMessage(WebLoginChallengeRepository.VerificationResult result) {
        return switch (result.status()) {
            case VERIFIED -> "§aWebsite login verified. Return to your browser to finish signing in.";
            case ALREADY_VERIFIED -> "§eThis website login code was already verified. Return to your browser.";
            case INVALID -> "§cInvalid website login code. Generate a new code on the website.";
            case EXPIRED -> "§cThat website login code expired. Generate a new code on the website.";
            case USED -> "§cThat website login code has already been used.";
            case INACTIVE -> "§cYour Core Builders profile is inactive.";
        };
    }

    private String requireLinked(UUID minecraftUuid) {
        return links.findDiscordId(minecraftUuid)
                .orElseThrow(() -> new IllegalStateException(
                        "Your Minecraft account is not linked to Discord. Link Discord from your website account."));
    }

    private String formatLeaderboard(List<LeaderboardEntry> entries) {
        StringBuilder out = new StringBuilder("§6§lCore Builders Leaderboard");
        int position = 1;
        for (LeaderboardEntry entry : entries) {
            out.append("\n§e#").append(position++)
                    .append(" §f").append(entry.username())
                    .append(" §8- §b").append(entry.score()).append(" CXP");
        }
        return out.toString();
    }

    private void async(Player player, CheckedSupplier<String> work) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            String message;
            try {
                message = work.get();
            } catch (Exception error) {
                message = "§c" + ErrorMessages.safe(error, 500);
            }
            String result = message;
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    for (String line : result.split("\\n")) {
                        player.sendMessage(line);
                    }
                }
            });
        });
    }

    private void help(Player player, String label) {
        player.sendMessage("§6§lCore Builders Commands");
        player.sendMessage("§e/" + label + " login <code> §7- Verify a website login");
        player.sendMessage("§e/" + label + " profile §7- View your progression profile");
        player.sendMessage("§e/" + label + " balance §7- View CXP and Core Credits");
        player.sendMessage("§e/" + label + " leaderboard §7- View the top contributors");
        player.sendMessage("§e/" + label + " link <code> §7- Link a Discord profile using the legacy Discord code");
        player.sendMessage("§e/" + label + " unlink §7- Remove your Minecraft link");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> values = List.of(
                    "login", "profile", "balance", "leaderboard", "link", "unlink", "help"
            );
            List<String> result = new ArrayList<>();
            for (String value : values) {
                if (value.startsWith(prefix)) {
                    result.add(value);
                }
            }
            return result;
        }
        return List.of();
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> {
        T get() throws Exception;
    }
}

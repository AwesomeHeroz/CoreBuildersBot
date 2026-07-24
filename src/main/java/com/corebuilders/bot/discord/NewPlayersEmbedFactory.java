package com.corebuilders.bot.discord;

import com.corebuilders.bot.external.NewPlayersResponse;
import com.corebuilders.bot.external.NewPlayerData;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Converts external new-player API data into Discord-safe paged embeds. */
public final class NewPlayersEmbedFactory {
    private static final int PLAYERS_PER_EMBED = 10;

    public List<MessageEmbed> create(
            String server,
            int requestedPage,
            int requestedSize,
            NewPlayersResponse response
    ) {
        List<NewPlayerData> players = response.players();
        int embedCount = Math.max(1, (players.size() + PLAYERS_PER_EMBED - 1) / PLAYERS_PER_EMBED);
        List<MessageEmbed> embeds = new ArrayList<>(embedCount);

        for (int embedIndex = 0; embedIndex < embedCount; embedIndex++) {
            int from = embedIndex * PLAYERS_PER_EMBED;
            int to = Math.min(players.size(), from + PLAYERS_PER_EMBED);

            EmbedBuilder builder = new EmbedBuilder()
                    .setTitle("New Players — " + server)
                    .setDescription(metadata(server, requestedPage, requestedSize, response, embedIndex, embedCount))
                    .setFooter("Returned " + players.size() + " player(s) on this API page");

            if (players.isEmpty()) {
                builder.addField("Players", "No players were returned for this page.", false);
            } else {
                for (int index = from; index < to; index++) {
                    NewPlayerData player = players.get(index);
                    builder.addField(
                            "#" + (index + 1) + " " + valueOrNull(player.playerName()),
                            playerDetails(player),
                            false
                    );
                }
            }
            embeds.add(builder.build());
        }
        return List.copyOf(embeds);
    }

    static String metadata(
            String server,
            int requestedPage,
            int requestedSize,
            NewPlayersResponse response,
            int embedIndex,
            int embedCount
    ) {
        StringBuilder metadata = new StringBuilder()
                .append("**Request arguments**\n")
                .append("Server: `").append(server).append("`\n")
                .append("Page: `").append(requestedPage).append("`\n")
                .append("Size: `").append(requestedSize).append("`\n\n")
                .append("**API pagination**\n")
                .append("Page: `").append(response.page()).append("`\n")
                .append("Size: `").append(response.size()).append("`\n")
                .append("Total: `").append(response.total()).append("`\n")
                .append("Pages: `").append(response.pages()).append("`");
        if (embedCount > 1) {
            metadata.append("\n\nResult section: `")
                    .append(embedIndex + 1)
                    .append("/")
                    .append(embedCount)
                    .append("`");
        }
        return metadata.toString();
    }

    static String playerDetails(NewPlayerData player) {
        return "Player UUID: `" + valueOrNull(player.playerUuid()) + "`\n"
                + "Player name: `" + valueOrNull(player.playerName()) + "`\n"
                + "Online: `" + player.online() + "`\n"
                + "First join: `" + valueOrNull(player.firstJoin()) + "`\n"
                + "Last join: `" + valueOrNull(player.lastJoin()) + "`\n"
                + "Last seen: `" + valueOrNull(player.lastSeen()) + "`\n"
                + "Age: `" + formatSeconds(player.ageSeconds()) + "`\n"
                + "Priority queue: `" + player.prioQueue() + "`";
    }

    public static String formatSeconds(long seconds) {
        Duration duration = Duration.ofSeconds(Math.max(0, seconds));
        return String.format(
                "%d days, %d hours, %d minutes, %d seconds",
                duration.toDays(),
                duration.toHoursPart(),
                duration.toMinutesPart(),
                duration.toSecondsPart()
        );
    }

    private static String valueOrNull(String value) {
        return value == null || value.isBlank() ? "null" : value;
    }
}

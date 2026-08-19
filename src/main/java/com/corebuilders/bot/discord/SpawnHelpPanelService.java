package com.corebuilders.bot.discord;

import com.corebuilders.bot.config.SpawnHelpConfig;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.util.Objects;
import java.util.function.Consumer;

/** Creates or refreshes the permanent spawn-help request panel. */
public final class SpawnHelpPanelService {
    public static final String REQUEST_BUTTON_ID = "spawnhelp:request";
    private static final Logger log = LoggerFactory.getLogger(SpawnHelpPanelService.class);

    private final SpawnHelpConfig config;
    private final String guildId;
    private final Consumer<String> messageIdStore;

    public SpawnHelpPanelService(SpawnHelpConfig config, String guildId, Consumer<String> messageIdStore) {
        this.config = Objects.requireNonNull(config, "config");
        this.guildId = Objects.requireNonNull(guildId, "guildId");
        this.messageIdStore = Objects.requireNonNull(messageIdStore, "messageIdStore");
    }

    public void setupPanel(JDA jda) {
        if (!config.enabled()) return;
        try {
            Guild guild = jda.getGuildById(guildId);
            if (guild == null) throw new IllegalStateException("Configured Discord guild is unavailable: " + guildId);
            TextChannel channel = resolveChannel(guild, config.panelChannel());
            var embed = new EmbedBuilder()
                    .setTitle(config.panelTitle())
                    .setDescription(config.panelDescription())
                    .setColor(new Color(52, 152, 219))
                    .build();
            Button button = Button.primary(REQUEST_BUTTON_ID, config.panelButtonLabel());

            if (!config.panelMessageId().isBlank()) {
                channel.retrieveMessageById(config.panelMessageId()).queue(
                        message -> message.editMessageEmbeds(embed).setComponents(ActionRow.of(button)).queue(),
                        error -> {
                            log.warn("Configured spawn-help panel message {} was not found in #{}. Creating a replacement.",
                                    config.panelMessageId(), channel.getName());
                            createPanel(channel, embed, button);
                        }
                );
            } else {
                createPanel(channel, embed, button);
            }
        } catch (Exception error) {
            log.warn("Spawn-help panel could not be created: {}", error.getMessage());
        }
    }

    private void createPanel(TextChannel channel, net.dv8tion.jda.api.entities.MessageEmbed embed, Button button) {
        channel.sendMessageEmbeds(embed)
                .setComponents(ActionRow.of(button))
                .queue(message -> {
                    messageIdStore.accept(message.getId());
                    log.info("Created spawn-help panel in #{} with message ID {}.", channel.getName(), message.getId());
                });
    }

    private static TextChannel resolveChannel(Guild guild, String reference) {
        TextChannel byId = reference.matches("\\d{15,22}") ? guild.getTextChannelById(reference) : null;
        if (byId != null) return byId;
        return guild.getTextChannelsByName(reference, true).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "discord.spawn-help.panel.channel could not be resolved as a text-channel ID or exact name: " + reference
                ));
    }
}

package com.corebuilders.bot.discord;

import com.corebuilders.bot.config.ApplicationPanelConfig;
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

/** Creates or refreshes the permanent Discord application entry panel. */
public final class ApplicationPanelService {
    public static final String APPLY_BUTTON_ID = "app:apply";
    private static final Logger log = LoggerFactory.getLogger(ApplicationPanelService.class);

    private final ApplicationPanelConfig config;
    private final String guildId;
    private final Consumer<String> messageIdStore;

    public ApplicationPanelService(
            ApplicationPanelConfig config,
            String guildId,
            Consumer<String> messageIdStore
    ) {
        this.config = Objects.requireNonNull(config, "config");
        this.guildId = guildId == null ? "" : guildId.trim();
        this.messageIdStore = Objects.requireNonNull(messageIdStore, "messageIdStore");
    }

    public void setupPanel(JDA jda) {
        if (!config.enabled()) return;

        TextChannel channel;
        try {
            channel = resolveChannel(jda, config.channel());
        } catch (IllegalStateException error) {
            log.warn("Application entry panel is enabled but could not be created: {}", error.getMessage());
            return;
        }

        var embed = new EmbedBuilder()
                .setTitle(config.title())
                .setDescription(config.description())
                .setColor(new Color(88, 101, 242))
                .build();
        Button applyButton = Button.primary(APPLY_BUTTON_ID, config.buttonLabel());

        if (!config.messageId().isBlank()) {
            channel.retrieveMessageById(config.messageId()).queue(
                    message -> message.editMessageEmbeds(embed)
                            .setComponents(ActionRow.of(applyButton))
                            .queue(),
                    failure -> {
                        log.warn(
                                "Configured application panel message {} was not found in #{}. Creating a new panel.",
                                config.messageId(), channel.getName()
                        );
                        createPanel(channel, embed, applyButton);
                    }
            );
            return;
        }

        createPanel(channel, embed, applyButton);
    }

    private TextChannel resolveChannel(JDA jda, String reference) {
        if (!guildId.isBlank()) {
            Guild guild = jda.getGuildById(guildId);
            if (guild == null) {
                throw new IllegalStateException("Configured Discord guild is not available: " + guildId);
            }
            return resolveChannel(guild, reference);
        }

        TextChannel byId = isSnowflake(reference) ? jda.getTextChannelById(reference) : null;
        if (byId != null) return byId;
        return jda.getTextChannelsByName(reference, true).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "applications.entry-panel.channel could not be resolved: " + reference
                ));
    }

    private static TextChannel resolveChannel(Guild guild, String reference) {
        TextChannel byId = isSnowflake(reference) ? guild.getTextChannelById(reference) : null;
        if (byId != null) return byId;
        return guild.getTextChannelsByName(reference, true).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "applications.entry-panel.channel could not be resolved in guild '"
                                + guild.getName() + "': " + reference
                ));
    }

    private void createPanel(TextChannel channel, net.dv8tion.jda.api.entities.MessageEmbed embed, Button button) {
        channel.sendMessageEmbeds(embed)
                .addComponents(ActionRow.of(button))
                .queue(message -> {
                    messageIdStore.accept(message.getId());
                    log.info("Created application panel in #{} with message ID {}.", channel.getName(), message.getId());
                });
    }

    private static boolean isSnowflake(String value) {
        return value != null && value.matches("\\d{15,22}");
    }
}

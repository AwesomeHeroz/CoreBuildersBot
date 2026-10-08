package com.corebuilders.bot.discord;

import com.corebuilders.bot.config.BuildChallengeConfig;
import com.corebuilders.bot.service.BuildChallengeService;
import com.corebuilders.bot.service.BuildChallengeService.Standing;
import com.corebuilders.bot.service.BuildChallengeService.Submission;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.attachmentupload.AttachmentUpload;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.utils.FileUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Discord UI and moderation flow for the independent build challenge. */
public final class BuildChallengeDiscordListener extends ListenerAdapter implements AutoCloseable {
    public static final String APPLY_BUTTON_ID = "buildchallenge:apply";
    private static final Logger log = LoggerFactory.getLogger(BuildChallengeDiscordListener.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final EnumSet<Permission> PARTICIPANT_PERMISSIONS = EnumSet.of(
            Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY,
            Permission.MESSAGE_ATTACH_FILES, Permission.MESSAGE_EMBED_LINKS);

    private final BuildChallengeConfig config;
    private final BuildChallengeService service;
    private final String guildId;
    private final Consumer<String> panelMessageIdStore;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    public BuildChallengeDiscordListener(BuildChallengeConfig config, BuildChallengeService service,
                                         String guildId, Consumer<String> panelMessageIdStore) {
        this.config = config;
        this.service = service;
        this.guildId = guildId == null ? "" : guildId.trim();
        this.panelMessageIdStore = panelMessageIdStore;
    }

    public Set<String> handledCommandNames() { return Set.of("buildchallenge"); }

    public void setup(JDA jda) {
        if (!config.enabled()) return;
        Guild guild = jda.getGuildById(guildId);
        if (guild == null) { log.warn("Build challenge enabled but configured guild is unavailable."); return; }
        List<String> problems = new ArrayList<>();
        if (guild.getTextChannelById(config.submissionsChannelId()) == null) problems.add("submissions-channel-id");
        if (!config.announcementChannelId().isBlank() && guild.getTextChannelById(config.announcementChannelId()) == null) problems.add("announcement-channel-id");
        if (guild.getCategoryById(config.prizeCategoryId()) == null) problems.add("prize.category-id");
        if (config.judgeRoleIds().stream().noneMatch(id -> guild.getRoleById(id) != null)) problems.add("judge-role-ids");
        if (config.panelEnabled() && guild.getTextChannelById(config.panelChannelId()) == null) problems.add("entry-panel.channel-id");
        if (!problems.isEmpty()) {
            log.warn("Build challenge configuration has unresolved Discord resources: {}", problems);
            return;
        }
        if (config.panelEnabled()) setupPanel(guild);
    }

    private void setupPanel(Guild guild) {
        TextChannel channel = guild.getTextChannelById(config.panelChannelId());
        var embed = new EmbedBuilder().setTitle(config.panelTitle()).setDescription(config.panelDescription()).build();
        Button button = Button.primary(APPLY_BUTTON_ID, config.panelButtonLabel());
        if (!config.panelMessageId().isBlank()) {
            channel.retrieveMessageById(config.panelMessageId()).queue(
                    msg -> msg.editMessageEmbeds(embed).setComponents(ActionRow.of(button)).queue(),
                    fail -> createPanel(channel, embed, button));
        } else createPanel(channel, embed, button);
    }

    private void createPanel(TextChannel channel, net.dv8tion.jda.api.entities.MessageEmbed embed, Button button) {
        channel.sendMessageEmbeds(embed).addComponents(ActionRow.of(button)).queue(msg -> {
            panelMessageIdStore.accept(msg.getId());
            log.info("Created build challenge entry panel in #{} ({})", channel.getName(), msg.getId());
        });
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        if (!APPLY_BUTTON_ID.equals(event.getComponentId())) return;
        if (!available(event.getGuild())) { event.reply("Build challenge submissions are unavailable here.").setEphemeral(true).queue(); return; }
        if (config.preventDuplicateSubmission() && service.latestForUser(event.getUser().getId()).isPresent()) {
            event.reply("You already have a build challenge submission on record.").setEphemeral(true).queue();
            return;
        }
        event.replyModal(submissionModal()).queue();
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!"buildchallenge".equals(event.getName())) return;
        if (!available(event.getGuild())) { event.reply("Build challenge is unavailable here.").setEphemeral(true).queue(); return; }
        try {
            String sub = event.getSubcommandName();
            if ("submit".equals(sub)) event.replyModal(submissionModal()).queue();
            else if ("status".equals(sub)) status(event);
            else if ("score".equals(sub)) score(event);
            else if ("standings".equals(sub)) standings(event);
            else if ("announce-winners".equals(sub)) announceWinners(event);
            else if ("set-winner".equals(sub)) setWinner(event);
            else event.reply("Unknown build challenge command.").setEphemeral(true).queue();
        } catch (Exception e) {
            event.reply("❌ " + safe(e)).setEphemeral(true).queue();
        }
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        if (!"buildchallenge:submission".equals(event.getModalId())) return;
        if (!available(event.getGuild())) { event.reply("Build challenge is unavailable here.").setEphemeral(true).queue(); return; }
        event.deferReply(true).queue(hook -> executor.submit(() -> {
            try {
                if (config.preventDuplicateSubmission() && service.latestForUser(event.getUser().getId()).isPresent())
                    throw new IllegalStateException("You already have a build challenge submission on record.");
                String ign = requiredText(event, "ign");
                String coords = requiredText(event, "coordinates");
                ModalMapping upload = event.getValue("screenshots");
                List<Message.Attachment> screenshots = upload == null ? List.of() : List.copyOf(upload.getAsAttachmentList());
                if (screenshots.isEmpty()) throw new IllegalArgumentException("At least one screenshot is required.");
                if (screenshots.size() > config.maxScreenshots()) throw new IllegalArgumentException("Too many screenshots.");

                TextChannel destination = event.getGuild().getTextChannelById(config.submissionsChannelId());
                UUID id = UUID.randomUUID();
                List<String> urls = preserveScreenshots(destination, id, screenshots);
                Submission submission = service.create(id, event.getUser().getId(), event.getUser().getName(), ign, coords, urls,
                        config.preventDuplicateSubmission());
                Message packet = destination.sendMessageEmbeds(submissionEmbed(submission)).complete();
                submission = service.setMessage(id, destination.getId(), packet.getId());
                hook.editOriginal("✅ " + config.submittedMessage() + "\nSubmission ID: `" + submission.id() + "`").queue();
            } catch (Exception e) {
                hook.editOriginal("❌ " + safe(e)).queue();
            }
        }));
    }

    private Modal submissionModal() {
        TextInput ign = TextInput.create("ign", TextInputStyle.SHORT).setRequired(true).setMinLength(2).setMaxLength(100)
                .setPlaceholder("Minecraft IGN").build();
        TextInput coords = TextInput.create("coordinates", TextInputStyle.SHORT).setRequired(true).setMinLength(3).setMaxLength(255)
                .setPlaceholder("Example: X 1200, Y 80, Z -4500 / dimension").build();
        AttachmentUpload screenshots = AttachmentUpload.create("screenshots").setRequired(true).setMinValues(1).setMaxValues(config.maxScreenshots()).build();
        return Modal.create("buildchallenge:submission", "Build Challenge Submission")
                .addComponents(Label.of("Minecraft IGN", ign),
                        Label.of("Exact build coordinates", "Include dimension if relevant.", coords),
                        Label.of("Build screenshots", "Upload clear screenshots of your build.", screenshots)).build();
    }

    private void status(SlashCommandInteractionEvent event) {
        var submission = service.latestForUser(event.getUser().getId());
        if (submission.isEmpty()) event.reply("You have not submitted a build challenge entry yet.").setEphemeral(true).queue();
        else event.replyEmbeds(submissionEmbed(submission.get())).setEphemeral(true).queue();
    }

    private void score(SlashCommandInteractionEvent event) {
        requireJudge(event.getMember());
        UUID submissionId = UUID.fromString(event.getOption("submission").getAsString());
        int value = event.getOption("score").getAsInt();
        String notes = event.getOption("notes") == null ? "" : event.getOption("notes").getAsString();
        service.score(submissionId, event.getUser().getId(), value, notes);
        event.reply("✅ Score saved: **" + value + "/100** for `" + submissionId + "`.").setEphemeral(true).queue();
    }

    private void standings(SlashCommandInteractionEvent event) {
        requireJudge(event.getMember());
        List<Standing> standings = service.standings();
        if (standings.isEmpty()) { event.reply("No judged build challenge submissions yet.").setEphemeral(true).queue(); return; }
        StringBuilder text = new StringBuilder("**Build Challenge Judge Averages**\n");
        int rank = 1;
        for (Standing s : standings.stream().limit(20).toList()) {
            text.append(rank++).append(". **").append(escape(s.submission().ign())).append("** — ")
                    .append(String.format(Locale.ROOT, "%.2f", s.averageScore())).append("/100")
                    .append(" (").append(s.judgeCount()).append(" judge").append(s.judgeCount() == 1 ? "" : "s").append(")")
                    .append(" — `").append(s.submission().id()).append("`\n");
        }
        event.reply(text.toString()).setEphemeral(true).queue();
    }

    private void announceWinners(SlashCommandInteractionEvent event) {
        requireJudge(event.getMember());
        if (config.announcementChannelId().isBlank()) throw new IllegalStateException("build-challenge.announcement-channel-id is not configured.");
        TextChannel channel = event.getGuild().getTextChannelById(config.announcementChannelId());
        if (channel == null) throw new IllegalStateException("Configured build challenge announcement channel is unavailable.");
        StringBuilder text = new StringBuilder("🏆 **Core Builders Build Challenge Winners** 🏆\n\n");
        for (int place = 1; place <= 3; place++) {
            int currentPlace = place;
            var winner = service.winner(currentPlace).orElseThrow(() -> new IllegalStateException("Winner #" + currentPlace + " has not been assigned yet."));
            Submission submission = service.get(winner.submissionId());
            text.append("**#").append(currentPlace).append(" — ").append(escape(submission.ign())).append("** (<@")
                    .append(winner.winnerDiscordId()).append(">)\n");
        }
        channel.sendMessage(text.toString()).queue();
        event.reply("✅ Published the assigned build challenge winners in " + channel.getAsMention() + ".").setEphemeral(true).queue();
    }

    private void setWinner(SlashCommandInteractionEvent event) {
        requireJudge(event.getMember());
        int place = event.getOption("place").getAsInt();
        if (place < 1 || place > 3) throw new IllegalArgumentException("Winner place must be 1, 2, or 3.");
        User winnerUser = event.getOption("user").getAsUser();
        Submission submission = service.latestForUser(winnerUser.getId())
                .orElseThrow(() -> new IllegalArgumentException("That user has no build challenge submission."));
        if (service.winner(place).isPresent()) throw new IllegalStateException("Winner place " + place + " is already assigned.");

        event.deferReply(true).queue(hook -> executor.submit(() -> {
            TextChannel channel = null;
            try {
                Guild guild = event.getGuild();
                Member winner = guild.retrieveMemberById(winnerUser.getId()).complete();
                Category category = guild.getCategoryById(config.prizeCategoryId());
                if (category == null) throw new IllegalStateException("Configured prize category is unavailable.");
                String code = makeClaimCode(place);
                channel = createPrizeChannel(category, winner, event.getGuild().getSelfMember(), place);
                service.assignWinner(place, submission, code, channel.getId(), event.getUser().getId());

                String shopLine = config.frostShopUrl().isBlank() ? "" : "\nFrost Shop: " + config.frostShopUrl();
                channel.sendMessage("🏆 Congratulations " + winner.getAsMention() + "! You placed **#" + place + "** in the build challenge."
                        + "\nYour prize claim code is: `" + code + "`" + shopLine
                        + "\nUse this private channel to discuss prize claiming, including priority queue payment if needed.").complete();
                TextChannel prizeChannel = channel;
                winnerUser.openPrivateChannel().flatMap(dm -> dm.sendMessage("You placed #" + place + " in the Core Builders build challenge! Prize channel: " + prizeChannel.getAsMention() + "\nClaim code: `" + code + "`" + shopLine)).queue(ignored -> {}, ignored -> {});
                hook.editOriginal("✅ Set " + winnerUser.getAsMention() + " as **#" + place + "**. Prize channel: " + channel.getAsMention()).queue();
            } catch (Exception e) {
                if (channel != null) try { channel.delete().complete(); } catch (Exception ignored) { }
                hook.editOriginal("❌ " + safe(e)).queue();
            }
        }));
    }

    private TextChannel createPrizeChannel(Category category, Member winner, Member bot, int place) {
        String name = config.prizeChannelNamePattern().replace("{place}", String.valueOf(place))
                .replace("{username}", winner.getUser().getName()).toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9-]", "-").replaceAll("-+", "-");
        var action = category.createTextChannel(name)
                .addPermissionOverride(category.getGuild().getPublicRole(), Collections.emptySet(), EnumSet.of(Permission.VIEW_CHANNEL))
                .addPermissionOverride(winner, PARTICIPANT_PERMISSIONS, Collections.emptySet())
                .addPermissionOverride(bot, EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY, Permission.MANAGE_CHANNEL), Collections.emptySet());
        for (String roleId : config.judgeRoleIds()) {
            Role role = category.getGuild().getRoleById(roleId);
            if (role != null) action.addPermissionOverride(role, PARTICIPANT_PERMISSIONS, Collections.emptySet());
        }
        return action.complete();
    }

    private List<String> preserveScreenshots(TextChannel channel, UUID submissionId, List<Message.Attachment> attachments) throws Exception {
        long declared = attachments.stream().mapToLong(Message.Attachment::getSize).sum();
        if (declared > config.maxTotalUploadSizeBytes()) throw new IllegalArgumentException("Screenshots exceed the configured total upload limit.");
        List<Path> temp = new ArrayList<>();
        List<FileUpload> uploads = new ArrayList<>();
        long total = 0;
        try {
            for (Message.Attachment a : attachments) {
                if (a.getSize() > config.maxFileSizeBytes()) throw new IllegalArgumentException("Screenshot '" + a.getFileName() + "' is too large.");
                URI uri = URI.create(a.getUrl());
                String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
                if (!"https".equalsIgnoreCase(uri.getScheme()) || !(host.equals("cdn.discordapp.com") || host.equals("media.discordapp.net")))
                    throw new IllegalArgumentException("Untrusted screenshot URL.");
                Path path = Files.createTempFile("corebuilders-buildchallenge-", ".upload"); temp.add(path);
                HttpResponse<InputStream> response = http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(45)).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IllegalStateException("Could not preserve screenshot '" + a.getFileName() + "'.");
                try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(path)) {
                    byte[] buf = new byte[64 * 1024]; int n; long file = 0;
                    while ((n = in.read(buf)) >= 0) { file += n; total += n; if (file > config.maxFileSizeBytes() || total > config.maxTotalUploadSizeBytes()) throw new IllegalArgumentException("Screenshot upload limit exceeded."); out.write(buf, 0, n); }
                }
                uploads.add(FileUpload.fromData(path, safeFilename(a.getFileName())));
            }
            Message evidence = channel.sendFiles(uploads).setContent("**Build Challenge `" + submissionId + "` — screenshots**").complete();
            return evidence.getAttachments().stream().map(Message.Attachment::getUrl).toList();
        } finally {
            for (FileUpload upload : uploads) try { upload.close(); } catch (Exception ignored) { }
            for (Path path : temp) try { Files.deleteIfExists(path); } catch (Exception ignored) { }
        }
    }

    private net.dv8tion.jda.api.entities.MessageEmbed submissionEmbed(Submission s) {
        EmbedBuilder b = new EmbedBuilder().setTitle("Build Challenge Submission — " + s.ign())
                .addField("Entrant", "<@" + s.discordUserId() + "> (`" + s.discordUserId() + "`)", false)
                .addField("IGN", escape(s.ign()), true)
                .addField("Coordinates", "`" + escape(s.coordinates()) + "`", true)
                .addField("Submission ID", "`" + s.id() + "`", false)
                .setFooter("Judges: /buildchallenge score submission:<id> score:<0-100>");
        if (!s.screenshotUrls().isEmpty()) {
            b.addField("Screenshots", s.screenshotUrls().size() + " preserved screenshot(s). See the evidence upload posted with this submission.", false);
            b.setImage(s.screenshotUrls().getFirst());
        }
        return b.build();
    }

    private boolean available(Guild guild) { return config.enabled() && guild != null && (guildId.isBlank() || guild.getId().equals(guildId)); }
    private void requireJudge(Member member) {
        if (member == null || member.getRoles().stream().noneMatch(r -> config.judgeRoleIds().contains(r.getId())))
            throw new SecurityException("This command is restricted to the configured judge panel.");
    }
    private static String requiredText(ModalInteractionEvent event, String id) {
        ModalMapping m = event.getValue(id);
        String v = m == null || m.getAsOptionalString() == null ? "" : m.getAsOptionalString().trim();
        if (v.isBlank()) throw new IllegalArgumentException("Required field missing: " + id); return v;
    }
    private String makeClaimCode(int place) {
        StringBuilder s = new StringBuilder(config.codePrefix()).append('-').append(place).append('-');
        for (int i = 0; i < 10; i++) s.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
        return s.toString();
    }
    private static String safeFilename(String name) { return (name == null ? "screenshot.png" : name).replaceAll("[^A-Za-z0-9._-]", "_"); }
    private static String escape(String v) { return v == null ? "" : v.replace("`", "'").replace("*", "\\*").replace("_", "\\_"); }
    private static String safe(Exception e) { String m = e.getMessage(); return m == null || m.isBlank() ? e.getClass().getSimpleName() : m; }
    @Override public void close() { executor.shutdownNow(); }
}

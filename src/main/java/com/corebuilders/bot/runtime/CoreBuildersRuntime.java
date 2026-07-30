package com.corebuilders.bot.runtime;

import com.corebuilders.bot.config.ApplicationConfig;
import com.corebuilders.bot.config.BotProperties;
import com.corebuilders.bot.config.MusicConfig;
import com.corebuilders.bot.config.ProgressionConfig;
import com.corebuilders.bot.config.ShopConfig;
import com.corebuilders.bot.config.WebsiteConfig;
import com.corebuilders.bot.config.ApplicationPanelConfig;
import com.corebuilders.bot.db.QueryDslDatabase;
import com.corebuilders.bot.model.RankCatalog;
import com.corebuilders.bot.minecraft.MinecraftIdentityPolicy;
import com.corebuilders.bot.model.ShopCatalog;
import com.corebuilders.bot.persistence.QueryDslWebLoginChallengeRepository;
import com.corebuilders.bot.discord.ApplicationDiscordListener;
import com.corebuilders.bot.discord.ApplicationPanelService;
import com.corebuilders.bot.discord.CommandRegistrar;
import com.corebuilders.bot.discord.DiscordBotListener;
import com.corebuilders.bot.discord.DiscordNotifier;
import com.corebuilders.bot.discord.PermissionService;
import com.corebuilders.bot.discord.RankRoleService;
import com.corebuilders.bot.discord.music.DiscordAudioBootstrap;
import com.corebuilders.bot.discord.music.MusicDiscordListener;
import com.corebuilders.bot.discord.music.MusicService;
import com.corebuilders.bot.external.CachingNewPlayersProvider;
import com.corebuilders.bot.external.HyperglidingClient;
import com.corebuilders.bot.external.NewPlayersProvider;
import com.corebuilders.bot.external.HyperglidingConfig;
import com.corebuilders.bot.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zaxxer.hikari.HikariDataSource;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.bukkit.plugin.java.JavaPlugin;
import com.corebuilders.bot.web.MarketplaceHttpServer;
import com.corebuilders.bot.web.auth.CoreWebsiteIdentity;
import com.corebuilders.bot.web.auth.DiscordOAuthHttpClient;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Plain-Java application wiring for the Paper plugin.
 *
 * No Spring container is used. All dependencies are created explicitly so the
 * plugin remains predictable inside Paper's isolated classloader.
 */
public final class CoreBuildersRuntime implements AutoCloseable {
    private final Logger logger;
    private final HikariDataSource dataSource;
    private final DiscordBotListener discordListener;
    private final ApplicationDiscordListener applicationListener;
    private final MusicDiscordListener musicListener;
    private final JDA jda;
    private final CommandRegistrar commandRegistrar;
    private final MarketplaceHttpServer websiteServer;

    private final LinkService linkService;
    private final MemberService memberService;
    private final AchievementService achievementService;
    private final LedgerService ledgerService;
    private final WebLoginService webLoginService;

    private CoreBuildersRuntime(
            Logger logger,
            HikariDataSource dataSource,
            DiscordBotListener discordListener,
            ApplicationDiscordListener applicationListener,
            MusicDiscordListener musicListener,
            JDA jda,
            CommandRegistrar commandRegistrar,
            MarketplaceHttpServer websiteServer,
            LinkService linkService,
            MemberService memberService,
            AchievementService achievementService,
            LedgerService ledgerService,
            WebLoginService webLoginService
    ) {
        this.logger = logger;
        this.dataSource = dataSource;
        this.discordListener = discordListener;
        this.applicationListener = applicationListener;
        this.musicListener = musicListener;
        this.jda = jda;
        this.commandRegistrar = commandRegistrar;
        this.websiteServer = websiteServer;
        this.linkService = linkService;
        this.memberService = memberService;
        this.achievementService = achievementService;
        this.ledgerService = ledgerService;
        this.webLoginService = webLoginService;
    }

    public static CoreBuildersRuntime start(JavaPlugin plugin) throws InterruptedException {
        BotProperties properties = new BotProperties(plugin.getConfig());
        RankCatalog ranks = ProgressionConfig.from(plugin.getConfig());
        ShopCatalog shopCatalog = ShopConfig.from(plugin.getConfig());
        WebsiteConfig websiteConfig = WebsiteConfig.from(plugin.getConfig());
        MinecraftIdentityPolicy.validate(plugin, websiteConfig);
        HikariDataSource dataSource = null;
        DiscordBotListener listener = null;
        ApplicationDiscordListener applicationListener = null;
        MusicDiscordListener musicListener = null;
        MarketplaceHttpServer websiteServer = null;
        JDA jda = null;

        try {
            dataSource = DatabaseBootstrap.start(plugin);

            QueryDslDatabase database = new QueryDslDatabase(dataSource);
            ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

            LedgerService ledger = new LedgerService(database);
            AuditService audit = new AuditService(database);
            MemberService members = new MemberService(database, ledger, ranks);
            ContributionService contributions = new ContributionService(database, ledger, audit);
            AchievementService achievements = new AchievementService(database, ledger, contributions, audit);
            ProjectService projects = new ProjectService(database, ledger, audit);
            MissionService missions = new MissionService(database, ledger, audit);
            ShopService shop = new ShopService(database, ledger, audit);
            MarketplaceService marketplace = new MarketplaceService(
                    database, ledger, audit, websiteConfig.allowedImageHosts());
            ShopService.CatalogSyncResult shopSync = shop.synchronizeCatalog(shopCatalog);
            plugin.getLogger().info("Shop catalog synchronized: " + shopSync.inserted() + " inserted, "
                    + shopSync.updated() + " updated, " + shopSync.disabled() + " disabled.");
            LinkService links = new LinkService(database);
            WebLoginService webLogin = new WebLoginService(
                    new QueryDslWebLoginChallengeRepository(database),
                    java.time.Duration.ofMinutes(10)
            );
            ApplicationConfig applicationConfig = new ApplicationConfig(plugin.getConfig());
            ApplicationService applications = new ApplicationService(
                    database, objectMapper, audit, applicationConfig.isPreventDuplicatePending()
            );
            ApplicationPanelService applicationPanelService = new ApplicationPanelService(
                    new ApplicationPanelConfig(plugin.getConfig()),
                    properties.getGuildId(),
                    messageId -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                        plugin.getConfig().set("applications.entry-panel.message-id", messageId);
                        plugin.saveConfig();
                    })
            );

            PermissionService permissions = new PermissionService(properties);
            RankRoleService rankRoles = new RankRoleService(members, ledger, ranks);
            DiscordNotifier notifier = new DiscordNotifier(properties);
            HyperglidingClient hyperglidingClient = new HyperglidingClient(new HyperglidingConfig(
                    properties.getHyperglidingApiUrl(),
                    properties.getHyperglidingApiKey(),
                    java.time.Duration.ofSeconds(properties.getHyperglidingTimeoutSeconds())
            ));
            NewPlayersProvider hypergliding = new CachingNewPlayersProvider(
                    hyperglidingClient,
                    java.time.Duration.ofSeconds(properties.getHyperglidingCacheSeconds())
            );

            listener = new DiscordBotListener(
                    members,
                    ledger,
                    contributions,
                    achievements,
                    projects,
                    missions,
                    shop,
                    marketplace,
                    audit,
                    links,
                    permissions,
                    rankRoles,
                    notifier,
                    properties,
                    hypergliding
            );
            applicationListener = new ApplicationDiscordListener(
                    applications,
                    applicationConfig,
                    properties.getGuildId()
            );
            MusicConfig musicConfig = MusicConfig.from(plugin.getConfig());
            MusicService musicService = new MusicService(musicConfig);
            musicListener = new MusicDiscordListener(musicConfig, properties.getGuildId(), musicService);

            String token = properties.getToken();
            if (token == null || token.isBlank()) {
                throw new IllegalStateException(
                        "Discord token is missing. Set discord.token in plugins/CoreBuilders/config.yml "
                                + "or the DISCORD_BOT_TOKEN environment variable."
                );
            }

            JDABuilder jdaBuilder = JDABuilder.createDefault(token)
                    .setActivity(Activity.playing("Core Builders progression"))
                    .addEventListeners(listener, applicationListener, musicListener);

            if (musicConfig.enabled()) {
                jdaBuilder.enableIntents(GatewayIntent.GUILD_VOICE_STATES);
                DiscordAudioBootstrap.configure(jdaBuilder);
                plugin.getLogger().info("Discord music is enabled with DAVE voice encryption.");
            }

            if (properties.isTextCommandsEnabled()) {
                jdaBuilder.enableIntents(GatewayIntent.MESSAGE_CONTENT);
                plugin.getLogger().info(
                        "Discord text commands are enabled with prefix '"
                                + properties.getTextCommandPrefix() + "'."
                );
            }

            jda = jdaBuilder.build().awaitReady();
            plugin.getLogger().info(
                    "Discord bot ready as " + jda.getSelfUser().getName()
                            + " (" + jda.getSelfUser().getId() + "), connected to "
                            + jda.getGuilds().size() + " guild(s)."
            );
            applicationListener.validateConfiguration(jda);
            applicationPanelService.setupPanel(jda);

            if (websiteConfig.enabled()) {
                websiteServer = new MarketplaceHttpServer(
                        websiteConfig,
                        objectMapper,
                        new DiscordOAuthHttpClient(websiteConfig, properties.getGuildId(), objectMapper),
                        new CoreWebsiteIdentity(database, ledger),
                        webLogin,
                        marketplace,
                        plugin.getLogger()
                );
                websiteServer.start();
            } else {
                plugin.getLogger().info("Marketplace website is disabled. Set website.enabled=true to start it.");
            }

            java.util.Set<String> handledCommands = new java.util.LinkedHashSet<>(listener.handledCommandNames());
            handledCommands.addAll(applicationListener.handledCommandNames());
            handledCommands.addAll(musicListener.handledCommandNames());
            CommandRegistrar registrar = new CommandRegistrar(jda, properties, handledCommands);
            return new CoreBuildersRuntime(
                    plugin.getLogger(),
                    dataSource,
                    listener,
                    applicationListener,
                    musicListener,
                    jda,
                    registrar,
                    websiteServer,
                    links,
                    members,
                    achievements,
                    ledger,
                    webLogin
            );
        } catch (Exception error) {
            if (websiteServer != null) {
                websiteServer.close();
            }
            if (jda != null) {
                jda.shutdownNow();
            }
            if (listener != null) {
                listener.shutdown();
            }
            if (applicationListener != null) {
                applicationListener.close();
            }
            if (musicListener != null) {
                musicListener.close();
            }
            if (dataSource != null) {
                dataSource.close();
            }
            if (error instanceof InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw interrupted;
            }
            if (error instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException("Could not initialize Core Builders runtime.", error);
        }
    }

    public CommandRegistrar commandRegistrar() {
        return commandRegistrar;
    }

    public LinkService linkService() {
        return linkService;
    }

    public MemberService memberService() {
        return memberService;
    }

    public AchievementService achievementService() {
        return achievementService;
    }

    public LedgerService ledgerService() {
        return ledgerService;
    }

    public WebLoginService webLoginService() {
        return webLoginService;
    }

    @Override
    public void close() {
        closeQuietly("Music service", musicListener::close);
        if (websiteServer != null) closeQuietly("Marketplace website", websiteServer::close);
        closeQuietly("Discord client", jda::shutdownNow);
        closeQuietly("Discord command listener", discordListener::shutdown);
        closeQuietly("Application listener", applicationListener::close);
        closeQuietly("Database pool", dataSource::close);
    }

    private void closeQuietly(String resource, Runnable action) {
        try {
            action.run();
        } catch (Exception error) {
            logger.log(Level.WARNING, "Could not close " + resource + ": " + error.getMessage(), error);
        }
    }

}

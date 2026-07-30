package com.corebuilders.bot.db;

import com.querydsl.core.types.PathMetadataFactory;
import com.querydsl.core.types.dsl.BooleanPath;
import com.querydsl.core.types.dsl.DateTimePath;
import com.querydsl.core.types.dsl.NumberPath;
import com.querydsl.core.types.dsl.StringPath;
import com.querydsl.sql.RelationalPathBase;

import java.time.LocalDateTime;

/**
 * QueryDSL relational metadata for the Core Builders schema.
 *
 * Keeping all table/column names here prevents services from embedding SQL or schema names.
 * These classes follow the same shape as QueryDSL generated Q-types and can later be replaced
 * by querydsl-sql-codegen output without changing service logic.
 */
public final class Schema {
    private Schema() {}

    public static final QMembers MEMBERS = new QMembers("m");
    public static final QXpTransactions XP = new QXpTransactions("x");
    public static final QCreditTransactions CREDITS = new QCreditTransactions("ct");
    public static final QContributions CONTRIBUTIONS = new QContributions("c");
    public static final QAchievements ACHIEVEMENTS = new QAchievements("a");
    public static final QMemberAchievements MEMBER_ACHIEVEMENTS = new QMemberAchievements("ma");
    public static final QProjects PROJECTS = new QProjects("p");
    public static final QProjectMembers PROJECT_MEMBERS = new QProjectMembers("pm");
    public static final QProjectTasks PROJECT_TASKS = new QProjectTasks("pt");
    public static final QMissions MISSIONS = new QMissions("mi");
    public static final QMissionMembers MISSION_MEMBERS = new QMissionMembers("mm");
    public static final QShopItems SHOP_ITEMS = new QShopItems("si");
    public static final QShopOrders SHOP_ORDERS = new QShopOrders("so");
    public static final QAuditLogs AUDIT_LOGS = new QAuditLogs("al");
    public static final QMinecraftLinkCodes LINK_CODES = new QMinecraftLinkCodes("lc");
    public static final QWebLoginChallenges WEB_LOGIN_CHALLENGES = new QWebLoginChallenges("wlc");
    public static final QApplications APPLICATIONS = new QApplications("app");
    public static final QMarketplaceShops MARKETPLACE_SHOPS = new QMarketplaceShops("mps");
    public static final QMarketplaceItems MARKETPLACE_ITEMS = new QMarketplaceItems("mpi");
    public static final QMarketplaceCarts MARKETPLACE_CARTS = new QMarketplaceCarts("mpc");
    public static final QMarketplaceCartItems MARKETPLACE_CART_ITEMS = new QMarketplaceCartItems("mpci");
    public static final QMarketplaceOrders MARKETPLACE_ORDERS = new QMarketplaceOrders("mpo");
    public static final QMarketplaceOrderItems MARKETPLACE_ORDER_ITEMS = new QMarketplaceOrderItems("mpoi");

    private abstract static class Table extends RelationalPathBase<Object> {
        protected Table(String variable, String table) {
            super(Object.class, PathMetadataFactory.forVariable(variable), null, table);
        }
    }

    public static final class QMembers extends Table {
        public final StringPath id = createString("id");
        public final StringPath discordUserId = createString("discord_user_id");
        public final StringPath discordUsername = createString("discord_username");
        public final StringPath discordAvatarUrl = createString("discord_avatar_url");
        public final StringPath username = createString("username");
        public final StringPath reputation = createString("reputation");
        public final StringPath primaryRole = createString("primary_role");
        public final BooleanPath active = createBoolean("active");
        public final StringPath minecraftUuid = createString("minecraft_uuid");
        public final StringPath minecraftName = createString("minecraft_name");
        public final BooleanPath minecraftLoginProvisional = createBoolean("minecraft_login_provisional");
        public final NumberPath<Long> securityVersion = createNumber("security_version", Long.class);
        public final DateTimePath<LocalDateTime> createdAt = createDateTime("created_at", LocalDateTime.class);
        public final DateTimePath<LocalDateTime> updatedAt = createDateTime("updated_at", LocalDateTime.class);
        public QMembers(String variable) { super(variable, "members"); }
    }

    public static final class QXpTransactions extends Table {
        public final StringPath id = createString("id");
        public final StringPath memberId = createString("member_id");
        public final NumberPath<Long> amount = createNumber("amount", Long.class);
        public final StringPath category = createString("category");
        public final StringPath sourceType = createString("source_type");
        public final StringPath referenceId = createString("reference_id");
        public final StringPath reason = createString("reason");
        public final StringPath actorDiscordId = createString("actor_discord_id");
        public final DateTimePath<LocalDateTime> createdAt = createDateTime("created_at", LocalDateTime.class);
        public QXpTransactions(String variable) { super(variable, "xp_transactions"); }
    }

    public static final class QCreditTransactions extends Table {
        public final StringPath id = createString("id");
        public final StringPath memberId = createString("member_id");
        public final NumberPath<Long> amount = createNumber("amount", Long.class);
        public final StringPath sourceType = createString("source_type");
        public final StringPath referenceId = createString("reference_id");
        public final StringPath reason = createString("reason");
        public final StringPath actorDiscordId = createString("actor_discord_id");
        public final DateTimePath<LocalDateTime> createdAt = createDateTime("created_at", LocalDateTime.class);
        public QCreditTransactions(String variable) { super(variable, "credit_transactions"); }
    }

    public static final class QContributions extends Table {
        public final StringPath id = createString("id");
        public final StringPath memberId = createString("member_id");
        public final StringPath category = createString("category");
        public final StringPath description = createString("description");
        public final StringPath projectName = createString("project_name");
        public final StringPath evidenceUrl = createString("evidence_url");
        public final StringPath status = createString("status");
        public final NumberPath<Long> suggestedCxp = createNumber("suggested_cxp", Long.class);
        public final NumberPath<Long> suggestedCredits = createNumber("suggested_credits", Long.class);
        public final NumberPath<Long> awardedCxp = createNumber("awarded_cxp", Long.class);
        public final NumberPath<Long> awardedCredits = createNumber("awarded_credits", Long.class);
        public final StringPath reviewerDiscordId = createString("reviewer_discord_id");
        public final StringPath reviewReason = createString("review_reason");
        public final DateTimePath<LocalDateTime> reviewedAt = createDateTime("reviewed_at", LocalDateTime.class);
        public final DateTimePath<LocalDateTime> createdAt = createDateTime("created_at", LocalDateTime.class);
        public QContributions(String variable) { super(variable, "contributions"); }
    }

    public static final class QAchievements extends Table {
        public final StringPath code = createString("code");
        public final StringPath name = createString("name");
        public final StringPath description = createString("description");
        public final StringPath metric = createString("metric");
        public final StringPath category = createString("category");
        public final NumberPath<Long> threshold = createNumber("threshold", Long.class);
        public final NumberPath<Long> rewardCxp = createNumber("reward_cxp", Long.class);
        public final NumberPath<Long> rewardCredits = createNumber("reward_credits", Long.class);
        public final BooleanPath active = createBoolean("active");
        public QAchievements(String variable) { super(variable, "achievements"); }
    }

    public static final class QMemberAchievements extends Table {
        public final StringPath memberId = createString("member_id");
        public final StringPath achievementCode = createString("achievement_code");
        public final DateTimePath<LocalDateTime> unlockedAt = createDateTime("unlocked_at", LocalDateTime.class);
        public QMemberAchievements(String variable) { super(variable, "member_achievements"); }
    }

    public static final class QProjects extends Table {
        public final StringPath id = createString("id");
        public final StringPath name = createString("name");
        public final StringPath description = createString("description");
        public final StringPath status = createString("status");
        public final StringPath leadDiscordId = createString("lead_discord_id");
        public final StringPath createdByDiscordId = createString("created_by_discord_id");
        public final DateTimePath<LocalDateTime> createdAt = createDateTime("created_at", LocalDateTime.class);
        public final DateTimePath<LocalDateTime> completedAt = createDateTime("completed_at", LocalDateTime.class);
        public QProjects(String variable) { super(variable, "projects"); }
    }

    public static final class QProjectMembers extends Table {
        public final StringPath projectId = createString("project_id");
        public final StringPath memberId = createString("member_id");
        public final DateTimePath<LocalDateTime> joinedAt = createDateTime("joined_at", LocalDateTime.class);
        public QProjectMembers(String variable) { super(variable, "project_members"); }
    }

    public static final class QProjectTasks extends Table {
        public final StringPath id = createString("id");
        public final StringPath projectId = createString("project_id");
        public final StringPath title = createString("title");
        public final StringPath status = createString("status");
        public final StringPath assignedMemberId = createString("assigned_member_id");
        public final NumberPath<Long> rewardCxp = createNumber("reward_cxp", Long.class);
        public final NumberPath<Long> rewardCredits = createNumber("reward_credits", Long.class);
        public final StringPath completedByMemberId = createString("completed_by_member_id");
        public final DateTimePath<LocalDateTime> createdAt = createDateTime("created_at", LocalDateTime.class);
        public final DateTimePath<LocalDateTime> completedAt = createDateTime("completed_at", LocalDateTime.class);
        public QProjectTasks(String variable) { super(variable, "project_tasks"); }
    }

    public static final class QMissions extends Table {
        public final StringPath id = createString("id");
        public final StringPath name = createString("name");
        public final StringPath description = createString("description");
        public final StringPath status = createString("status");
        public final NumberPath<Long> rewardCxp = createNumber("reward_cxp", Long.class);
        public final NumberPath<Long> rewardCredits = createNumber("reward_credits", Long.class);
        public final NumberPath<Integer> maxSlots = createNumber("max_slots", Integer.class);
        public final DateTimePath<LocalDateTime> deadline = createDateTime("deadline", LocalDateTime.class);
        public final StringPath createdByDiscordId = createString("created_by_discord_id");
        public final DateTimePath<LocalDateTime> createdAt = createDateTime("created_at", LocalDateTime.class);
        public final DateTimePath<LocalDateTime> completedAt = createDateTime("completed_at", LocalDateTime.class);
        public QMissions(String variable) { super(variable, "missions"); }
    }

    public static final class QMissionMembers extends Table {
        public final StringPath missionId = createString("mission_id");
        public final StringPath memberId = createString("member_id");
        public final DateTimePath<LocalDateTime> joinedAt = createDateTime("joined_at", LocalDateTime.class);
        public QMissionMembers(String variable) { super(variable, "mission_members"); }
    }

    public static final class QShopItems extends Table {
        public final StringPath code = createString("code");
        public final StringPath name = createString("name");
        public final StringPath description = createString("description");
        public final NumberPath<Long> price = createNumber("price", Long.class);
        public final NumberPath<Integer> stock = createNumber("stock", Integer.class);
        public final BooleanPath active = createBoolean("active");
        public QShopItems(String variable) { super(variable, "shop_items"); }
    }

    public static final class QShopOrders extends Table {
        public final StringPath id = createString("id");
        public final StringPath memberId = createString("member_id");
        public final StringPath itemCode = createString("item_code");
        public final NumberPath<Long> price = createNumber("price", Long.class);
        public final StringPath status = createString("status");
        public final StringPath fulfillmentNote = createString("fulfillment_note");
        public final StringPath completedByDiscordId = createString("completed_by_discord_id");
        public final DateTimePath<LocalDateTime> createdAt = createDateTime("created_at", LocalDateTime.class);
        public final DateTimePath<LocalDateTime> completedAt = createDateTime("completed_at", LocalDateTime.class);
        public QShopOrders(String variable) { super(variable, "shop_orders"); }
    }

    public static final class QAuditLogs extends Table {
        public final StringPath id = createString("id");
        public final StringPath actorDiscordId = createString("actor_discord_id");
        public final StringPath action = createString("action");
        public final StringPath targetDiscordId = createString("target_discord_id");
        public final StringPath entityType = createString("entity_type");
        public final StringPath entityId = createString("entity_id");
        public final StringPath details = createString("details");
        public final DateTimePath<LocalDateTime> createdAt = createDateTime("created_at", LocalDateTime.class);
        public QAuditLogs(String variable) { super(variable, "audit_logs"); }
    }

    public static final class QMinecraftLinkCodes extends Table {
        public final StringPath code = createString("code");
        public final StringPath discordUserId = createString("discord_user_id");
        public final DateTimePath<LocalDateTime> expiresAt = createDateTime("expires_at", LocalDateTime.class);
        public final DateTimePath<LocalDateTime> createdAt = createDateTime("created_at", LocalDateTime.class);
        public QMinecraftLinkCodes(String variable) { super(variable, "minecraft_link_codes"); }
    }
    public static final class QWebLoginChallenges extends Table {
        public final StringPath id = createString("id");
        public final StringPath browserTokenHash = createString("browser_token_hash");
        public final StringPath verificationCodeHash = createString("verification_code_hash");
        public final StringPath memberId = createString("member_id");
        public final StringPath minecraftUuid = createString("minecraft_uuid");
        public final StringPath minecraftName = createString("minecraft_name");
        public final DateTimePath<LocalDateTime> expiresAt = createDateTime("expires_at", LocalDateTime.class);
        public final DateTimePath<LocalDateTime> verifiedAt = createDateTime("verified_at", LocalDateTime.class);
        public final DateTimePath<LocalDateTime> consumedAt = createDateTime("consumed_at", LocalDateTime.class);
        public final DateTimePath<LocalDateTime> createdAt = createDateTime("created_at", LocalDateTime.class);
        public QWebLoginChallenges(String variable) { super(variable, "web_login_challenges"); }
    }
    public static final class QApplications extends Table {
        public final StringPath id = createString("id");
        public final StringPath discordUserId = createString("discord_user_id");
        public final StringPath username = createString("username");
        public final StringPath status = createString("status");
        public final StringPath pendingGuard = createString("pending_guard");
        public final StringPath answersJson = createString("answers_json");
        public final StringPath pendingChannelId = createString("pending_channel_id");
        public final StringPath pendingMessageId = createString("pending_message_id");
        public final StringPath ticketChannelId = createString("ticket_channel_id");
        public final StringPath reviewerDiscordId = createString("reviewer_discord_id");
        public final StringPath reviewReason = createString("review_reason");
        public final DateTimePath<LocalDateTime> createdAt = createDateTime("created_at", LocalDateTime.class);
        public final DateTimePath<LocalDateTime> reviewedAt = createDateTime("reviewed_at", LocalDateTime.class);
        public QApplications(String variable) { super(variable, "applications"); }
    }


    public static final class QMarketplaceShops extends Table {
        public final StringPath id = createString("id");
        public final StringPath ownerMemberId = createString("owner_member_id");
        public final StringPath name = createString("name");
        public final StringPath description = createString("description");
        public final BooleanPath active = createBoolean("active");
        public final DateTimePath<LocalDateTime> createdAt = createDateTime("created_at", LocalDateTime.class);
        public final DateTimePath<LocalDateTime> updatedAt = createDateTime("updated_at", LocalDateTime.class);
        public QMarketplaceShops(String variable) { super(variable, "marketplace_shops"); }
    }

    public static final class QMarketplaceItems extends Table {
        public final StringPath id = createString("id");
        public final StringPath shopId = createString("shop_id");
        public final StringPath name = createString("name");
        public final StringPath description = createString("description");
        public final StringPath imageUrl = createString("image_url");
        public final NumberPath<Integer> stock = createNumber("stock", Integer.class);
        public final NumberPath<Long> price = createNumber("price", Long.class);
        public final StringPath category = createString("category");
        public final BooleanPath active = createBoolean("active");
        public final NumberPath<Long> version = createNumber("version", Long.class);
        public final DateTimePath<LocalDateTime> createdAt = createDateTime("created_at", LocalDateTime.class);
        public final DateTimePath<LocalDateTime> updatedAt = createDateTime("updated_at", LocalDateTime.class);
        public QMarketplaceItems(String variable) { super(variable, "marketplace_items"); }
    }

    public static final class QMarketplaceCarts extends Table {
        public final StringPath id = createString("id");
        public final StringPath memberId = createString("member_id");
        public final DateTimePath<LocalDateTime> createdAt = createDateTime("created_at", LocalDateTime.class);
        public final DateTimePath<LocalDateTime> updatedAt = createDateTime("updated_at", LocalDateTime.class);
        public QMarketplaceCarts(String variable) { super(variable, "marketplace_carts"); }
    }

    public static final class QMarketplaceCartItems extends Table {
        public final StringPath cartId = createString("cart_id");
        public final StringPath itemId = createString("item_id");
        public final NumberPath<Integer> quantity = createNumber("quantity", Integer.class);
        public final DateTimePath<LocalDateTime> createdAt = createDateTime("created_at", LocalDateTime.class);
        public final DateTimePath<LocalDateTime> updatedAt = createDateTime("updated_at", LocalDateTime.class);
        public QMarketplaceCartItems(String variable) { super(variable, "marketplace_cart_items"); }
    }

    public static final class QMarketplaceOrders extends Table {
        public final StringPath id = createString("id");
        public final StringPath buyerMemberId = createString("buyer_member_id");
        public final NumberPath<Long> totalPrice = createNumber("total_price", Long.class);
        public final StringPath status = createString("status");
        public final DateTimePath<LocalDateTime> createdAt = createDateTime("created_at", LocalDateTime.class);
        public final DateTimePath<LocalDateTime> completedAt = createDateTime("completed_at", LocalDateTime.class);
        public QMarketplaceOrders(String variable) { super(variable, "marketplace_orders"); }
    }

    public static final class QMarketplaceOrderItems extends Table {
        public final StringPath id = createString("id");
        public final StringPath orderId = createString("order_id");
        public final StringPath itemId = createString("item_id");
        public final StringPath shopId = createString("shop_id");
        public final StringPath sellerMemberId = createString("seller_member_id");
        public final StringPath shopName = createString("shop_name");
        public final StringPath itemName = createString("item_name");
        public final StringPath imageUrl = createString("image_url");
        public final StringPath category = createString("category");
        public final NumberPath<Integer> quantity = createNumber("quantity", Integer.class);
        public final NumberPath<Long> unitPrice = createNumber("unit_price", Long.class);
        public final NumberPath<Long> lineTotal = createNumber("line_total", Long.class);
        public final StringPath status = createString("status");
        public final BooleanPath fundsReleased = createBoolean("funds_released");
        public final DateTimePath<LocalDateTime> createdAt = createDateTime("created_at", LocalDateTime.class);
        public final DateTimePath<LocalDateTime> deliveredAt = createDateTime("delivered_at", LocalDateTime.class);
        public final DateTimePath<LocalDateTime> buyerConfirmedAt = createDateTime("buyer_confirmed_at", LocalDateTime.class);
        public final DateTimePath<LocalDateTime> disputedAt = createDateTime("disputed_at", LocalDateTime.class);
        public final StringPath disputeReason = createString("dispute_reason");
        public final DateTimePath<LocalDateTime> resolvedAt = createDateTime("resolved_at", LocalDateTime.class);
        public final StringPath resolution = createString("resolution");
        public final StringPath resolutionNote = createString("resolution_note");
        public QMarketplaceOrderItems(String variable) { super(variable, "marketplace_order_items"); }
    }

}

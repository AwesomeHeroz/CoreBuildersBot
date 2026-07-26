package com.corebuilders.bot.discord;

import com.corebuilders.bot.model.Models.Member;
import com.corebuilders.bot.model.RankCatalog;
import com.corebuilders.bot.model.RankDefinition;
import com.corebuilders.bot.service.LedgerService;
import com.corebuilders.bot.service.MemberService;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class RankRoleService {
    private static final Logger log = LoggerFactory.getLogger(RankRoleService.class);

    private final MemberService memberService;
    private final LedgerService ledger;
    private final RankCatalog ranks;

    public RankRoleService(MemberService memberService, LedgerService ledger) {
        this(memberService, ledger, RankCatalog.defaults());
    }

    public RankRoleService(MemberService memberService, LedgerService ledger, RankCatalog ranks) {
        this.memberService = Objects.requireNonNull(memberService, "memberService");
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.ranks = Objects.requireNonNull(ranks, "ranks");
    }

    public List<String> createMissingRoles(Guild guild) {
        Objects.requireNonNull(guild, "guild");
        List<String> missingRoleIds = ranks.ranks().stream()
                .filter(RankDefinition::hasDiscordRoleId)
                .filter(rank -> guild.getRoleById(rank.discordRoleId()) == null)
                .map(rank -> rank.display() + " (" + rank.discordRoleId() + ")")
                .toList();
        if (!missingRoleIds.isEmpty()) {
            throw new IllegalStateException("Configured progression role IDs do not exist: " + String.join(", ", missingRoleIds));
        }
        List<String> created = new ArrayList<>();
        for (RankDefinition rank : ranks.ranks()) {
            if (!rank.hasDiscordRoleId() && guild.getRolesByName(rank.display(), true).isEmpty()) {
                guild.createRole().setName(rank.display()).complete();
                created.add(rank.display());
            }
        }
        return created;
    }

    public void sync(Guild guild, String discordUserId) {
        Member profile;
        try {
            profile = memberService.requireByDiscordId(discordUserId);
        } catch (RuntimeException ex) {
            return;
        }

        RankDefinition rank = ranks.rankForXp(ledger.totalXp(profile.id()));
        guild.retrieveMemberById(discordUserId).queue(discordMember -> {
            List<Role> configuredRoles = ranks.ranks().stream()
                    .map(configured -> resolveRole(guild, configured))
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            Role target = resolveRole(guild, rank);
            if (target == null) {
                log.debug(
                        "Discord role for rank '{}' is unavailable; check progression.ranks or run /setup roles.",
                        rank.display()
                );
                return;
            }
            for (Role role : configuredRoles) {
                if (!role.equals(target) && discordMember.getRoles().contains(role)) {
                    guild.removeRoleFromMember(discordMember, role).queue(
                            ignored -> {},
                            error -> log.warn("Could not remove rank role {} from {}: {}",
                                    role.getName(), discordUserId, error.getMessage())
                    );
                }
            }
            if (!discordMember.getRoles().contains(target)) {
                guild.addRoleToMember(discordMember, target).queue(
                        ignored -> {},
                        error -> log.warn("Could not add rank role {} to {}: {}",
                                target.getName(), discordUserId, error.getMessage())
                );
            }
        }, error -> log.debug("Unable to retrieve Discord member {}: {}", discordUserId, error.getMessage()));
    }

    private static Role resolveRole(Guild guild, RankDefinition rank) {
        if (rank.hasDiscordRoleId()) {
            return guild.getRoleById(rank.discordRoleId());
        }
        return guild.getRolesByName(rank.display(), true).stream().findFirst().orElse(null);
    }
}

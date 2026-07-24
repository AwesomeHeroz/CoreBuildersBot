package com.corebuilders.bot.discord;

import com.corebuilders.bot.model.Domain.RankTier;
import com.corebuilders.bot.model.Models.Member;
import com.corebuilders.bot.service.LedgerService;
import com.corebuilders.bot.service.MemberService;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

public final class RankRoleService {
    private static final Logger log = LoggerFactory.getLogger(RankRoleService.class);

    private final MemberService memberService;
    private final LedgerService ledger;

    public RankRoleService(MemberService memberService, LedgerService ledger) {
        this.memberService = memberService;
        this.ledger = ledger;
    }

    public void sync(Guild guild, String discordUserId) {
        Member profile;
        try {
            profile = memberService.requireByDiscordId(discordUserId);
        } catch (RuntimeException ex) {
            return;
        }
        RankTier rank = RankTier.fromXp(ledger.totalXp(profile.id()));
        guild.retrieveMemberById(discordUserId).queue(discordMember -> {
            List<Role> rankRoles = Arrays.stream(RankTier.values())
                    .flatMap(tier -> guild.getRolesByName(tier.display(), true).stream())
                    .distinct()
                    .toList();
            Role target = guild.getRolesByName(rank.display(), true).stream().findFirst().orElse(null);
            if (target == null) {
                log.debug("Rank role '{}' does not exist; run /setup roles.", rank.display());
                return;
            }
            for (Role role : rankRoles) {
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
}

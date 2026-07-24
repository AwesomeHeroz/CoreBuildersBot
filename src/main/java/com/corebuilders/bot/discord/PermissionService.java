package com.corebuilders.bot.discord;

import com.corebuilders.bot.config.BotProperties;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;

import java.util.Set;

/** Authorizes privileged actions using immutable Discord role IDs. */
public final class PermissionService {
    private final BotProperties properties;

    public PermissionService(BotProperties properties) {
        this.properties = properties;
    }

    public boolean isTrustedStaff(Member member) {
        return hasAnyRoleId(member, properties.trustedStaffRoleIds()) || isAdmin(member);
    }

    public boolean isAdmin(Member member) {
        return hasAnyRoleId(member, properties.adminRoleIds()) || isLeadership(member);
    }

    public boolean isLeadership(Member member) {
        return member != null && (
                member.hasPermission(Permission.ADMINISTRATOR)
                        || hasAnyRoleId(member, properties.leadershipRoleIds())
        );
    }

    public void requireTrustedStaff(Member member) {
        if (!isTrustedStaff(member)) throw new SecurityException("This command requires a configured Trusted Staff role.");
    }

    public void requireAdmin(Member member) {
        if (!isAdmin(member)) throw new SecurityException("This command requires a configured Core Admin role.");
    }

    public void requireLeadership(Member member) {
        if (!isLeadership(member)) throw new SecurityException("This command requires a configured Leadership role.");
    }

    private static boolean hasAnyRoleId(Member member, Set<String> roleIds) {
        if (member == null || roleIds.isEmpty()) return false;
        return member.getRoles().stream().anyMatch(role -> roleIds.contains(role.getId()));
    }
}

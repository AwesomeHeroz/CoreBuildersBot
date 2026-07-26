package com.corebuilders.bot.web.auth;

import com.corebuilders.bot.model.Models.Member;
import com.corebuilders.bot.service.LedgerService;
import com.corebuilders.bot.service.MemberService;

import java.util.Objects;
import java.util.UUID;

public final class CoreWebsiteIdentity implements WebsiteIdentity {
    private final MemberService members;
    private final LedgerService ledger;

    public CoreWebsiteIdentity(MemberService members, LedgerService ledger) {
        this.members = Objects.requireNonNull(members, "members");
        this.ledger = Objects.requireNonNull(ledger, "ledger");
    }

    @Override
    public SessionPrincipal ensureProfile(DiscordIdentity identity) {
        Objects.requireNonNull(identity, "identity");
        Member member = members.ensureMember(identity.id(), identity.displayName());
        if (!member.active()) throw new IllegalStateException("Your Core Builders profile is inactive.");
        return new SessionPrincipal(member.id(), member.discordUserId(), member.username(), identity.avatarUrl());
    }

    @Override
    public long contributionPointBalance(UUID memberId) {
        return ledger.creditBalance(memberId);
    }
}

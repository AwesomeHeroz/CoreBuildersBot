package com.corebuilders.bot.web.auth;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class WebSessionStorePrincipalTest {
    @Test
    void discordLinkRefreshesPrincipalWithoutChangingCsrfOrSessionId() {
        WebSessionStore store = new WebSessionStore(Duration.ofHours(1));
        UUID memberId = UUID.randomUUID();
        WebSessionStore.Session original = store.create(
                new SessionPrincipal(memberId, null, "Steve", null)
        );

        SessionPrincipal linked = new SessionPrincipal(memberId, "123456789012345678", "Steve", "https://cdn.example/avatar.png");
        WebSessionStore.Session updated = store.replacePrincipal(original.id(), linked).orElseThrow();

        assertEquals(original.id(), updated.id());
        assertEquals(original.csrfToken(), updated.csrfToken());
        assertEquals(original.expiresAt(), updated.expiresAt());
        assertEquals(linked, updated.principal());
    }
}

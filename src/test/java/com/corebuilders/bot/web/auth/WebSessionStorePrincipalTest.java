package com.corebuilders.bot.web.auth;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class WebSessionStorePrincipalTest {
    @Test
    void discordLinkRotatesSessionAndCsrfTokens() {
        WebSessionStore store = new WebSessionStore(Duration.ofHours(1), Duration.ofMinutes(30));
        UUID memberId = UUID.randomUUID();
        WebSessionStore.Session original = store.create(new SessionPrincipal(memberId, null, "Steve", null, 0L));
        SessionPrincipal linked = new SessionPrincipal(memberId, "123456789012345678", "Steve", null, 1L);

        WebSessionStore.Session rotated = store.rotate(original.id(), linked).orElseThrow();

        assertNotEquals(original.id(), rotated.id());
        assertNotEquals(original.csrfToken(), rotated.csrfToken());
        assertTrue(store.find(original.id()).isEmpty());
        assertEquals(linked, rotated.principal());
    }
}

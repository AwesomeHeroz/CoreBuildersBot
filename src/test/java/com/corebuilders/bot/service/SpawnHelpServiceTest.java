package com.corebuilders.bot.service;

import com.corebuilders.bot.model.Domain.SpawnHelpStatus;
import com.corebuilders.bot.model.Models.SpawnHelpTicket;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SpawnHelpServiceTest {
    @Test
    void createsOpenTicketAndPreventsSecondActiveTicket() {
        FakeRepository repository = new FakeRepository();
        SpawnHelpService service = new SpawnHelpService(repository, Map.of("2b2t", "2b2t"));

        SpawnHelpTicket first = service.create("123456789012345", "Player", "2b2t", "Steve_123");
        assertEquals(SpawnHelpStatus.OPEN, first.status());
        assertEquals("Steve_123", first.inGameName());

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.create("123456789012345", "Player", "2b2t", "Alex_123"));
        assertTrue(error.getMessage().contains("active"));
    }

    @Test
    void rejectsUnknownServerAndInvalidMinecraftName() {
        SpawnHelpService service = new SpawnHelpService(new FakeRepository(), Map.of("2b2t", "2b2t"));

        assertThrows(IllegalArgumentException.class,
                () -> service.create("123456789012345", "Player", "unknown", "Steve"));
        assertThrows(IllegalArgumentException.class,
                () -> service.create("123456789012345", "Player", "2b2t", "bad name!"));
    }

    @Test
    void pickupAssignsHelperAndOnlyAssignedHelperCanComplete() {
        FakeRepository repository = new FakeRepository();
        SpawnHelpService service = new SpawnHelpService(repository, Map.of("2b2t", "2b2t"));
        SpawnHelpTicket ticket = service.create("123456789012345", "Player", "2b2t", "Steve");

        SpawnHelpTicket picked = service.claim(ticket.id(), "222222222222222", "HelperOne");
        assertEquals(SpawnHelpStatus.ON_HELP, picked.status());
        assertEquals("222222222222222", picked.helperDiscordId());

        assertThrows(SecurityException.class,
                () -> service.complete(ticket.id(), "333333333333333"));

        SpawnHelpTicket completed = service.complete(ticket.id(), "222222222222222");
        assertEquals(SpawnHelpStatus.COMPLETED, completed.status());
        assertTrue(service.activeForUser("123456789012345").isEmpty());
    }

    @Test
    void assignedHelperCanDenyOnHelpTicketAndReasonIsStored() {
        FakeRepository repository = new FakeRepository();
        SpawnHelpService service = new SpawnHelpService(repository, Map.of("6b6t", "6b6t"));
        SpawnHelpTicket ticket = service.create("123456789012345", "Player", "6b6t", "Steve");
        service.claim(ticket.id(), "222222222222222", "HelperOne");

        assertThrows(SecurityException.class,
                () -> service.deny(ticket.id(), "333333333333333", "HelperTwo", "Cannot assist"));

        SpawnHelpTicket denied = service.deny(ticket.id(), "222222222222222", "HelperOne", "Player is not at spawn");
        assertEquals(SpawnHelpStatus.DENIED, denied.status());
        assertEquals("Player is not at spawn", denied.denialReason());
        assertTrue(service.activeForUser("123456789012345").isEmpty());
    }

    private static final class FakeRepository implements SpawnHelpTicketRepository {
        private final Map<UUID, SpawnHelpTicket> tickets = new HashMap<>();
        private final Map<String, UUID> active = new HashMap<>();

        @Override
        public SpawnHelpTicket createOpen(SpawnHelpTicket ticket) {
            if (active.containsKey(ticket.discordUserId())) {
                throw new IllegalStateException("You already have an active spawn-help ticket.");
            }
            tickets.put(ticket.id(), ticket);
            active.put(ticket.discordUserId(), ticket.id());
            return ticket;
        }

        @Override public Optional<SpawnHelpTicket> find(UUID id) { return Optional.ofNullable(tickets.get(id)); }
        @Override public Optional<SpawnHelpTicket> activeForUser(String discordUserId) {
            return Optional.ofNullable(active.get(discordUserId)).map(tickets::get);
        }

        @Override
        public SpawnHelpTicket setChannel(UUID id, String channelId, String controlMessageId) {
            SpawnHelpTicket t = require(id);
            return save(copy(t, t.status(), t.helperDiscordId(), t.helperUsername(), t.denialReason(), channelId, controlMessageId, t.closedAt()));
        }

        @Override
        public SpawnHelpTicket claim(UUID id, String helperDiscordId, String helperUsername) {
            SpawnHelpTicket t = require(id);
            if (t.status() != SpawnHelpStatus.OPEN) throw new IllegalStateException("already picked up");
            return save(copy(t, SpawnHelpStatus.ON_HELP, helperDiscordId, helperUsername, null, t.channelId(), t.controlMessageId(), null));
        }

        @Override
        public SpawnHelpTicket complete(UUID id, String helperDiscordId) {
            SpawnHelpTicket t = require(id);
            if (!helperDiscordId.equals(t.helperDiscordId())) throw new SecurityException("wrong helper");
            active.remove(t.discordUserId());
            return save(copy(t, SpawnHelpStatus.COMPLETED, t.helperDiscordId(), t.helperUsername(), null,
                    t.channelId(), t.controlMessageId(), Instant.now()));
        }

        @Override
        public SpawnHelpTicket deny(UUID id, String helperDiscordId, String helperUsername, String reason) {
            SpawnHelpTicket t = require(id);
            if (t.status() == SpawnHelpStatus.ON_HELP && !helperDiscordId.equals(t.helperDiscordId())) {
                throw new SecurityException("wrong helper");
            }
            active.remove(t.discordUserId());
            return save(copy(t, SpawnHelpStatus.DENIED, helperDiscordId, helperUsername, reason,
                    t.channelId(), t.controlMessageId(), Instant.now()));
        }

        @Override
        public void abortUninitialized(UUID id, String discordUserId) {
            SpawnHelpTicket t = tickets.get(id);
            if (t != null && t.discordUserId().equals(discordUserId) && t.channelId() == null) {
                tickets.remove(id);
                active.remove(discordUserId);
            }
        }

        private SpawnHelpTicket require(UUID id) {
            SpawnHelpTicket t = tickets.get(id);
            if (t == null) throw new IllegalArgumentException("missing");
            return t;
        }

        private SpawnHelpTicket save(SpawnHelpTicket ticket) {
            tickets.put(ticket.id(), ticket);
            return ticket;
        }

        private static SpawnHelpTicket copy(
                SpawnHelpTicket t,
                SpawnHelpStatus status,
                String helperId,
                String helperName,
                String denialReason,
                String channelId,
                String messageId,
                Instant closedAt
        ) {
            return new SpawnHelpTicket(
                    t.id(), t.discordUserId(), t.discordUsername(), t.serverId(), t.serverName(), t.inGameName(),
                    status, helperId, helperName, denialReason, channelId, messageId,
                    t.createdAt(), Instant.now(), closedAt
            );
        }
    }
}

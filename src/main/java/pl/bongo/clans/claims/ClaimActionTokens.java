package pl.bongo.clans.claims;

import java.util.*;
import java.util.function.LongSupplier;

/** Single-use capabilities: a client never supplies a claim, recipient or permission level. */
final class ClaimActionTokens<T> {
    static final long TTL = 90_000_000_000L;
    private record Entry<T>(UUID player, long created, T value) {}
    private final Map<String, Entry<T>> entries = new HashMap<>();
    private final LongSupplier clock;
    ClaimActionTokens() { this(System::nanoTime); }
    ClaimActionTokens(LongSupplier clock) { this.clock = clock; }
    String issue(UUID player, T value) {
        String token = UUID.randomUUID().toString();
        entries.put(token, new Entry<>(player, clock.getAsLong(), value));
        return token;
    }
    T take(UUID player, String token) {
        Entry<T> entry = entries.get(token);
        if (entry == null || !entry.player.equals(player)) return null;
        entries.remove(token);
        return clock.getAsLong() - entry.created < TTL ? entry.value : null;
    }
    void forget(UUID player) { entries.values().removeIf(e -> e.player.equals(player)); }
    void expire() { long now = clock.getAsLong(); entries.values().removeIf(e -> now - e.created >= TTL); }
}

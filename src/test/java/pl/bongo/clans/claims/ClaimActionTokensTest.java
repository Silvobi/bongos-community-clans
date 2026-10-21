package pl.bongo.clans.claims;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClaimActionTokensTest {
    @Test void anotherPlayerCannotUseOrConsumeCapability() {
        var tokens = new ClaimActionTokens<String>(); UUID owner = UUID.randomUUID();
        String token = tokens.issue(owner, "delete claim 3");
        assertNull(tokens.take(UUID.randomUUID(), token));
        assertEquals("delete claim 3", tokens.take(owner, token));
        assertNull(tokens.take(owner, token));
    }
    @Test void expiredTokensAndCancelledMenusCannotBeReplayed() {
        AtomicLong clock = new AtomicLong(); var tokens = new ClaimActionTokens<String>(clock::get);
        UUID owner = UUID.randomUUID(); String token = tokens.issue(owner, "grant");
        clock.set(ClaimActionTokens.TTL); assertNull(tokens.take(owner, token));
        token = tokens.issue(owner, "transfer"); tokens.forget(owner); assertNull(tokens.take(owner, token));
        String other = tokens.issue(owner, "delete"); clock.addAndGet(ClaimActionTokens.TTL); tokens.expire(); assertNull(tokens.take(owner, other));
    }
}

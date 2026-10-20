package pl.bongo.clans;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Regression for JK8cvtw: eight visible letters, 72 persisted characters with per-letter HEX colors. */
class ClanTagCompatibilityTest {
    private static final String GRADIENT="&#FF0000R&#BF0000O&#800000B&#400000O&#000000T&#550000A&#AA0000I&#FF0000M";
    @TempDir Path root;
    @Test void existingEscapedGradientLoadsWithoutRewritingOrLosingMembership() throws Exception {
        Path file=root.resolve("clans.json");var store=new ClanStore(file);var service=new ClanService(store);
        UUID owner=UUID.randomUUID(),mate=UUID.randomUUID();
        service.create(owner,"Owner","ROBOTAIM",GRADIENT);service.invite(owner,mate);service.accept(mate,"Mate");
        service.quickchat(mate);service.ignore(owner);
        UUID id=service.require(owner).id;
        // The existing production file uses escaped ampersands. JSON decoding must precede visible-length validation.
        Files.writeString(file,Files.readString(file).replace("&","\\u0026"));byte[] original=Files.readAllBytes(file);
        var loaded=new ClanStore(file);var clan=loaded.state().clan(owner);
        assertArrayEquals(original,Files.readAllBytes(file));assertEquals(id,clan.id);assertEquals(GRADIENT,clan.tag);
        assertEquals(72,GRADIENT.codePointCount(0,GRADIENT.length()));assertEquals("ROBOTAIM",ClanTag.plainText(clan.tag));
        assertEquals(2,clan.members.size());assertTrue(loaded.state().users.get(owner).ignoreInvites);assertTrue(loaded.state().users.get(mate).quickchat);
        new ClanService(loaded).quickchat(owner);
        assertEquals(GRADIENT,new ClanStore(file).state().clan(mate).tag);
        assertArrayEquals(original,Files.readAllBytes(file.resolveSibling("clans.json.bak")));
    }
    @Test void visibleLimitStillRejectsInvalidTagsAndPreservesTheirFile() throws Exception {
        for(String tag:List.of("&#FF0000"+"X".repeat(13),"&a"," ","line\nbreak","§cTest")) {
            var file=root.resolve(UUID.randomUUID()+".json");UUID id=UUID.randomUUID(),owner=UUID.randomUUID();
            var state=new ClanData.State();var clan=new ClanData.Clan();clan.id=id;clan.name="Test";clan.tag=tag;
            clan.members.put(owner,new ClanData.Member(owner,"Owner",ClanData.Role.FOUNDER));state.clans.put(id,clan);
            Files.writeString(file,new com.google.gson.Gson().toJson(state));byte[] before=Files.readAllBytes(file);
            assertThrows(java.io.IOException.class,()->new ClanStore(file),tag);assertArrayEquals(before,Files.readAllBytes(file));
        }
        assertEquals("Paws_Kingdom",ClanData.tag("Paws_Kingdom"));
        assertEquals("&#FFFFFF"+"X".repeat(12),ClanData.tag("&#FFFFFF"+"X".repeat(12)));
    }
}

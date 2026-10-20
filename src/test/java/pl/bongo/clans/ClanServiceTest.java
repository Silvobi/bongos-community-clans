package pl.bongo.clans;

import static org.junit.jupiter.api.Assertions.*;
import static pl.bongo.clans.ClanData.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

class ClanServiceTest {
    @TempDir Path dir;
    ClanStore store;
    ClanService service;
    UUID founder=UUID.randomUUID(), cofounder=UUID.randomUUID(), manager=UUID.randomUUID(), member=UUID.randomUUID(), outsider=UUID.randomUUID();
    AtomicLong now=new AtomicLong(1_000_000);
    @BeforeEach void setup() throws Exception {
        store=new ClanStore(dir.resolve("clans.json")); service=new ClanService(store,now::get);
        service.create(founder,"Founder","MasloTeamX","MasloTeamX");
        join(cofounder,"CoFounder"); join(manager,"Manager"); join(member,"Member");
        service.role(founder,cofounder,Role.COFOUNDER,true); service.role(cofounder,manager,Role.MANAGER,true);
    }
    void join(UUID id,String name) { service.invite(founder,id); service.accept(id,name); }
    void denied(String key,Runnable operation) { assertEquals(key,assertThrows(ClanException.class,operation::run).key); }
    @Test void permissionsAndHierarchyAreEnforcedBeyondCommandVisibility() {
        denied("no_permission",()->service.invite(member,outsider));
        denied("no_permission",()->service.change(manager,"tag","NEW"));
        denied("no_permission",()->service.role(manager,member,Role.MANAGER,true));
        denied("hierarchy",()->service.role(cofounder,member,Role.COFOUNDER,true));
        denied("hierarchy",()->service.kick(manager,cofounder));
        denied("hierarchy",()->service.kick(cofounder,founder));
        denied("hierarchy",()->service.kick(founder,founder));
        denied("hierarchy",()->service.role(founder,member,Role.FOUNDER,true));
        denied("no_permission",()->service.delete(cofounder,service.require(founder).id));
        service.kick(manager,member); assertNull(service.state().clan(member));
    }
    @Test void invitationExpiryRevocationAndIgnoreWork() {
        service.invite(manager,outsider); now.addAndGet(ClanService.INVITE_LIFETIME);
        denied("no_invite",()->service.accept(outsider,"Outsider"));
        service.invite(manager,outsider); service.role(cofounder,manager,Role.MEMBER,false);
        denied("no_invite",()->service.accept(outsider,"Outsider"));
        assertTrue(service.ignore(outsider)); denied("ignores_invites",()->service.invite(founder,outsider));
        assertFalse(service.ignore(outsider)); service.invite(founder,outsider);
        denied("invite_pending",()->service.invite(cofounder,outsider));
        service.decline(outsider); denied("no_invite",()->service.accept(outsider,"Outsider"));
    }
    @Test void transferAndLeavePreserveExactlyOneFounder() {
        denied("founder_leave",()->service.leave(founder));
        service.transfer(founder,member);
        assertEquals(Role.FOUNDER,service.require(member).members.get(member).role);
        assertEquals(Role.COFOUNDER,service.require(founder).members.get(founder).role);
        service.leave(founder); assertNull(service.state().clan(founder));
        ClanData.validate(service.state());
    }
    @Test void creationIsAtomicAndClansAndTagsAreUniqueIgnoringCase() throws Exception {
        denied("already_clan",()->service.create(founder,"Founder","Other","Other"));
        denied("name_taken",()->service.create(outsider,"Outsider","masloteamx","Other"));
        denied("tag_taken",()->service.create(outsider,"Outsider","Other","masloteamx"));
        assertEquals(1,service.state().clans.size());
        assertEquals(1,new ClanStore(dir.resolve("clans.json")).state().clans.size());
    }
    @Test void changesPreferencesAndMembershipSurviveRestart() throws Exception {
        service.change(cofounder,"name","Zażółć_123.-"); service.change(cofounder,"tag","&1★&#a1b2c3🧈&4★");
        service.quickchat(member); service.ignore(outsider);
        ClanStore reloaded=new ClanStore(dir.resolve("clans.json"));
        Clan c=reloaded.state().clan(member);
        assertEquals("&1★&#a1b2c3🧈&4★",c.tag); assertEquals(0xFFFFFF,c.color); assertEquals("Zażółć_123.-",c.name);
        assertEquals(Role.COFOUNDER,c.members.get(cofounder).role);
        assertTrue(reloaded.state().users.get(member).quickchat); assertTrue(reloaded.state().users.get(outsider).ignoreInvites);
        assertTrue(Files.exists(dir.resolve("clans.json.bak")));
    }
    @Test void leavingKickingAndDeletionResetQuickchatAndInvites() {
        service.quickchat(member); service.leave(member); assertFalse(service.state().users.get(member).quickchat);
        service.invite(manager,outsider); service.kick(founder,manager);
        denied("no_invite",()->service.accept(outsider,"Outsider"));
        service.invite(founder,outsider); service.quickchat(cofounder);
        service.delete(founder,service.require(founder).id);
        assertTrue(service.state().clans.isEmpty()); assertTrue(service.state().invitations.isEmpty());
        assertFalse(service.state().users.get(cofounder).quickchat);
    }
    @Test void failedSaveDoesNotChangeState() throws Exception {
        // Make the parent path a file, so persistence fails after validation.
        Path obstacle=dir.resolve("obstacle"); Files.writeString(obstacle,"blocked");
        ClanStore blocked=new ClanStore(obstacle.resolve("data.json"));
        assertThrows(java.io.UncheckedIOException.class,()->new ClanService(blocked).create(outsider,"Outsider","New","New"));
        assertTrue(blocked.state().clans.isEmpty());
    }
    @Test void corruptedDataIsPreservedAndNeverReplacedWithAnEmptyState() throws Exception {
        Path bad=dir.resolve("bad.json"); String original="{ definitely invalid"; Files.writeString(bad,original);
        assertThrows(java.io.IOException.class,()->new ClanStore(bad)); assertEquals(original,Files.readString(bad));
    }
    @Test void unicodeLimitsAndFormattingRestrictions() {
        assertArrayEquals(new String[]{"Zażółć","★🧈★"},ClanCommands.createArguments("Zażółć ★🧈★"));
        assertArrayEquals(new String[]{"MyClan","tag space"},ClanCommands.createArguments("MyClan \"tag space\""));
        assertEquals("#12ab34",ClanCommands.textArgument("#12ab34"));
        assertEquals("🧈".repeat(12),ClanData.tag("🧈".repeat(12)));
        denied("bad_tag",()->ClanData.tag("🧈".repeat(13)));
        denied("bad_tag",()->ClanData.tag("a\nb")); denied("bad_tag",()->ClanData.tag("§cTest"));
        denied("bad_tag",()->ClanData.tag("   ")); denied("bad_name",()->ClanData.name("a b"));
        denied("bad_name",()->ClanData.name("a".repeat(33)));
        assertEquals("ą",ClanData.name("a\u0328"));
    }
    @Test void inlineColorsDoNotCountTowardsUnicodeTagLimit() {
        String twelve = "&#123456🧈&A".repeat(12);
        assertEquals(twelve,ClanData.tag(twelve));
        denied("bad_tag",()->ClanData.tag(twelve+"&0🧈"));
        assertEquals("&1Super&4Clan",ClanData.tag("&1Super&4Clan"));
        assertEquals("&aą",ClanData.tag("&aa\u0328"));
        for (String tag : List.of("&a", "&#000000", "&a &4  ", "&a\nX", "&a§cX", "&a\uD800"))
            denied("bad_tag",()->ClanData.tag(tag));
        assertEquals("R&z&k&#12x",ClanTag.plainText(ClanData.tag("R&z&k&#12x")));
        denied("bad_tag",()->ClanData.tag("&k".repeat(7)));
    }
    @Test void namesRejectColorsOnCreationAndRenameWithoutChangingData() {
        for (String name : List.of("&aName", "&#000000Name", "§aName", "Na&4me")) {
            denied("bad_name",()->service.create(outsider,"Outsider",name,"Other"));
            denied("bad_name",()->service.change(founder,"name",name));
        }
        assertEquals("MasloTeamX",service.require(founder).name);
        assertNull(service.state().clan(outsider));
    }
    @Test void coloredTagsAreCreatedAndComparedByTheirPlainText() throws Exception {
        denied("tag_taken",()->service.create(outsider,"Outsider","Other","&#ff0000maslo&ATeamX"));
        service.create(outsider,"Outsider","Other","&1Super&4Clan");
        assertEquals("&1Super&4Clan",new ClanStore(dir.resolve("clans.json")).state().clan(outsider).tag);
        denied("tag_taken",()->service.change(founder,"tag","&#000000SUPERclan"));
        assertEquals("MasloTeamX",service.require(founder).tag);
        service.change(outsider,"tag","&aSuper&bClan");
        assertEquals("&aSuper&bClan",service.require(outsider).tag);
    }
    @Test void oldSavedColorsSurviveLoadingUntilTheTagIsEdited() throws Exception {
        store.update(s -> { s.clan(founder).color=0x123456; return null; });
        ClanService reloaded=new ClanService(new ClanStore(dir.resolve("clans.json")));
        assertEquals(0x123456,reloaded.require(founder).color);
        reloaded.change(founder,"tag","&aNewTag");
        assertEquals(0xFFFFFF,reloaded.require(founder).color);
        reloaded.change(founder,"tag","PlainTag");
        assertEquals(0xFFFFFF,reloaded.require(founder).color);
        assertThrows(IllegalArgumentException.class,()->reloaded.change(founder,"color","&a"));
    }
    @Test void polishAndEnglishHaveIdenticalMessageCoverage() {
        assertEquals(Lang.keys("en"),Lang.keys("pl"));
        assertEquals("pl",Lang.language("pl_pl")); assertEquals("en",Lang.language("de_de"));
        assertEquals("Founder",Lang.text("en_us","role_founder"));
        assertEquals("Założyciel",Lang.text("pl_pl","role_founder"));
        assertEquals("pl",Lang.language("PL_pl"));
        assertEquals("en",Lang.language(null));
        assertEquals("This command is available to players.",Lang.text((String)null,"player_only"));
        for (String key : Lang.keys("en")) {
            // Both catalogs use %s. Extra arguments also cover keys without placeholders.
            String[] args = java.util.Collections.nCopies(8,"VALUE").toArray(String[]::new);
            String pl = Lang.text("pl_pl",key,(Object[])args), en = Lang.text("en_us",key,(Object[])args);
            assertFalse(pl.isBlank(),key); assertFalse(en.isBlank(),key);
            assertEquals(pl.split("VALUE",-1).length,en.split("VALUE",-1).length,key);
        }
    }
}

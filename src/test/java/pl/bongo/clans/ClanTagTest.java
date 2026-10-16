package pl.bongo.clans;

import static org.junit.jupiter.api.Assertions.*;
import com.mojang.brigadier.CommandDispatcher;
import java.util.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.*;
import org.junit.jupiter.api.Test;

class ClanTagTest {
    record Run(String text, int color) {}
    private static List<Run> runs(Component component) {
        List<Run> result = new ArrayList<>();
        component.visit((Style style, String text) -> {
            if (!text.isEmpty()) result.add(new Run(text, style.getColor().getValue()));
            return Optional.empty();
        }, Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF)));
        return result;
    }
    @Test void legacyAndHexCodesCanBeMixedWithoutAppearingInText() {
        Component tag = ClanTag.component("Hi&1Super&#a1B2c3Clan&4!",0xFFFFFF);
        assertEquals("HiSuperClan!",tag.getString());
        assertEquals(List.of(new Run("Hi",0xFFFFFF),new Run("Super",0x0000AA),
                new Run("Clan",0xA1B2C3),new Run("!",0xAA0000)),runs(tag));
    }
    @Test void allSixteenLegacyCodesUseMinecraftPaletteIgnoringCase() {
        int[] expected = {0,0x0000AA,0x00AA00,0x00AAAA,0xAA0000,0xAA00AA,0xFFAA00,0xAAAAAA,
                0x555555,0x5555FF,0x55FF55,0x55FFFF,0xFF5555,0xFF55FF,0xFFFF55,0xFFFFFF};
        String codes="0123456789ABCDEF";
        for (int i=0;i<codes.length();i++)
            assertEquals(List.of(new Run("X",expected[i])),runs(ClanTag.component("&"+codes.charAt(i)+"X",0xFFFFFF)));
    }
    @Test void consecutiveTrailingAndBlackHexColorsWork() {
        assertEquals(List.of(new Run("X",0)),runs(ClanTag.component("&a&#000000X&4&#FFFFFF",0xFFFFFF)));
    }
    @Test void unsupportedCodesRemainLiteralAndHaveNoStyleEffects() {
        Component tag=ClanTag.component("&k&l&r&#123&z&",0x123456);
        assertEquals(List.of(new Run("&k&l&r&#123&z&",0x123456)),runs(tag));
        tag.visit((Style style, String text) -> {
            assertFalse(style.isObfuscated()); assertFalse(style.isBold());
            return Optional.empty();
        },Style.EMPTY);
    }
    @Test void tagColorsCannotLeakIntoBracketsOrPlayerName() {
        ClanData.Clan clan=new ClanData.Clan(); clan.tag="&1Super&4Clan";
        Component name=CommunityClans.displayName(clan,"Player");
        assertEquals("[SuperClan] Player",name.getString());
        assertEquals(List.of(new Run("[",0xFFFFFF),new Run("Super",0x0000AA),new Run("Clan",0xAA0000),
                new Run("] ",0xFFFFFF),new Run("Player",0xFFFFFF)),runs(name));
    }
    @Test void savedSingleColorPrefixesKeepTheirAppearance() {
        ClanData.Clan clan=new ClanData.Clan(); clan.tag="Old"; clan.color=0x123456;
        assertEquals(List.of(new Run("[",0x123456),new Run("Old",0x123456),new Run("] ",0x123456),
                new Run("Player",0xFFFFFF)),runs(CommunityClans.displayName(clan,"Player")));
    }
    @Test void translatedMessagesPreserveTagColorsAndResetAfterTheArgument() {
        ClanData.Clan clan=new ClanData.Clan(); clan.tag="&1Super&4Clan";
        Component tag=CommunityClans.tag(clan);
        assertEquals("Tag klanu został zmieniony na [SuperClan].",Lang.component("pl_pl","changed_tag",tag).getString());
        assertEquals(List.of(new Run("Clan tag changed to [",0xFFFFFF),new Run("Super",0x0000AA),
                new Run("Clan",0xAA0000),new Run("].",0xFFFFFF)),runs(Lang.component("en_us","changed_tag",tag)));
        assertEquals("Members: 4",Lang.component("en_us","info_members",4).getString());
        assertTrue(Lang.component("en_us","invitation","Player","MyClan",tag).getString().contains("MyClan [SuperClan]"));
    }
    @Test void separateColorCommandIsRemovedFromTheCommandTree() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        var dispatcher=new CommandDispatcher<CommandSourceStack>();
        ClanCommands.register(dispatcher);
        var change=dispatcher.getRoot().getChild("clan").getChild("change");
        assertNotNull(change.getChild("tag")); assertNotNull(change.getChild("name"));
        assertNull(change.getChild("color"));
    }
}

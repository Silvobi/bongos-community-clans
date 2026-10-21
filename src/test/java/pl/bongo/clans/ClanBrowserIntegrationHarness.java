package pl.bongo.clans;

import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.HashedStack;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.Items;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import static pl.bongo.clans.IntegrationHarness.*;

/** Disposable fixture: 28 clans, including Żółwie with 28 members, newest by creation time. */
public final class ClanBrowserIntegrationHarness {
    record Screen(ClientboundOpenScreenPacket open, ClientboundContainerSetContentPacket content) {}
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); port=Integer.parseInt(args[0]);
        net.minecraft.core.registries.BuiltInRegistries.ITEM.listElements().forEach(h -> {
            if (!h.areComponentsBound()) h.bindComponents(net.minecraft.core.component.DataComponentMap.EMPTY);
        });
        String stamp=Long.toString(System.nanoTime(),36);
        try (var viewer=new Bot("Browser"+stamp,"en_us")) {
            viewer.send(new ServerboundCommandSuggestionPacket(1,"/clan info Ż"));
            var suggestions=(ClientboundCommandSuggestionsPacket)viewer.await(p->p instanceof ClientboundCommandSuggestionsPacket,5000);
            check(suggestions.toSuggestions().getList().stream().anyMatch(s -> s.getText().equals("Żółwie")),"clan names autocomplete for outsiders");
            Screen list=open(viewer,"clans");
            check(list.open.getType()==MenuType.GENERIC_9x6,"clans directory uses large chest protocol");
            check(name(list,18).contains("Żółwie"),"newest clan is listed first");
            check(list.content.items().get(6).is(Items.STAINED_GLASS_PANE.black()),"area sorting is hidden without GP3D");
            final int listId=list.open.getContainerId();
            for(ContainerInput input:List.of(ContainerInput.QUICK_MOVE,ContainerInput.SWAP,ContainerInput.THROW,ContainerInput.CLONE,ContainerInput.QUICK_CRAFT,ContainerInput.PICKUP_ALL)) {
                click(viewer,list,18,input);
                var unchanged=(ClientboundContainerSetContentPacket)viewer.await(p->p instanceof ClientboundContainerSetContentPacket c && c.containerId()==listId,5000);
                check(unchanged.carriedItem().isEmpty() && unchanged.items().get(18).is(Items.NETHERITE_SWORD),"browser blocks extraction via "+input);
            }
            click(viewer,list,18,ContainerInput.PICKUP); Screen info=screen(viewer);
            check(info.open.getTitle().getString().contains("Żółwie"),"outsider can open other clan information");
            check(info.content.items().get(1).is(Items.NAME_TAG) && info.content.items().get(3).is(Items.CLOCK)
                    && info.content.items().get(5).is(Items.NETHERITE_SWORD),"metadata follows reference layout");
            check(info.content.items().get(18).is(Items.PLAYER_HEAD) && info.content.items().get(44).is(Items.PLAYER_HEAD),"27 member slots occupy rows 3–5");
            check(info.content.items().get(45).is(Items.ARROW) && info.content.items().get(49).is(Items.BARRIER)
                    && info.content.items().get(50).is(Items.PAPER),"back, close and next controls follow reference layout");
            click(viewer,info,50,ContainerInput.PICKUP); info=screen(viewer);
            check(info.content.items().get(18).is(Items.PLAYER_HEAD) && info.content.items().get(19).isEmpty(),"second member page contains only remaining member");
            click(viewer,info,45,ContainerInput.PICKUP); list=screen(viewer);
            click(viewer,list,1,ContainerInput.PICKUP); list=screen(viewer);
            check(name(list,18).contains("Fixture01"),"oldest sorting reverses creation order");
            click(viewer,list,50,ContainerInput.PICKUP); list=screen(viewer);
            check(name(list,18).contains("Żółwie"),"directory pagination reaches the last clan");
            click(viewer,list,18,ContainerInput.PICKUP); info=screen(viewer);
            click(viewer,info,45,ContainerInput.PICKUP); list=screen(viewer);
            check(name(list,18).contains("Żółwie") && list.content.items().get(48).is(Items.PAPER),"back preserves sort and directory page");
            viewer.send(new ServerboundContainerClosePacket(list.open.getContainerId()));
            info=open(viewer,"clan info Żółwie");
            check(info.content.items().get(45).is(Items.STAINED_GLASS_PANE.black()),"direct Unicode clan lookup has no directory back button");
            click(viewer,info,49,ContainerInput.PICKUP);
            viewer.command("clan info MissingClan"); viewer.message("No clan with that name");
            System.out.println("PASS: directory sort/pagination, outsiders, Unicode lookup, info layout, member pagination, return context, inventory protection.");
        }
    }
    private static String name(Screen screen,int slot) { return screen.content.items().get(slot).get(DataComponents.CUSTOM_NAME).getString(); }
    private static Screen open(Bot bot,String command) throws Exception { bot.command(command);return screen(bot); }
    private static Screen screen(Bot bot) throws Exception {
        var open=(ClientboundOpenScreenPacket)bot.await(p->p instanceof ClientboundOpenScreenPacket,5000);
        var content=(ClientboundContainerSetContentPacket)bot.await(p->p instanceof ClientboundContainerSetContentPacket c && c.containerId()==open.getContainerId(),5000);
        return new Screen(open,content);
    }
    private static void click(Bot bot,Screen screen,int slot,ContainerInput input) throws Exception {
        bot.send(new ServerboundContainerClickPacket(screen.open.getContainerId(),screen.content.stateId(),(short)slot,(byte)0,input,
                new Int2ObjectOpenHashMap<>(),HashedStack.EMPTY));
    }
}

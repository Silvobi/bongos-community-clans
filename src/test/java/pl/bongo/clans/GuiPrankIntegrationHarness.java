package pl.bongo.clans;

import static pl.bongo.clans.IntegrationHarness.check;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.component.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.HashedStack;
import net.minecraft.network.protocol.common.*;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.*;

/** Real survival container packets, including negative gestures and a full cooldown. */
public final class GuiPrankIntegrationHarness {
    record Screen(ClientboundOpenScreenPacket open,ClientboundContainerSetContentPacket items) {}
    static Screen open(IntegrationHarness.Bot bot) throws Exception {
        bot.command("clan info");
        var screen=(ClientboundOpenScreenPacket)bot.await(p->p instanceof ClientboundOpenScreenPacket,5000);
        var items=(ClientboundContainerSetContentPacket)bot.await(p->p instanceof ClientboundContainerSetContentPacket c && c.containerId()==screen.getContainerId(),5000);
        return new Screen(screen,items);
    }
    static void click(IntegrationHarness.Bot bot,Screen screen,int slot,ContainerInput input,int id) throws Exception {
        bot.send(new ServerboundContainerClickPacket(id,screen.items.stateId(),(short)slot,(byte)0,input,new Int2ObjectOpenHashMap<>(),HashedStack.EMPTY));
    }
    static void gesture(IntegrationHarness.Bot bot,Screen screen,int slot,ContainerInput input) throws Exception {
        click(bot,screen,slot,input,screen.open.getContainerId());
        bot.send(new ServerboundContainerClosePacket(screen.open.getContainerId()));
    }
    static void none(IntegrationHarness.Bot bot,String label) throws Exception {
        bot.drain(150);
        check(bot.events.stream().noneMatch(CatIntegrationHarness::catSpawn),label);
        check(bot.events.stream().noneMatch(p->p instanceof ClientboundSoundPacket s && s.getSound().value().location().getPath().equals("cat.clapping")),"no cat audio for: "+label);
        bot.events.clear();
    }
    static void responsiveWait(IntegrationHarness.Bot a,IntegrationHarness.Bot b,long millis) throws Exception {
        long deadline=System.currentTimeMillis()+millis;
        while(System.currentTimeMillis()<deadline) { a.drain(10); b.drain(5); }
    }
    static void inventoryFixture(String name) throws Exception {
        String suffix=Long.toString(System.currentTimeMillis(),36);
        try(var bot=new IntegrationHarness.Bot(name,"pl_pl")) {
            bot.command("clan create I"+suffix+" I"+suffix); bot.message("Utworzono klan");
            System.out.println("READY: give "+name+" minecraft:stone 8 in the isolated server console");
            long deadline=System.currentTimeMillis()+30000; Screen screen=null; int source=-1;
            while(System.currentTimeMillis()<deadline && source<0) {
                screen=open(bot);
                for(int i=54;i<90;i++) if(!screen.items.items().get(i).isEmpty()) { source=i; break; }
                if(source<0)bot.drain(2000);
            }
            check(source>=54,"fixture includes a real survival inventory item");
            ItemStack before=screen.items.items().get(source).copy();
            gesture(bot,screen,source,ContainerInput.QUICK_MOVE);
            bot.await(CatIntegrationHarness::catSpawn,5000);
            bot.await(p->p instanceof ClientboundSoundPacket s && s.getSound().value().location().getPath().equals("cat.clapping"),5000);
            check(true,"attempted insertion from the player's inventory also triggers cat and audio");
            Screen after=open(bot);
            check(ItemStack.matches(before,after.items.items().get(source)) && after.items.carriedItem().isEmpty(),"player keeps the real item and its count after the insertion gesture");
            bot.send(new ServerboundContainerClosePacket(after.open.getContainerId()));
        }
    }
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        IntegrationHarness.port=Integer.parseInt(args[0]);
        BuiltInRegistries.ITEM.listElements().forEach(h->{if(!h.areComponentsBound())h.bindComponents(DataComponentMap.EMPTY);});
        if(args.length>1 && args[1].equals("inventory")) { inventoryFixture(args[2]); return; }
        String suffix=Long.toString(System.currentTimeMillis(),36);
        try(var viewer=new IntegrationHarness.Bot("GP"+suffix,"pl_pl");var other=new IntegrationHarness.Bot("GO"+suffix,"en_us")) {
            viewer.command("clan create G"+suffix+" T"+suffix); viewer.message("Utworzono klan");
            Screen normal=open(viewer); gesture(viewer,normal,9,ContainerInput.PICKUP);
            none(viewer,"ordinary click and close does not trigger");
            Screen empty=open(viewer); check(empty.items.items().get(45).isEmpty(),"fixture slot is empty");
            gesture(viewer,empty,45,ContainerInput.QUICK_MOVE); none(viewer,"empty shift-click does not trigger");
            Screen slow=open(viewer); click(viewer,slow,9,ContainerInput.QUICK_MOVE,slow.open.getContainerId());
            responsiveWait(viewer,other,800); viewer.send(new ServerboundContainerClosePacket(slow.open.getContainerId()));
            none(viewer,"closing after 800 ms does not trigger");
            Screen interrupted=open(viewer);
            click(viewer,interrupted,9,ContainerInput.QUICK_MOVE,interrupted.open.getContainerId());
            click(viewer,interrupted,4,ContainerInput.PICKUP,interrupted.open.getContainerId());
            viewer.send(new ServerboundContainerClosePacket(interrupted.open.getContainerId()));
            none(viewer,"an intervening ordinary click cancels the gesture");
            Screen staleClick=open(viewer); click(viewer,staleClick,9,ContainerInput.QUICK_MOVE,staleClick.open.getContainerId()+1);
            viewer.send(new ServerboundContainerClosePacket(staleClick.open.getContainerId()));
            none(viewer,"click for another window does not trigger");
            Screen staleClose=open(viewer); click(viewer,staleClose,9,ContainerInput.QUICK_MOVE,staleClose.open.getContainerId());
            viewer.send(new ServerboundContainerClosePacket(staleClose.open.getContainerId()+1));
            none(viewer,"close for another window does not trigger");
            viewer.send(new ServerboundContainerClickPacket(0,0,(short)9,(byte)0,ContainerInput.QUICK_MOVE,new Int2ObjectOpenHashMap<>(),HashedStack.EMPTY));
            viewer.send(new ServerboundContainerClosePacket(0));
            none(viewer,"ordinary player inventory is ignored");

            Screen target=open(viewer);
            List<ItemStack> inventoryBefore=target.items.items().subList(54,90).stream().map(ItemStack::copy).toList();
            gesture(viewer,target,9,ContainerInput.QUICK_MOVE);
            var spawn=(ClientboundAddEntityPacket)viewer.await(CatIntegrationHarness::catSpawn,5000);
            long first=System.nanoTime();
            viewer.await(p->p instanceof ClientboundSoundPacket s && s.getSound().value().location().getPath().equals("cat.clapping"),5000);
            check(true,"shift-click on a head and immediate close triggers cat and audio");
            Screen verify=open(viewer);
            check(!verify.items.items().get(9).isEmpty(),"clan head remains owned by the GUI");
            var after=verify.items.items().subList(54,90);
            boolean same=true; for(int i=0;i<after.size();i++) same &= ItemStack.matches(inventoryBefore.get(i),after.get(i));
            check(same && verify.items.carriedItem().isEmpty(),"gesture and cat preserve authoritative survival inventory");
            gesture(viewer,verify,9,ContainerInput.QUICK_MOVE);
            viewer.drain(150);
            check(viewer.events.stream().filter(CatIntegrationHarness::catSpawn).findAny().isEmpty(),"repeated gesture cannot interrupt or stack an active cat");
            viewer.await(p->p instanceof ClientboundRemoveEntitiesPacket r && r.entityIds().contains(spawn.getId()),8000);
            viewer.events.clear();
            Screen cooling=open(viewer); gesture(viewer,cooling,9,ContainerInput.QUICK_MOVE);
            none(viewer,"cooldown suppresses another cat even after the first animation has ended");
            other.drain(100);
            check(other.events.stream().noneMatch(CatIntegrationHarness::catSpawn),"observer receives no automatic cat");
            check(other.events.stream().noneMatch(p->p instanceof ClientboundSoundPacket s && s.getSound().value().location().getPath().equals("cat.clapping")),"observer receives no automatic audio");
            viewer.command("clan cat"); viewer.await(CatIntegrationHarness::catSpawn,5000);
            check(true,"manual preview remains usable during automatic cooldown");
            long remaining=30_250-(System.nanoTime()-first)/1_000_000;
            if(remaining>0)responsiveWait(viewer,other,remaining);
            viewer.events.clear();
            Screen expired=open(viewer); gesture(viewer,expired,9,ContainerInput.QUICK_MOVE);
            viewer.await(CatIntegrationHarness::catSpawn,5000);
            check(true,"another gesture works after the 30-second cooldown expires");
        }
        try(var noPack=new IntegrationHarness.Bot("GN"+suffix,"en_us",(bot,pack)->bot.send(new ServerboundResourcePackPacket(pack.id(),ServerboundResourcePackPacket.Action.DECLINED)))) {
            noPack.command("clan create N"+suffix+" N"+suffix); noPack.message("Created clan");
            Screen screen=open(noPack); noPack.events.clear(); int messages=noPack.messages.size();
            gesture(noPack,screen,9,ContainerInput.QUICK_MOVE); none(noPack,"automatic gesture silently skips a client without the pack");
            check(noPack.messages.size()==messages,"automatic gesture adds no chat message without the pack");
        }
        System.out.println("PASS: GUI prank integration complete");
    }
}

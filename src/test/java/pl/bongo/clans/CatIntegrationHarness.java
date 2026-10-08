package pl.bongo.clans;

import static pl.bongo.clans.IntegrationHarness.check;
import java.io.*;
import java.net.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.*;
import net.minecraft.network.protocol.common.*;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.protocol.configuration.ClientboundFinishConfigurationPacket;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.Identifier;

/** Manual cat command over the vanilla wire, with no client mod or clan membership. */
public final class CatIntegrationHarness {
    static boolean catSpawn(Object p) { return p instanceof ClientboundAddEntityPacket e && e.getType() == EntityTypes.ITEM_DISPLAY; }
    static Identifier model(ClientboundSetEntityDataPacket packet) {
        for (var value : packet.packedItems()) if (value.value() instanceof ItemStack stack) return stack.get(DataComponents.ITEM_MODEL);
        return null;
    }
    static void waitBeforeWorld(IntegrationHarness.Bot bot,long millis) throws Exception {
        long until=System.currentTimeMillis()+millis;
        while(System.currentTimeMillis()<until) {
            if(bot.input.available()>0 || bot.socket.getInputStream().available()>0) {
                Object packet=bot.read();
                if(packet instanceof ClientboundFinishConfigurationPacket) throw new AssertionError("Server finished configuration before the pack finished loading");
                if(packet instanceof ClientboundKeepAlivePacket alive) bot.send(new ServerboundKeepAlivePacket(alive.getId()));
                if(packet instanceof ClientboundPingPacket ping) bot.send(new ServerboundPongPacket(ping.getId()));
            } else Thread.sleep(5);
        }
    }
    static void simultaneousConnections(String suffix) throws Exception {
        var barrier=new java.util.concurrent.CountDownLatch(2);
        try(var workers=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var results=new ArrayList<java.util.concurrent.Future<Boolean>>();
            for(int i=0;i<2;i++) {
                String name="CP"+i+suffix;
                results.add(workers.submit(()-> {
                    try(var bot=new IntegrationHarness.Bot(name,"en_us",(client,pack)-> {
                        barrier.countDown();
                        if(!barrier.await(5,java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("Concurrent connection did not receive its pack");
                        client.send(new ServerboundResourcePackPacket(pack.id(),ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED));
                    })) { return bot.configurationPacks.size()==1; }
                }));
            }
            for(var result:results) check(result.get(),"simultaneous connection receives its pre-world pack; shared HTTP host starts once");
        }
    }
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        IntegrationHarness.port = Integer.parseInt(args[0]);
        BuiltInRegistries.ITEM.listElements().forEach(h -> { if (!h.areComponentsBound()) h.bindComponents(DataComponentMap.EMPTY); });
        String suffix = Long.toString(System.currentTimeMillis(), 36);
        simultaneousConnections(suffix);
        try (var viewer = new IntegrationHarness.Bot("CT" + suffix, "pl_pl",(bot,pack)-> {
            check(!pack.required(),"cat pack is offered optionally in configuration, before entering the world");
            check(pack.prompt().orElseThrow().getString().startsWith("Pobierz"),"pre-world prompt uses the client's Polish locale");
            waitBeforeWorld(bot,200);
            bot.send(new ServerboundResourcePackPacket(pack.id(),ServerboundResourcePackPacket.Action.ACCEPTED));
            waitBeforeWorld(bot,200);
            bot.send(new ServerboundResourcePackPacket(pack.id(),ServerboundResourcePackPacket.Action.DOWNLOADED));
            waitBeforeWorld(bot,200);
            check(true,"configuration waits through the prompt, acceptance and download until the client finishes loading");
            bot.send(new ServerboundResourcePackPacket(pack.id(),ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED));
        }); var other = new IntegrationHarness.Bot("CW" + suffix, "en_us",(bot,pack)-> {
            check(pack.prompt().orElseThrow().getString().startsWith("Download"),"pre-world prompt uses the client's English locale");
            bot.send(new ServerboundResourcePackPacket(pack.id(),ServerboundResourcePackPacket.Action.DECLINED));
        })) {
            check(viewer.configurationPacks.size()==1 && other.configurationPacks.size()==1,"each connection receives its pack offer before configuration finishes");
            var offered = viewer.configurationPacks.getFirst();
            URLConnection connection = URI.create(offered.url()).toURL().openConnection();
            connection.setConnectTimeout(5000); connection.setReadTimeout(5000);
            byte[] zip; try (var input = connection.getInputStream()) { zip = input.readAllBytes(); }
            check(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(zip)).equals(offered.hash()), "HTTP download matches the advertised pack hash");
            int textures = 0, models = 0, items = 0, sound = 0;
            try (var archive = new ZipInputStream(new ByteArrayInputStream(zip))) {
                for (ZipEntry entry; (entry = archive.getNextEntry()) != null;) {
                    String name = entry.getName();
                    if (name.contains("textures/item/cat/") && name.endsWith(".png")) textures++;
                    if (name.contains("models/item/cat/")) models++;
                    if (name.contains("/items/cat/")) items++;
                    if (name.endsWith("sounds/cat/clapping.ogg")) sound++;
                }
            }
            check(textures == 137 && models == 137 && items == 137 && sound == 1, "pack contains every frame, model, item definition and OGG sound");
            viewer.drain(150);
            check(viewer.events.stream().noneMatch(CatIntegrationHarness::catSpawn), "loading the pack during connection does not automatically spawn a cat");
            other.command("clan cat");
            other.message("Rejoin the server"); other.drain(100);
            check(other.events.stream().noneMatch(p->p instanceof ClientboundResourcePackPushPacket) && other.events.stream().noneMatch(CatIntegrationHarness::catSpawn),"declining the optional pack still allows world entry; the command only gives a message, without a new prompt");
            viewer.command("clan cat");
            var spawn = (ClientboundAddEntityPacket) viewer.await(CatIntegrationHarness::catSpawn, 5000);
            var first = (ClientboundSetEntityDataPacket) viewer.await(p -> p instanceof ClientboundSetEntityDataPacket d && d.id() == spawn.getId(), 5000);
            check(model(first).getPath().equals("cat/frame_000"), "playback starts at frame zero");
            var audio = (ClientboundSoundPacket) viewer.await(p -> p instanceof ClientboundSoundPacket s && s.getSound().value().location().getPath().equals("cat.clapping"), 5000);
            check(audio.getSound().value().location().getPath().equals("cat.clapping") && audio.getPitch() == 1, "custom sound starts at its original speed");
            long start = System.nanoTime(); int previous = 0, received = 1; boolean removed = false;
            while ((System.nanoTime() - start) < 9_000_000_000L && !removed) {
                viewer.poll(10); other.drain(5);
                for (Object event : new ArrayList<>(viewer.events)) {
                    if (event instanceof ClientboundSetEntityDataPacket data && data.id() == spawn.getId()) {
                        Identifier id = model(data);
                        if (id != null) {
                            int index = Integer.parseInt(id.getPath().substring("cat/frame_".length()));
                            if(index<=previous) throw new AssertionError("Frames played out of order: "+previous+" -> "+index);
                            previous = index; received++;
                        }
                        viewer.events.remove(event);
                    } else if (event instanceof ClientboundRemoveEntitiesPacket end && end.entityIds().contains(spawn.getId())) {
                        removed = true; viewer.events.remove(event);
                    }
                }
            }
            check(removed && previous >= 133 && received > 100, "animation advances at 20 FPS and removes the private display after 6.85 seconds");
            check(other.events.stream().noneMatch(CatIntegrationHarness::catSpawn) && other.events.stream().noneMatch(p -> p instanceof ClientboundSoundPacket s && s.getSound().value().location().getPath().equals("cat.clapping")), "another vanilla client receives neither the cat nor its audio");
            viewer.command("clan cat");
            var replay = (ClientboundAddEntityPacket) viewer.await(CatIntegrationHarness::catSpawn, 5000);
            check(viewer.events.stream().noneMatch(p -> p instanceof ClientboundResourcePackPushPacket), "replay uses the already loaded pack");
            viewer.command("clan cat");
            viewer.await(p -> p instanceof ClientboundRemoveEntitiesPacket end && end.entityIds().contains(replay.getId()), 5000);
            viewer.await(CatIntegrationHarness::catSpawn, 5000);
            check(true, "repeated command replaces the active display instead of stacking cats");
        }
        try(var failed=new IntegrationHarness.Bot("CE"+suffix,"pl_pl",(bot,pack)->
                bot.send(new ServerboundResourcePackPacket(pack.id(),ServerboundResourcePackPacket.Action.FAILED_DOWNLOAD)))) {
            failed.command("clan cat"); failed.message("Dołącz ponownie");
            check(failed.events.stream().noneMatch(CatIntegrationHarness::catSpawn),"failed pack download permits entry and never enables the cat");
        }
        try(var rejoined=new IntegrationHarness.Bot("CT"+suffix,"pl_pl",(bot,pack)->
                bot.send(new ServerboundResourcePackPacket(pack.id(),ServerboundResourcePackPacket.Action.DECLINED)))) {
            rejoined.command("clan cat"); rejoined.message("Dołącz ponownie");
            check(rejoined.events.stream().noneMatch(CatIntegrationHarness::catSpawn),"reconnecting does not inherit the previous session's loaded flag");
        }
        try(var rejoined=new IntegrationHarness.Bot("CW"+suffix,"en_us")) {
            rejoined.command("clan cat"); rejoined.await(CatIntegrationHarness::catSpawn,5000);
            check(true,"a player who previously declined can accept on reconnect and immediately use the cat command");
        }
        System.out.println("PASS: manual cat integration complete");
    }
}

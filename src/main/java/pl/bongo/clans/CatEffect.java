package pl.bongo.clans;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.*;
import net.minecraft.network.protocol.game.*;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.network.config.ServerResourcePackConfigurationTask;
import net.fabricmc.fabric.api.networking.v1.FabricServerConfigurationPacketListenerImpl;
import pl.bongo.clans.mixin.CommonConnectionAccessor;
import pl.bongo.clans.mixin.ConfigurationInfoAccessor;
import net.minecraft.sounds.*;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.Vec3;
import java.io.IOException;
import java.util.*;

/** Viewer-private vanilla display; never added to the world or inventory. */
public final class CatEffect implements AutoCloseable {
    private static final int FRAME_COUNT = 137;
    private static final long FRAME_NANOS = 50_000_000L;
    private static final Identifier SOUND = Identifier.fromNamespaceAndPath(CommunityClans.ID, "cat.clapping");
    private final ClanRuntime runtime;
    private final CatResourcePack pack;
    // Configuration callbacks and disconnects may run on different connection threads.
    private final Set<Connection> loaded = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Set<Connection> offered = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Map<UUID, Playback> active = new HashMap<>();
    private final Map<UUID, Long> pranks = new HashMap<>();
    private static final long PRANK_COOLDOWN_NANOS = 30_000_000_000L;

    public CatEffect(ClanRuntime runtime) throws IOException {
        this.runtime = runtime;
        pack = new CatResourcePack(runtime.server.getPort());
    }

    private static Connection connection(ServerCommonPacketListenerImpl handler) {
        return ((CommonConnectionAccessor) handler).clans$connection();
    }

    public void configure(ServerConfigurationPacketListenerImpl handler) {
        Connection connection = connection(handler);
        if (loaded.contains(connection)) return;
        try { pack.ensureHosted(); }
        catch (IOException e) {
            CommunityClans.LOGGER.error("Could not host cat test pack", e);
            return;
        }
        String language = ((ConfigurationInfoAccessor) handler).clans$clientInformation().language();
        var info = new MinecraftServer.ServerResourcePackInfo(pack.id, pack.url, pack.hash, false,
                Component.literal(Lang.text(language, "cat_pack_prompt")));
        ((FabricServerConfigurationPacketListenerImpl) handler).addTask(new ServerResourcePackConfigurationTask(info) {
            @Override public void start(java.util.function.Consumer<net.minecraft.network.protocol.Packet<?>> sender) {
                offered.add(connection);
                super.start(sender);
            }
        });
    }

    public void request(ServerPlayer player) {
        if (loaded.contains(connection(player.connection))) play(player);
        else runtime.tell(player, "cat_not_loaded");
    }

    /** Automatic gestures remain silent without the pack and never interrupt an active cat. */
    public void prank(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator() || player.hasInfiniteMaterials()
                || !loaded.contains(connection(player.connection)) || active.containsKey(player.getUUID())) return;
        long now = System.nanoTime();
        Long previous = pranks.get(player.getUUID());
        if (previous != null && now - previous < PRANK_COOLDOWN_NANOS) return;
        play(player);
        pranks.put(player.getUUID(), now);
    }

    public void packResponse(ServerCommonPacketListenerImpl handler, ServerboundResourcePackPacket packet) {
        if (!packet.id().equals(pack.id)) return;
        Connection connection = connection(handler);
        if (packet.action() == ServerboundResourcePackPacket.Action.DISCARDED) {
            loaded.remove(connection); offered.remove(connection);
            if (handler instanceof ServerGamePacketListenerImpl game) stop(game.player);
            return;
        }
        if (!offered.contains(connection)) return;
        if (packet.action() == ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED) {
            offered.remove(connection); loaded.add(connection);
        } else if (packet.action().isTerminal()) {
            offered.remove(connection); loaded.remove(connection);
        }
    }

    private static ItemStack frame(int index) {
        ItemStack item = new ItemStack(Items.PAPER);
        item.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath(CommunityClans.ID, String.format(Locale.ROOT, "cat/frame_%03d", index)));
        return item;
    }

    private void play(ServerPlayer player) {
        stop(player);
        Display.ItemDisplay display = new Display.ItemDisplay(EntityTypes.ITEM_DISPLAY, player.level());
        CompoundTag settings = new CompoundTag();
        settings.putString("billboard", "vertical"); settings.putString("item_display", "fixed");
        CompoundTag light = new CompoundTag(); light.putInt("block", 15); light.putInt("sky", 15);
        settings.put("brightness", light); settings.putFloat("width", 2); settings.putFloat("height", 2);
        display.load(TagValueInput.create(ProblemReporter.DISCARDING, runtime.server.registryAccess(), settings));
        double yaw = Math.toRadians(player.getYRot());
        Vec3 position = player.position().add(-Math.sin(yaw) * 1.35, 0.5, Math.cos(yaw) * 1.35);
        display.setPos(position); display.getSlot(0).set(frame(0));
        var spawn = new ClientboundAddEntityPacket(display.getId(), display.getUUID(), position.x, position.y, position.z,
                0, 0, EntityTypes.ITEM_DISPLAY, 0, Vec3.ZERO, 0);
        var data = new ClientboundSetEntityDataPacket(display.getId(), Objects.requireNonNull(display.getEntityData().getNonDefaultValues()));
        var audio = new ClientboundSoundPacket(Holder.direct(SoundEvent.createVariableRangeEvent(SOUND)), SoundSource.PLAYERS,
                position.x, player.getY() + 0.2, position.z, 1, 1, java.util.concurrent.ThreadLocalRandom.current().nextLong());
        // Bundle keeps the first frame and sound together in the vanilla client's game tick.
        player.connection.send(new ClientboundBundlePacket(List.of(spawn, data, audio)));
        display.getEntityData().packDirty();
        active.put(player.getUUID(), new Playback(display, player.level(), System.nanoTime()));
    }

    public void tick() {
        long now = System.nanoTime();
        for (UUID id : new ArrayList<>(active.keySet())) {
            Playback playback = active.get(id);
            ServerPlayer player = runtime.server.getPlayerList().getPlayer(id);
            if (player == null) { active.remove(id); continue; }
            long frame = (now - playback.started) / FRAME_NANOS;
            if (!player.isAlive() || player.level() != playback.level || frame >= FRAME_COUNT) { stop(player); continue; }
            if (frame <= playback.lastFrame) continue;
            playback.display.getSlot(0).set(frame((int) frame));
            var changed = playback.display.getEntityData().packDirty();
            if (changed != null) player.connection.send(new ClientboundSetEntityDataPacket(playback.display.getId(), changed));
            playback.lastFrame = (int) frame;
        }
    }

    private void stop(ServerPlayer player) {
        Playback previous = active.remove(player.getUUID());
        if (previous == null) return;
        player.connection.send(new ClientboundBundlePacket(List.of(new ClientboundRemoveEntitiesPacket(previous.display.getId()),
                new ClientboundStopSoundPacket(SOUND, SoundSource.PLAYERS))));
    }

    public void disconnected(ServerCommonPacketListenerImpl handler) {
        Connection connection = connection(handler);
        offered.remove(connection); loaded.remove(connection);
        if (handler instanceof ServerGamePacketListenerImpl game) {
            UUID id = game.player.getUUID();
            // Playback is owned by the game thread; keep the disconnect callback off its maps.
            runtime.server.execute(() -> { active.remove(id); pranks.remove(id); });
        }
    }
    @Override public void close() {
        for (UUID id : new ArrayList<>(active.keySet())) {
            ServerPlayer player = runtime.server.getPlayerList().getPlayer(id);
            if (player != null) stop(player);
        }
        active.clear(); pranks.clear(); offered.clear(); loaded.clear(); pack.close();
    }

    private static final class Playback {
        final Display.ItemDisplay display;
        final net.minecraft.world.level.Level level;
        final long started;
        int lastFrame;
        Playback(Display.ItemDisplay display, net.minecraft.world.level.Level level, long started) {
            this.display = display; this.level = level; this.started = started;
        }
    }
}

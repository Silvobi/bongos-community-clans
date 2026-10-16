package pl.bongo.clans;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;

public final class CommunityClans implements ModInitializer {
    public static final String ID = "bongo_community_clans";
    public static final Logger LOGGER = LoggerFactory.getLogger(ID);
    public static volatile ClanRuntime runtime;
    @Override public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> ClanCommands.register(dispatcher));
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try {
                runtime = new ClanRuntime(server, new ClanStore(server.getWorldPath(LevelResource.ROOT).resolve("bongo-community-clans.json")));
                runtime.syncDisplay();
                LOGGER.info("Bongo's Community Clans: {} clans loaded", runtime.service.state().clans.size());
            } catch (IOException e) { throw new IllegalStateException("Cannot safely load clan data; original file preserved", e); }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> { if (runtime != null) runtime.cat.close(); });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> runtime = null);
        ServerConfigurationConnectionEvents.CONFIGURE.register((handler, server) -> {
            if (runtime != null) runtime.cat.configure(handler);
        });
        ServerConfigurationConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (runtime != null) runtime.cat.disconnected(handler);
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (runtime == null) return;
            try {
                runtime.service.remember(handler.player.getUUID(), handler.player.getGameProfile().name());
                runtime.syncDisplay();
                var invite = runtime.service.state().invitations.get(handler.player.getUUID());
                if (invite != null && invite.expiresAt > System.currentTimeMillis()) {
                    var clan = runtime.service.state().clans.get(invite.clan);
                    if (clan != null && clan.members.containsKey(invite.inviter))
                        runtime.tell(handler.player, "invitation", clan.members.get(invite.inviter).name, clan.name, tag(clan));
                }
            } catch (RuntimeException e) { LOGGER.error("Could not update joining player's clan data", e); }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (runtime != null) {
                runtime.forgetDialog(handler.player.getUUID());
                runtime.cat.disconnected(handler);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> { if (runtime != null) runtime.tick(); });
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, player, bound) -> {
            if (runtime == null) return true;
            var user = runtime.service.state().users.get(player.getUUID());
            String text = message.signedContent();
            if (user == null || !user.quickchat || !text.startsWith("#") || text.startsWith("##")) return true;
            try { runtime.chat(player, text.substring(1)); }
            catch (ClanData.ClanException e) { runtime.tell(player, e.key); }
            return false;
        });
    }
    public static MutableComponent prefix(ClanData.Clan clan) {
        return Component.literal("[").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(clan.color)))
                .append(tag(clan)).append(Component.literal("] "));
    }
    public static MutableComponent tag(ClanData.Clan clan) { return ClanTag.component(clan.tag, clan.color); }
    public static MutableComponent displayName(ClanData.Clan clan, String name) {
        return Component.empty().append(prefix(clan)).append(Component.literal(name).withStyle(ChatFormatting.WHITE));
    }
}

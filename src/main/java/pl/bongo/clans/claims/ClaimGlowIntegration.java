package pl.bongo.clans.claims;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import static pl.bongo.clans.claims.GpClaims.*;

/** Uses GP3D's own glowing displays, expiry and refresh; suppresses only its fake-block sends. */
public final class ClaimGlowIntegration {
    public static Object renderer;
    public static Object settings() {
        if (renderer == null) throw new IllegalStateException("GP3D visualization adapter is unavailable");
        return field(renderer, "settings");
    }
    public static boolean glow(ServerPlayer player) {
        if (renderer == null || !(boolean)call(call(settings(), "tools"), "visualizationGlow")) return false;
        try { return (boolean)call(call(field(renderer, "claims"), "claimBlockService"), "glowEnabled", player.getUUID()); }
        catch (IllegalStateException e) { return true; } // Match GP3D's default on player-data read failure.
    }
    public static void fakeBlock(ServerPlayer player, BlockPos pos, BlockState state) {
        if (!glow(player)) player.connection.send(new ClientboundBlockUpdatePacket(pos, state));
    }
    public static void show(ServerPlayer player, Object root) {
        if (renderer != null) call(renderer, "visualizeClaim", player, player.level(), root, player.blockPosition());
    }
}

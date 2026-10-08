package pl.bongo.clans.mixin;

import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.bongo.clans.ClanInfoMenu;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ClanGuiCloseMixin {
    @Shadow public ServerPlayer player;
    @Unique private ClanInfoMenu clans$closing;

    // Vanilla has already moved handling to the game thread at this invocation.
    @Inject(method = "handleContainerClose", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;doCloseContainer()V"))
    private void clans$beforeClose(ServerboundContainerClosePacket packet, CallbackInfo ci) {
        clans$closing = player.containerMenu instanceof ClanInfoMenu menu && menu.containerId == packet.getContainerId() ? menu : null;
    }

    @Inject(method = "handleContainerClose", at = @At("RETURN"))
    private void clans$afterClose(ServerboundContainerClosePacket packet, CallbackInfo ci) {
        ClanInfoMenu menu = clans$closing; clans$closing = null;
        if (menu != null) menu.clientClosed(player);
    }
}

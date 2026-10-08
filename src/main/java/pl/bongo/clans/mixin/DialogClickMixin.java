package pl.bongo.clans.mixin;

import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.bongo.clans.CommunityClans;

@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class DialogClickMixin {
    @Inject(method = "handleCustomClickAction", at = @At("RETURN"))
    private void clans$click(ServerboundCustomClickActionPacket packet, CallbackInfo ci) {
        if (!packet.id().getNamespace().equals(CommunityClans.ID)) return;
        if ((Object) this instanceof ServerGamePacketListenerImpl game && CommunityClans.runtime != null)
            CommunityClans.runtime.click(game.player, packet.id().getPath());
    }
}

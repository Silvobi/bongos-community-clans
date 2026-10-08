package pl.bongo.clans.mixin;

import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.bongo.clans.CommunityClans;

@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ResourcePackMixin {
    // RETURN runs after vanilla's main-thread check.
    @Inject(method = "handleResourcePackResponse", at = @At("RETURN"))
    private void clans$catPack(ServerboundResourcePackPacket packet, CallbackInfo ci) {
        if (CommunityClans.runtime != null)
            CommunityClans.runtime.cat.packResponse((ServerCommonPacketListenerImpl) (Object) this, packet);
    }
}

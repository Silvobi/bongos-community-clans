package pl.bongo.clans.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.bongo.clans.CommunityClans;

@Mixin(ServerPlayer.class)
public abstract class TabNameMixin {
    @Inject(method = "getTabListDisplayName", at = @At("RETURN"), cancellable = true)
    private void clans$tab(CallbackInfoReturnable<Component> callback) {
        var runtime = CommunityClans.runtime;
        if (runtime == null) return;
        ServerPlayer player = (ServerPlayer) (Object) this;
        var clan = runtime.service.state().clan(player.getUUID());
        if (clan != null) callback.setReturnValue(CommunityClans.displayName(clan, player.getGameProfile().name()));
    }
}

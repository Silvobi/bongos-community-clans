package pl.bongo.clans.mixin;

import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.bongo.clans.claims.ClaimMenus;

/** Keep BongoUtils' command and global flag UI; local opens go to BongoCC's menu. */
@Pseudo
@Mixin(targets = "pl.bongo.bongoutils.claims.ManageClaimUi", remap = false)
public abstract class ManageClaimMenuMixin {
    @Inject(method = "open", at = @At("HEAD"), cancellable = true, remap = false)
    private void bongo$claimMenu(ServerPlayer player, boolean global, CallbackInfoReturnable<Integer> ci) {
        if (ClaimMenus.active == null) return;
        ClaimMenus.flagsUi = this;
        ClaimMenus.active.forget(player.getUUID());
        if (!global) ci.setReturnValue(ClaimMenus.active.open(player));
    }
}

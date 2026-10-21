package pl.bongo.clans.mixin;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.bongo.clans.claims.*;

@Pseudo
@Mixin(targets = "com.griefprevention.fabric.mc26_1.FabricAdminClaimCommands", remap = false)
public abstract class GpTransferMenuHintMixin {
    @Inject(method = "giveAway", at = @At("HEAD"), cancellable = true, remap = false)
    private void bongo$transferHint(@Coerce Object sender, net.minecraft.server.level.ServerPlayer player, String[] args, CallbackInfoReturnable<Boolean> ci) {
        if (ClaimCommandHints.send(sender, "claims")) ci.setReturnValue(true);
    }
}

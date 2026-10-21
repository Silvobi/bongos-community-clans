package pl.bongo.clans.mixin;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.bongo.clans.claims.ClaimCommandHints;

@Pseudo
@Mixin(targets = "com.griefprevention.fabric.mc26_1.FabricClaimCommands", remap = false)
public abstract class GpClaimMenuHintsMixin {
    @Inject(method = "claimsList", at = @At("HEAD"), cancellable = true, remap = false)
    private void bongo$claimsListHint(@Coerce Object sender, String[] args, CallbackInfoReturnable<Boolean> ci) {
        if (args.length == 0 && ClaimCommandHints.send(sender, "claims")) ci.setReturnValue(true);
    }
    @Inject(method = {"abandon", "abandonClaim", "abandonTopLevelClaim", "abandonAllClaims"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void bongo$deleteHint(@Coerce Object sender, String[] args, CallbackInfoReturnable<Boolean> ci) {
        if (ClaimCommandHints.send(sender, "claims")) ci.setReturnValue(true);
    }
    @Inject(method = {"claimPvp", "claimExplosions", "witherExplosions", "claimAlerts"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void bongo$flagHint(@Coerce Object sender, String[] args, CallbackInfoReturnable<Boolean> ci) {
        if (ClaimCommandHints.send(sender, "flags")) ci.setReturnValue(true);
    }
    @Inject(method = "toggleAllClaimAlerts", at = @At("HEAD"), cancellable = true, remap = false)
    private void bongo$allFlagsHint(@Coerce Object sender, String[] args, CallbackInfoReturnable<Boolean> ci) {
        if (ClaimCommandHints.send(sender, "allflags")) ci.setReturnValue(true);
    }
}

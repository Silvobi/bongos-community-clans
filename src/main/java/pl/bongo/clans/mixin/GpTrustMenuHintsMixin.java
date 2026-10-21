package pl.bongo.clans.mixin;

import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import pl.bongo.clans.claims.ClaimCommandHints;

@Pseudo
@Mixin(targets = "com.griefprevention.fabric.mc26_1.FabricTrustCommands", remap = false)
public abstract class GpTrustMenuHintsMixin {
    @Inject(method = "grant", at = @At("HEAD"), cancellable = true, remap = false)
    private void bongo$addHint(@Coerce Object sender, ServerPlayer player, @Coerce Object level, String recipient, CallbackInfo ci) {
        // Staff permission-node commands remain available; the player picker edits people.
        if (!recipient.startsWith("[") && ClaimCommandHints.send(sender, "add")) ci.cancel();
    }
    @Inject(method = "untrust", at = @At("HEAD"), cancellable = true, remap = false)
    private void bongo$removeHint(@Coerce Object sender, String[] args, CallbackInfoReturnable<Boolean> ci) {
        if (args.length == 0 || !args[0].startsWith("[")) if (ClaimCommandHints.send(sender, "remove")) ci.setReturnValue(true);
    }
    @Inject(method = "trustList", at = @At("HEAD"), cancellable = true, remap = false)
    private void bongo$listHint(@Coerce Object sender, String[] args, CallbackInfoReturnable<Boolean> ci) {
        if (ClaimCommandHints.send(sender, "access")) ci.setReturnValue(true);
    }
}

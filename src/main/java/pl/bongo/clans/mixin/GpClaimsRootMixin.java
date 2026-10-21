package pl.bongo.clans.mixin;

import net.minecraft.commands.CommandSourceStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.bongo.clans.claims.ClaimMenus;

/** GP may itself register /claims via alias.yml after other mods register commands. */
@Pseudo
@Mixin(targets = "com.griefprevention.fabric.mc26_1.FabricCommandRegistrar", remap = false)
public abstract class GpClaimsRootMixin {
    @Inject(method = "run", at = @At("HEAD"), cancellable = true, remap = false)
    private void bongo$claimsRoot(String label, CommandSourceStack source, String rawArguments, CallbackInfoReturnable<Integer> ci) {
        if (label.equalsIgnoreCase("claims") && rawArguments.isBlank() && source.getPlayer() != null && ClaimMenus.active != null) {
            ClaimMenus.active.overview(source.getPlayer()); ci.setReturnValue(1);
        }
    }
}

package pl.bongo.clans.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import pl.bongo.clans.claims.ClaimGlowIntegration;

/** Exact GP3D 19 Fabric hooks. Real block restoration and glowing display lifecycle stay native. */
@Pseudo
@Mixin(targets = "com.griefprevention.fabric.mc26_1.FabricFakeBlockVisualization", remap = false)
public abstract class GpGlowOnlyMixin {
    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void bongo$captureVisualization(CallbackInfo ci) { ClaimGlowIntegration.renderer = this; }

    @Redirect(method = {"render", "resendBrokenVisual"}, at = @At(value = "INVOKE", target = "Lcom/griefprevention/fabric/mc26_1/FabricFakeBlockVisualization;sendBlock(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V"), remap = false)
    private void bongo$glowInsteadOfBlocks(ServerPlayer player, BlockPos pos, BlockState state) { ClaimGlowIntegration.fakeBlock(player, pos, state); }

    @Redirect(method = "resend", at = @At(value = "INVOKE", target = "Lcom/griefprevention/fabric/mc26_1/FabricFakeBlockVisualization;sendBlock(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V"), remap = false)
    private static void bongo$glowResend(ServerPlayer player, BlockPos pos, BlockState state) { ClaimGlowIntegration.fakeBlock(player, pos, state); }

    @Inject(method = "handleBlockAttack", at = @At("RETURN"), cancellable = true, remap = false)
    private void bongo$allowRealBlockAttack(ServerPlayer player, BlockPos pos, CallbackInfoReturnable<Boolean> ci) {
        if (ClaimGlowIntegration.glow(player)) ci.setReturnValue(false);
    }
}

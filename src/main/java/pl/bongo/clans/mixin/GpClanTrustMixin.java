package pl.bongo.clans.mixin;

import java.util.UUID;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.bongo.clans.claims.ClanClaims;

@Pseudo
@Mixin(targets="com.griefprevention.fabric.mc26_1.FabricClaimRepository",remap=false)
public abstract class GpClanTrustMixin {
    @Unique private final ThreadLocal<Integer> bongo$trustDepth=ThreadLocal.withInitial(()->0);
    @Inject(method="allows(Lcom/griefprevention/claims/ClaimSnapshot;Ljava/util/UUID;Lcom/griefprevention/claims/ClaimTrustLevel;)Z",at=@At("HEAD"),remap=false)
    private void bongo$beginClanTrust(@Coerce Object claim,UUID player,@Coerce Object required,CallbackInfoReturnable<Boolean> ci) {
        bongo$trustDepth.set(bongo$trustDepth.get()+1);
    }
    @Inject(method="allows(Lcom/griefprevention/claims/ClaimSnapshot;Ljava/util/UUID;Lcom/griefprevention/claims/ClaimTrustLevel;)Z",at=@At("RETURN"),cancellable=true,remap=false)
    private void bongo$clanTrust(@Coerce Object claim,UUID player,@Coerce Object required,CallbackInfoReturnable<Boolean> ci) {
        int depth=bongo$trustDepth.get();
        if(depth<=1) bongo$trustDepth.remove(); else bongo$trustDepth.set(depth-1);
        // Native parent recursion must not smuggle a parent's clan grant past a child's local off.
        if(depth==1 && !ci.getReturnValue() && ClanClaims.extraAccess(this,claim,player,required)) ci.setReturnValue(true);
    }
}

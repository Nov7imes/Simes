package cn.simmc.simpricedisplay.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Compatibility guard for Xaero's World Map 1.44.2.
 *
 * <p>During rapid proxy transfers the old world can finish while a region is
 * still marked as refreshing. Xaero treats that transient state as fatal in
 * MapRegion#onCurrentDimFinish. Clearing the stale flags is equivalent to
 * abandoning work for the world that is already being unloaded.</p>
 */
@Pseudo
@Mixin(targets = "xaero.map.region.MapRegion", remap = false)
public abstract class XaeroMapRegionMixin {
    @Shadow(remap = false)
    public abstract byte getLoadState();

    @Shadow(remap = false)
    public abstract boolean isRefreshing();

    @Shadow(remap = false)
    public abstract void setRefreshing(boolean refreshing);

    @Shadow(remap = false)
    public abstract void setBeingWritten(boolean beingWritten);

    @Inject(
            method = "onCurrentDimFinish",
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false
    )
    private void simes$recoverStaleRefreshState(CallbackInfo ci) {
        if (getLoadState() != 2 && isRefreshing()) {
            setBeingWritten(false);
            setRefreshing(false);
            ci.cancel();
        }
    }
}

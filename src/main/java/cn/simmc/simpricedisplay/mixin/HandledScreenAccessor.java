package cn.simmc.simpricedisplay.mixin;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(HandledScreen.class)
public interface HandledScreenAccessor {
	@Accessor("x") int simes$getX();
	@Accessor("y") int simes$getY();
	@Accessor("backgroundWidth") int simes$getBackgroundWidth();
	@Accessor("backgroundHeight") int simes$getBackgroundHeight();
}

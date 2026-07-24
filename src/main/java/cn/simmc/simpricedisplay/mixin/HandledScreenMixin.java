package cn.simmc.simpricedisplay.mixin;

import cn.simmc.simpricedisplay.CoordinateCopyController;
import cn.simmc.simpricedisplay.ValuePanelController;
import cn.simmc.simpricedisplay.ValuePanelRenderer;
import cn.simmc.simpricedisplay.MarketDetailsController;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.slot.Slot;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin {
	@Shadow @Nullable protected Slot focusedSlot;

	@Inject(method = "drawMouseoverTooltip", at = @At("HEAD"), cancellable = true)
	private void simes$renderValuePanelBelowTooltip(
			DrawContext context,
			int mouseX,
			int mouseY,
			CallbackInfo callback
	) {
		HandledScreen<?> screen = (HandledScreen<?>)(Object)this;
		HandledScreenAccessor bounds = (HandledScreenAccessor)this;
		ValuePanelRenderer.render(screen, context,
				bounds.simes$getX(), bounds.simes$getY(),
				bounds.simes$getBackgroundWidth(), bounds.simes$getBackgroundHeight());
		MarketDetailsController.render(screen, context, mouseX, mouseY);
		if (MarketDetailsController.isLocked()) {
			// The locked details panel replaces the vanilla item tooltip (including
			// Simes' compact tooltip prices), keeping the selected item visually stable.
			callback.cancel();
		}
	}
	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void simes$copyShopCoordinates(
			int keyCode,
			int scanCode,
			int modifiers,
			CallbackInfoReturnable<Boolean> callback
	) {
		HandledScreen<?> screen = (HandledScreen<?>)(Object)this;
		if (screen.getFocused() instanceof TextFieldWidget) {
			return;
		}
		if (MarketDetailsController.handleKey(screen, focusedSlot, keyCode, scanCode)
				|| ValuePanelController.handleKey(keyCode, scanCode)
				|| CoordinateCopyController.handleKey(focusedSlot, keyCode, scanCode)) {
			callback.setReturnValue(true);
		}
	}

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void simes$copyShopCoordinatesWithMouse(
			double mouseX,
			double mouseY,
			int button,
			CallbackInfoReturnable<Boolean> callback
	) {
		if (MarketDetailsController.handleMouse(mouseX, mouseY, button)
				|| ValuePanelController.handleMouse(button)
				|| CoordinateCopyController.handleMouse(focusedSlot, button)) {
			callback.setReturnValue(true);
		}
	}
}

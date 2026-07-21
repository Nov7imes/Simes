package cn.simmc.simpricedisplay.mixin;

import cn.simmc.simpricedisplay.ArcaneCooldownHud;
import cn.simmc.simpricedisplay.DebugRecorder;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.OverlayMessageS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {
	@Inject(method = "onOverlayMessage", at = @At("HEAD"), cancellable = true)
	private void simes$recordOverlayPacket(OverlayMessageS2CPacket packet, CallbackInfo ci) {
		DebugRecorder.recordActionBar(packet.text());
		if (ArcaneCooldownHud.handleActionBar(packet.text())) {
			ci.cancel();
		}
	}
}

package cn.simmc.simpricedisplay.mixin;

import cn.simmc.simpricedisplay.DebugRecorder;
import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.network.packet.s2c.common.CustomPayloadS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientCommonNetworkHandler.class)
public abstract class ClientCommonNetworkHandlerMixin {
	@Inject(method = "onCustomPayload", at = @At("HEAD"))
	private void simes$customPayload(CustomPayloadS2CPacket packet, CallbackInfo ci) {
		DebugRecorder.recordCombatPacket("PACKET_CUSTOM_PAYLOAD", packet);
	}
}

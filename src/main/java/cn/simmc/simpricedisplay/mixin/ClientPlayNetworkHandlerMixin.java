package cn.simmc.simpricedisplay.mixin;

import cn.simmc.simpricedisplay.ArcaneCooldownHud;
import cn.simmc.simpricedisplay.ArcaneStatusHud;
import cn.simmc.simpricedisplay.DebugRecorder;
import cn.simmc.simpricedisplay.ManaHud;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.OverlayMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.*;
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

	@Inject(method = "onBossBar", at = @At("HEAD"), cancellable = true)
	private void simes$bossBar(BossBarS2CPacket p, CallbackInfo ci) {
		DebugRecorder.recordBossBar(p);
		if (ArcaneStatusHud.handleBossBar(p)) ci.cancel();
	}
	@Inject(method = "onEntitySpawn", at = @At("HEAD")) private void simes$spawn(EntitySpawnS2CPacket p, CallbackInfo ci) { DebugRecorder.recordCombatPacket("PACKET_ENTITY_SPAWN", p); }
	@Inject(method = "onEntityAnimation", at = @At("HEAD")) private void simes$animation(EntityAnimationS2CPacket p, CallbackInfo ci) { DebugRecorder.recordCombatPacket("PACKET_ENTITY_ANIMATION", p); }
	@Inject(method = "onEntityDamage", at = @At("HEAD")) private void simes$damage(EntityDamageS2CPacket p, CallbackInfo ci) { DebugRecorder.recordCombatPacket("PACKET_ENTITY_DAMAGE", p); }
	@Inject(method = "onEntityStatus", at = @At("HEAD")) private void simes$status(EntityStatusS2CPacket p, CallbackInfo ci) { DebugRecorder.recordCombatPacket("PACKET_ENTITY_STATUS", p); }
	@Inject(method = "onEntityTrackerUpdate", at = @At("HEAD")) private void simes$metadata(EntityTrackerUpdateS2CPacket p, CallbackInfo ci) { DebugRecorder.recordCombatPacket("PACKET_ENTITY_METADATA", p); }
	@Inject(method = "onEntitiesDestroy", at = @At("HEAD")) private void simes$destroy(EntitiesDestroyS2CPacket p, CallbackInfo ci) { DebugRecorder.recordCombatPacket("PACKET_ENTITY_DESTROY", p); }
	@Inject(method = "onBlockUpdate", at = @At("HEAD")) private void simes$blockUpdate(BlockUpdateS2CPacket p, CallbackInfo ci) { DebugRecorder.recordCombatPacket("PACKET_BLOCK_UPDATE", p); }
	@Inject(method = "onOpenScreen", at = @At("HEAD")) private void simes$openScreen(OpenScreenS2CPacket p, CallbackInfo ci) { DebugRecorder.recordCombatPacket("PACKET_OPEN_SCREEN", p); }
	@Inject(method = "onCloseScreen", at = @At("HEAD")) private void simes$closeScreen(CloseScreenS2CPacket p, CallbackInfo ci) { DebugRecorder.recordCombatPacket("PACKET_CLOSE_SCREEN", p); }
	@Inject(method = "onInventory", at = @At("HEAD")) private void simes$inventory(InventoryS2CPacket p, CallbackInfo ci) { DebugRecorder.recordCombatPacket("PACKET_INVENTORY", p); }
	@Inject(method = "onScreenHandlerSlotUpdate", at = @At("HEAD")) private void simes$slot(ScreenHandlerSlotUpdateS2CPacket p, CallbackInfo ci) { DebugRecorder.recordCombatPacket("PACKET_SLOT_UPDATE", p); }
	@Inject(method = "onScreenHandlerPropertyUpdate", at = @At("HEAD")) private void simes$property(ScreenHandlerPropertyUpdateS2CPacket p, CallbackInfo ci) { DebugRecorder.recordCombatPacket("PACKET_SCREEN_PROPERTY", p); }
	@Inject(method = "onDamageTilt", at = @At("HEAD")) private void simes$tilt(DamageTiltS2CPacket p, CallbackInfo ci) { DebugRecorder.recordCombatPacket("PACKET_DAMAGE_TILT", p); }
	@Inject(method = "onHealthUpdate", at = @At("HEAD")) private void simes$health(HealthUpdateS2CPacket p, CallbackInfo ci) { DebugRecorder.recordCombatPacket("PACKET_PLAYER_HEALTH", p); }
	@Inject(method = "onExperienceBarUpdate", at = @At("HEAD"), cancellable = true)
	private void simes$experience(ExperienceBarUpdateS2CPacket p, CallbackInfo ci) {
		DebugRecorder.recordExperiencePacket(p);
		if (ManaHud.handleExperiencePacket(p)) ci.cancel();
	}
	@Inject(method = "onEnterCombat", at = @At("HEAD")) private void simes$combatEnter(EnterCombatS2CPacket p, CallbackInfo ci) { DebugRecorder.recordCombatPacket("PACKET_COMBAT_ENTER", p); }
	@Inject(method = "onEndCombat", at = @At("HEAD")) private void simes$combatEnd(EndCombatS2CPacket p, CallbackInfo ci) { DebugRecorder.recordCombatPacket("PACKET_COMBAT_END", p); }
	@Inject(method = "onDeathMessage", at = @At("HEAD")) private void simes$death(DeathMessageS2CPacket p, CallbackInfo ci) { DebugRecorder.recordCombatPacket("PACKET_DEATH", p); }
	@Inject(method = "onExplosion", at = @At("HEAD")) private void simes$explosion(ExplosionS2CPacket p, CallbackInfo ci) { DebugRecorder.recordCombatPacket("PACKET_EXPLOSION", p); }
	@Inject(method = "onParticle", at = @At("HEAD")) private void simes$particle(ParticleS2CPacket p, CallbackInfo ci) { DebugRecorder.recordCombatPacket("PACKET_PARTICLE", p); }
	@Inject(method = "onPlaySound", at = @At("HEAD")) private void simes$sound(PlaySoundS2CPacket p, CallbackInfo ci) { DebugRecorder.recordCombatPacket("PACKET_SOUND", p); }
	@Inject(method = "onPlaySoundFromEntity", at = @At("HEAD")) private void simes$entitySound(PlaySoundFromEntityS2CPacket p, CallbackInfo ci) { DebugRecorder.recordCombatPacket("PACKET_ENTITY_SOUND", p); }
	@Inject(method = "onEntityVelocityUpdate", at = @At("HEAD")) private void simes$velocity(EntityVelocityUpdateS2CPacket p, CallbackInfo ci) { DebugRecorder.recordCombatPacket("PACKET_ENTITY_VELOCITY", p); }
	@Inject(method = "onEntityEquipmentUpdate", at = @At("HEAD")) private void simes$equipment(EntityEquipmentUpdateS2CPacket p, CallbackInfo ci) { DebugRecorder.recordCombatPacket("PACKET_ENTITY_EQUIPMENT", p); }
	@Inject(method = "onEntityAttributes", at = @At("HEAD")) private void simes$attributes(EntityAttributesS2CPacket p, CallbackInfo ci) { DebugRecorder.recordCombatPacket("PACKET_ENTITY_ATTRIBUTES", p); }
	@Inject(method = "onEntityStatusEffect", at = @At("HEAD")) private void simes$effect(EntityStatusEffectS2CPacket p, CallbackInfo ci) { DebugRecorder.recordCombatPacket("PACKET_ENTITY_EFFECT", p); }
	@Inject(method = "onRemoveEntityStatusEffect", at = @At("HEAD")) private void simes$effectRemove(RemoveEntityStatusEffectS2CPacket p, CallbackInfo ci) { DebugRecorder.recordCombatPacket("PACKET_ENTITY_EFFECT_REMOVE", p); }
}

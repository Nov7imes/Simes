package cn.simmc.simpricedisplay;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.text.Text;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.network.packet.s2c.play.BossBarS2CPacket;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/** A short, explicitly enabled local trace for investigating server-driven cooldown displays. */
public final class DebugRecorder {
	private static final Duration MAX_DURATION = Duration.ofMinutes(10);
	private static final int MAX_LOG_CHARACTERS = 32 * 1024 * 1024;
	private static final int MAX_VALUE_CHARACTERS = 4_096;
	private static final int MAX_PACKET_VALUE_CHARACTERS = 2_048;
	private static final long ENTITY_SCAN_INTERVAL_MILLIS = 200L;
	private static final long COOKWARE_SCAN_INTERVAL_MILLIS = 100L;
	private static final long COOKWARE_TARGET_RETENTION_MILLIS = 15_000L;
	private static final long RECENT_PACKET_RETENTION_MILLIS = 2_000L;
	private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
			.withZone(ZoneId.systemDefault());
	private static final DateTimeFormatter LINE_TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
			.withZone(ZoneId.systemDefault());
	private static final List<String> lines = new ArrayList<>();
	private static boolean recording;
	private static Instant startedAt;
	private static String lastState = "";
	private static String lastValuePanelState = "";
	private static String lastArcaneRenderState = "";
	private static String lastMainHandFingerprint = "";
	private static String lastOffHandFingerprint = "";
	private static int lastSelectedSlot = -1;
	private static int lastManaLevel = Integer.MIN_VALUE;
	private static int lastTotalExperience = Integer.MIN_VALUE;
	private static float lastExperienceProgress = Float.NaN;
	private static long lastStateCaptureMillis;
	private static long lastCombatCaptureMillis;
	private static long lastCookwareCaptureMillis;
	private static BlockPos cookwareTarget;
	private static long cookwareTargetAtMillis;
	private static final Map<Integer, String> cookwareDisplays = new HashMap<>();
	private static boolean lastAttackPressed;
	private static final Map<Integer, CombatEntityState> combatEntities = new HashMap<>();
	private static final Map<String, Long> recentPackets = new HashMap<>();
	private static final Map<String, PacketRateWindow> packetRateWindows = new HashMap<>();
	private static final Map<Integer, String> lastInventorySlots = new HashMap<>();
	private static final Map<Integer, String> lastHandlerSlots = new HashMap<>();
	private static String lastHandlerIdentity = "";
	private static int recordedCharacters;
	private static boolean logTruncated;
	private static long lastPacketCleanupMillis;

	private DebugRecorder() {
	}

	public static boolean isRecording() {
		return recording;
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
				dispatcher.register(literal("debug")
						.then(literal("start").executes(context -> start()))
						.then(literal("end").executes(context -> end(false)))));
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (recording && !overlay) {
				append("GAME_MESSAGE", message.getString());
			}
		});
		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (!recording || player != MinecraftClient.getInstance().player) return ActionResult.PASS;
			cookwareTarget = hitResult.getBlockPos().toImmutable();
			cookwareTargetAtMillis = System.currentTimeMillis();
			append("COOKWARE_INTERACT", "hand=" + hand
					+ " | pos=" + blockPos(cookwareTarget)
					+ " | side=" + hitResult.getSide()
					+ " | block=" + Registries.BLOCK.getId(world.getBlockState(cookwareTarget).getBlock())
					+ " | state=" + world.getBlockState(cookwareTarget)
					+ " | held=" + itemDetails(player.getStackInHand(hand)));
			captureCookwareProbe(MinecraftClient.getInstance(), true);
			return ActionResult.PASS;
		});
		ClientTickEvents.END_CLIENT_TICK.register(DebugRecorder::tick);
	}

	public static void recordActionBar(Text message) {
		if (recording) {
			append("ACTION_BAR_PACKET", message.getString());
		}
	}

	public static void recordArcaneDecision(String value) {
		if (recording) append("ARCANE_DECISION", value);
	}

	public static void recordArcaneRender(String value) {
		if (!recording || value == null || value.equals(lastArcaneRenderState)) return;
		lastArcaneRenderState = value;
		append("ARCANE_RENDER", value);
	}

	public static String codePoints(String value) {
		if (value == null || value.isEmpty()) return "[]";
		StringBuilder result = new StringBuilder("[");
		value.codePoints().limit(160).forEach(codePoint -> {
			if (result.length() > 1) result.append(' ');
			result.append("U+").append(String.format(java.util.Locale.ROOT, "%04X", codePoint));
		});
		return result.append(']').toString();
	}

	public static void recordValuePanel(String state) {
		if (!recording || state == null || state.equals(lastValuePanelState)) return;
		lastValuePanelState = state;
		append("VALUE_PANEL", state);
	}

	public static void recordCookware(String type, String value) {
		if (recording) append("COOKWARE_" + type, value);
	}

	public static void recordCombatPacket(String type, Object packet) {
		if (!recording) return;
		long now = System.currentTimeMillis();
		PacketRateWindow rateWindow = packetRateWindows.computeIfAbsent(type, ignored -> new PacketRateWindow(now));
		if (!rateWindow.tryAcquire(now, packetLimit(type))) return;
		String summary = limit(clean(String.valueOf(packet)), MAX_PACKET_VALUE_CHARACTERS);
		String key = type + '|' + summary;
		Long previous = recentPackets.put(key, now);
		if (previous != null && now - previous < 25L) return;
		if (now - lastPacketCleanupMillis >= RECENT_PACKET_RETENTION_MILLIS) {
			lastPacketCleanupMillis = now;
			recentPackets.entrySet().removeIf(entry -> now - entry.getValue() >= RECENT_PACKET_RETENTION_MILLIS);
		}
		append(type, summary);
	}

	public static void recordBossBar(BossBarS2CPacket packet) {
		if (!recording) return;
		packet.accept(new BossBarS2CPacket.Consumer() {
			@Override public void add(UUID id, Text name, float percent, BossBar.Color color, BossBar.Style style,
					boolean darkenSky, boolean dragonMusic, boolean thickenFog) {
				append("BOSS_BAR", "ADD id=" + id + " name=\"" + name.getString() + "\" percent=" + decimal(percent)
						+ " color=" + color + " style=" + style);
			}
			@Override public void remove(UUID id) { append("BOSS_BAR", "REMOVE id=" + id); }
			@Override public void updateProgress(UUID id, float percent) { append("BOSS_BAR", "PROGRESS id=" + id + " percent=" + decimal(percent)); }
			@Override public void updateName(UUID id, Text name) { append("BOSS_BAR", "NAME id=" + id + " name=\"" + name.getString() + "\""); }
			@Override public void updateStyle(UUID id, BossBar.Color color, BossBar.Style style) { append("BOSS_BAR", "STYLE id=" + id + " color=" + color + " style=" + style); }
			@Override public void updateProperties(UUID id, boolean darkenSky, boolean dragonMusic, boolean thickenFog) {
				append("BOSS_BAR", "PROPERTIES id=" + id + " darkenSky=" + darkenSky + " dragonMusic=" + dragonMusic + " thickenFog=" + thickenFog);
			}
		});
	}

	private static int start() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (recording) {
			localMessage(client, "§e[Simes] Debug 已经在记录中，请输入 /debug end 结束");
			return 0;
		}
		recording = true;
		startedAt = Instant.now();
		lines.clear();
		lastState = "";
		lastValuePanelState = "";
		lastArcaneRenderState = "";
		lastMainHandFingerprint = "";
		lastOffHandFingerprint = "";
		lastSelectedSlot = -1;
		lastManaLevel = Integer.MIN_VALUE;
		lastTotalExperience = Integer.MIN_VALUE;
		lastExperienceProgress = Float.NaN;
		lastStateCaptureMillis = 0L;
		lastCombatCaptureMillis = 0L;
		lastCookwareCaptureMillis = 0L;
		cookwareTarget = null;
		cookwareTargetAtMillis = 0L;
		cookwareDisplays.clear();
		lastAttackPressed = false;
		combatEntities.clear();
		recentPackets.clear();
		packetRateWindows.clear();
		lastInventorySlots.clear();
		lastHandlerSlots.clear();
		lastHandlerIdentity = "";
		recordedCharacters = 0;
		logTruncated = false;
		lastPacketCleanupMillis = 0L;
		append("DEBUG", "START | Minecraft combat/client trace | max=10m");
		captureState(client, true);
		captureHeldItems(client, true);
		captureMana(client, true);
		captureCombatEntities(client, true);
		captureCookwareProbe(client, true);
		captureInventoryAndScreen(client, true);
		localMessage(client, "§a[Simes] Debug 记录已开始");
		return 1;
	}

	private static int end(boolean timedOut) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (!recording) {
			localMessage(client, "§e[Simes] 当前没有正在进行的 Debug 记录");
			return 0;
		}
		captureState(client, true);
		captureHeldItems(client, true);
		captureMana(client, true);
		captureCombatEntities(client, true);
		captureCookwareProbe(client, true);
		captureInventoryAndScreen(client, true);
		append("DEBUG", timedOut ? "AUTO END | 10 minute limit" : "END");
		recording = false;
		Path file = writeLog();
		if (file == null) {
			localMessage(client, "§c[Simes] Debug 日志写入失败，请查看 latest.log");
			return 0;
		}
		localMessage(client, (timedOut ? "§e[Simes] Debug 已达到 10 分钟并自动结束：" : "§a[Simes] Debug 记录已结束：")
				+ file.toAbsolutePath());
		return 1;
	}

	private static void tick(MinecraftClient client) {
		if (!recording) {
			return;
		}
		if (Duration.between(startedAt, Instant.now()).compareTo(MAX_DURATION) >= 0) {
			end(true);
			return;
		}
		captureState(client, false);
		captureHeldItems(client, false);
		captureMana(client, false);
		captureAttackInput(client);
		captureCombatEntities(client, false);
		captureCookwareProbe(client, false);
		captureInventoryAndScreen(client, false);
	}

	private static void captureInventoryAndScreen(MinecraftClient client, boolean force) {
		if (client.player == null) return;
		String screen = client.currentScreen == null ? "none" : client.currentScreen.getClass().getName();
		var handler = client.player.currentScreenHandler;
		String handlerIdentity = handler.getClass().getName() + "|syncId=" + handler.syncId
				+ "|slots=" + handler.slots.size() + "|screen=" + screen;
		if (force || !handlerIdentity.equals(lastHandlerIdentity)) {
			append("GUI_STATE", handlerIdentity + " | cursor=" + itemDetails(handler.getCursorStack()));
			lastHandlerIdentity = handlerIdentity;
			lastHandlerSlots.clear();
		}

		Map<Integer, String> inventoryNow = new HashMap<>();
		for (int slot = 0; slot < client.player.getInventory().size(); slot++) {
			String value = itemFingerprint(client.player.getInventory().getStack(slot));
			inventoryNow.put(slot, value);
			String before = lastInventorySlots.get(slot);
			if (force || before == null || !before.equals(value)) {
				append("INVENTORY_SLOT", "slot=" + slot + " | before=" + displayFingerprint(before)
						+ " | after=" + value);
			}
		}
		lastInventorySlots.clear();
		lastInventorySlots.putAll(inventoryNow);

		Map<Integer, String> handlerNow = new HashMap<>();
		for (int index = 0; index < handler.slots.size(); index++) {
			var slot = handler.slots.get(index);
			String value = itemFingerprint(slot.getStack());
			handlerNow.put(index, value);
			String before = lastHandlerSlots.get(index);
			if (force || before == null || !before.equals(value)) {
				append("GUI_SLOT", "handlerSlot=" + index + " | inventoryIndex=" + slot.getIndex()
						+ " | inventoryClass=" + slot.inventory.getClass().getName()
						+ " | before=" + displayFingerprint(before) + " | after=" + value);
			}
		}
		lastHandlerSlots.clear();
		lastHandlerSlots.putAll(handlerNow);
	}

	private static String displayFingerprint(String value) {
		return value == null ? "<unseen>" : value;
	}

	private static void captureCookwareProbe(MinecraftClient client, boolean force) {
		long now = System.currentTimeMillis();
		if (!force && now - lastCookwareCaptureMillis < COOKWARE_SCAN_INTERVAL_MILLIS) return;
		lastCookwareCaptureMillis = now;
		if (client.world == null || client.player == null) return;

		BlockPos target = cookwareTarget;
		if (client.crosshairTarget instanceof BlockHitResult hit) {
			target = hit.getBlockPos();
		}
		if (target == null || (!force && cookwareTarget != null
				&& now - cookwareTargetAtMillis > COOKWARE_TARGET_RETENTION_MILLIS
				&& !(client.crosshairTarget instanceof BlockHitResult))) return;

		Box area = new Box(target).expand(1.25);
		Set<Integer> seen = new HashSet<>();
		for (DisplayEntity.ItemDisplayEntity display :
				client.world.getEntitiesByClass(DisplayEntity.ItemDisplayEntity.class, area, entity -> !entity.isRemoved())) {
			seen.add(display.getId());
			ItemStack shown = display.getItemStack();
			String state = "target=" + blockPos(target)
					+ " | entity=" + describeEntity(client.player, display)
					+ " | displayed=" + itemDetails(shown);
			String previous = cookwareDisplays.put(display.getId(), state);
			if (force || !state.equals(previous)) {
				append(previous == null ? "COOKWARE_DISPLAY_SEEN" : "COOKWARE_DISPLAY_CHANGED", state);
			}
		}
		for (Integer id : new HashSet<>(cookwareDisplays.keySet())) {
			if (!seen.contains(id)) {
				append("COOKWARE_DISPLAY_GONE", "id=" + id + " | last=" + cookwareDisplays.remove(id));
			}
		}
		if (force) {
			append("COOKWARE_SCAN", "target=" + blockPos(target)
					+ " | block=" + Registries.BLOCK.getId(client.world.getBlockState(target).getBlock())
					+ " | itemDisplays=" + seen.size());
		}
	}

	private static String blockPos(BlockPos pos) {
		return pos.getX() + "," + pos.getY() + "," + pos.getZ();
	}

	public static void recordExperiencePacket(Object packet) {
		if (recording) append("MANA_PACKET", String.valueOf(packet));
	}

	public static void recordManaSeparated(int level, float progress, double mana, double maximum) {
		if (recording) append("MANA_SEPARATED", "packetLevel=" + level + " | progress="
				+ String.format(java.util.Locale.ROOT, "%.4f", progress) + " | mana="
				+ String.format(java.util.Locale.ROOT, "%.2f", mana) + "/"
				+ String.format(java.util.Locale.ROOT, "%.2f", maximum));
	}

	private static void captureMana(MinecraftClient client, boolean force) {
		if (client.player == null) return;
		int level = client.player.experienceLevel;
		int total = client.player.totalExperience;
		float progress = client.player.experienceProgress;
		if (!force && level == lastManaLevel && total == lastTotalExperience
				&& Float.compare(progress, lastExperienceProgress) == 0) return;
		String delta = lastManaLevel == Integer.MIN_VALUE ? "initial" : signed(level - lastManaLevel);
		ItemStack main = client.player.getMainHandStack();
		append("MANA_LEVEL", "value=" + level
				+ " | delta=" + delta
				+ " | totalExperience=" + total
				+ " | barProgress=" + String.format(java.util.Locale.ROOT, "%.4f", progress)
				+ " | heldItem=" + Registries.ITEM.getId(main.getItem())
				+ " | heldName=\"" + clean(main.getName().getString()) + "\""
				+ " | isArcaneCodex=" + isLikelyArcaneCodex(main));
		lastManaLevel = level;
		lastTotalExperience = total;
		lastExperienceProgress = progress;
	}

	private static String signed(int value) {
		return value > 0 ? "+" + value : Integer.toString(value);
	}

	private static boolean isLikelyArcaneCodex(ItemStack stack) {
		if (stack.isEmpty()) return false;
		String components = stack.getComponents().toString();
		return components.contains("sim_magic:codex_item")
				|| components.contains("smccore:arcane_codex")
				|| components.contains("\"smc:id\":\"arcane_codex\"")
				|| components.contains("注能杖，用来承载奥术");
	}

	private static void captureHeldItems(MinecraftClient client, boolean force) {
		if (client.player == null) return;
		int selectedSlot = client.player.getInventory().getSelectedSlot();
		ItemStack main = client.player.getMainHandStack();
		ItemStack off = client.player.getOffHandStack();
		String mainFingerprint = itemFingerprint(main);
		String offFingerprint = itemFingerprint(off);
		boolean slotChanged = selectedSlot != lastSelectedSlot;
		if (force || slotChanged || !mainFingerprint.equals(lastMainHandFingerprint)) {
			append("HELD_ITEM", "hand=MAIN | reason=" + heldChangeReason(force, slotChanged,
					lastMainHandFingerprint, mainFingerprint) + " | selectedSlot=" + selectedSlot
					+ " | " + itemDetails(main));
		}
		if (force || !offFingerprint.equals(lastOffHandFingerprint)) {
			append("HELD_ITEM", "hand=OFF | reason=" + heldChangeReason(force, false,
					lastOffHandFingerprint, offFingerprint) + " | " + itemDetails(off));
		}
		lastSelectedSlot = selectedSlot;
		lastMainHandFingerprint = mainFingerprint;
		lastOffHandFingerprint = offFingerprint;
	}

	private static String heldChangeReason(boolean force, boolean slotChanged, String previous, String current) {
		if (force || previous.isEmpty()) return "INITIAL";
		if (slotChanged) return "SLOT_SWITCH";
		return current.equals(previous) ? "UNCHANGED" : "STACK_UPDATE";
	}

	private static String itemFingerprint(ItemStack stack) {
		if (stack.isEmpty()) return "empty";
		return Registries.ITEM.getId(stack.getItem()) + "|" + clean(stack.getName().getString())
				+ "|" + stack.getCount() + "|" + clean(stack.getComponents().toString());
	}

	private static String itemDetails(ItemStack stack) {
		if (stack.isEmpty()) return "empty";
		return "id=" + Registries.ITEM.getId(stack.getItem())
				+ " | count=" + stack.getCount()
				+ " | name=\"" + clean(stack.getName().getString()) + "\""
				+ " | components=" + clean(stack.getComponents().toString());
	}

	private static void captureAttackInput(MinecraftClient client) {
		boolean pressed = client.options.attackKey.isPressed();
		if (pressed && !lastAttackPressed && client.player != null) {
			String target = "none";
			if (client.crosshairTarget instanceof EntityHitResult hit) {
				target = describeEntity(client.player, hit.getEntity());
			}
			append("ATTACK_INPUT", "weapon=" + stack(client.player.getMainHandStack())
					+ " | attackCooldown=" + String.format(java.util.Locale.ROOT, "%.4f", client.player.getAttackCooldownProgress(0.0F))
					+ " | sprinting=" + client.player.isSprinting()
					+ " | onGround=" + client.player.isOnGround()
					+ " | target=" + target);
		}
		lastAttackPressed = pressed;
	}

	private static void captureCombatEntities(MinecraftClient client, boolean force) {
		long now = System.currentTimeMillis();
		if (!force && now - lastCombatCaptureMillis < ENTITY_SCAN_INTERVAL_MILLIS) return;
		lastCombatCaptureMillis = now;
		if (client.world == null || client.player == null) return;
		Set<Integer> seen = new HashSet<>();
		for (Entity entity : client.world.getOtherEntities(client.player, client.player.getBoundingBox().expand(96.0))) {
			if (!(entity instanceof LivingEntity) && !(entity instanceof DisplayEntity.TextDisplayEntity)) continue;
			seen.add(entity.getId());
			CombatEntityState current = CombatEntityState.of(entity);
			CombatEntityState previous = combatEntities.put(entity.getId(), current);
			if (previous == null) {
				if (force || entity instanceof LivingEntity) append("ENTITY_SEEN", describeEntity(client.player, entity) + current.details());
			} else if (current.combatChangedFrom(previous)) {
				String delta = "";
				if (current.health != null && previous.health != null) {
					delta = " | healthDelta=" + decimal(current.health - previous.health);
				}
				append("ENTITY_CHANGE", describeEntity(client.player, entity) + delta
						+ " | before=" + previous.details() + " | after=" + current.details());
			}
		}
		for (Integer id : new HashSet<>(combatEntities.keySet())) {
			if (!seen.contains(id)) {
				CombatEntityState previous = combatEntities.remove(id);
				Entity entity = client.world.getEntityById(id);
				if (entity == null || entity.isRemoved()) {
					append("ENTITY_REMOVED", "id=" + id + " | last=" + previous.details());
				}
			}
		}
	}

	private static String describeEntity(Entity observer, Entity entity) {
		return "id=" + entity.getId()
				+ " uuid=" + entity.getUuidAsString()
				+ " type=" + Registries.ENTITY_TYPE.getId(entity.getType())
				+ " name=\"" + clean(entity.getName().getString()) + "\""
				+ " distance=" + decimal(observer.distanceTo(entity))
				+ " pos=" + decimal(entity.getX()) + "," + decimal(entity.getY()) + "," + decimal(entity.getZ());
	}

	private static String decimal(double value) {
		return String.format(java.util.Locale.ROOT, "%.3f", value);
	}

	private static void captureState(MinecraftClient client, boolean force) {
		long nowMillis = System.currentTimeMillis();
		if (!force && nowMillis - lastStateCaptureMillis < 500L) return;
		lastStateCaptureMillis = nowMillis;
		if (client.player == null) {
			if (force || !"NO_PLAYER".equals(lastState)) {
				lastState = "NO_PLAYER";
				append("STATE", lastState);
			}
			return;
		}
		ItemStack main = client.player.getMainHandStack();
		ItemStack off = client.player.getOffHandStack();
		String screen = client.currentScreen == null ? "none" : client.currentScreen.getClass().getSimpleName();
		boolean mainCooling = client.player.getItemCooldownManager().isCoolingDown(main);
		boolean offCooling = client.player.getItemCooldownManager().isCoolingDown(off);
		String state = "main=" + stack(main)
				+ " | mainCooldown=" + mainCooling + ":" + cooldown(client, main)
				+ " | off=" + stack(off)
				+ " | offCooldown=" + offCooling + ":" + cooldown(client, off)
				+ " | selectedSlot=" + client.player.getInventory().getSelectedSlot()
				+ " | xpLevel=" + client.player.experienceLevel
				+ " | xpProgress=" + String.format(java.util.Locale.ROOT, "%.4f", client.player.experienceProgress)
				+ " | effects=" + client.player.getStatusEffects()
				+ " | screen=" + screen;
		if (force || !state.equals(lastState)) {
			lastState = state;
			append("STATE", state);
		}
	}

	private static String cooldown(MinecraftClient client, ItemStack stack) {
		if (stack.isEmpty()) {
			return "0.0000";
		}
		return String.format(java.util.Locale.ROOT, "%.4f",
				client.player.getItemCooldownManager().getCooldownProgress(stack, 0.0F));
	}

	private static String stack(ItemStack stack) {
		if (stack.isEmpty()) {
			return "empty";
		}
		return stack.getItem().toString() + " x" + stack.getCount()
				+ " name=\"" + clean(stack.getName().getString()) + "\" components=" + clean(stack.getComponents().toString());
	}

	private static void append(String type, String value) {
		Instant now = Instant.now();
		long elapsed = startedAt == null ? 0 : Duration.between(startedAt, now).toMillis();
		String line = "[" + LINE_TIME.format(now) + "][+" + elapsed + "ms][" + type + "] "
				+ limit(clean(value), MAX_VALUE_CHARACTERS);
		if (recordedCharacters + line.length() > MAX_LOG_CHARACTERS && !"DEBUG".equals(type)) {
			if (!logTruncated) {
				logTruncated = true;
				String marker = "[" + LINE_TIME.format(now) + "][+" + elapsed
						+ "ms][LIMIT] Log size limit reached; further detail was omitted.";
				lines.add(marker);
				recordedCharacters += marker.length() + 1;
			}
			return;
		}
		lines.add(line);
		recordedCharacters += line.length() + 1;
	}

	private static String clean(String value) {
		return value == null ? "" : value.replace('\r', ' ').replace('\n', ' ');
	}

	private static String limit(String value, int maximum) {
		if (value.length() <= maximum) return value;
		return value.substring(0, maximum) + "...<truncated " + (value.length() - maximum) + " chars>";
	}

	private static int packetLimit(String type) {
		return switch (type) {
			case "PACKET_ENTITY_METADATA", "PACKET_ENTITY_VELOCITY" -> 40;
			case "PACKET_BLOCK_UPDATE", "PACKET_INVENTORY", "PACKET_SLOT_UPDATE",
					"PACKET_SCREEN_PROPERTY" -> 60;
			case "PACKET_PARTICLE", "PACKET_SOUND", "PACKET_ENTITY_SOUND" -> 20;
			case "PACKET_CUSTOM_PAYLOAD" -> 40;
			default -> 100;
		};
	}

	private static Path writeLog() {
		Path directory = FabricLoader.getInstance().getConfigDir().resolve("simes").resolve("debug");
		Path file = directory.resolve("debug-" + FILE_TIME.format(Instant.now()) + ".log");
		try {
			Files.createDirectories(directory);
			Files.write(file, lines, StandardCharsets.UTF_8);
			return file;
		} catch (IOException error) {
			SimesClient.LOGGER.error("Failed to write Simes debug trace", error);
			return null;
		}
	}

	private static void localMessage(MinecraftClient client, String message) {
		if (client.player != null) {
			client.player.sendMessage(Text.literal(message), false);
		}
	}

	private record CombatEntityState(Float health, Float absorption, boolean dead, int hurtTime, String text) {
		private static CombatEntityState of(Entity entity) {
			if (entity instanceof LivingEntity living) {
				return new CombatEntityState(living.getHealth(), living.getAbsorptionAmount(), living.isDead(), living.hurtTime, "");
			}
			if (entity instanceof DisplayEntity.TextDisplayEntity display) {
				return new CombatEntityState(null, null, false, 0, clean(display.getText().getString()));
			}
			return new CombatEntityState(null, null, entity.isRemoved(), 0, "");
		}

		private String details() {
			if (health != null) return "health=" + decimal(health) + " absorption=" + decimal(absorption)
					+ " dead=" + dead + " hurtTime=" + hurtTime;
			return "text=\"" + text + "\" dead=" + dead;
		}

		private boolean combatChangedFrom(CombatEntityState previous) {
			return !java.util.Objects.equals(health, previous.health)
					|| !java.util.Objects.equals(absorption, previous.absorption)
					|| dead != previous.dead
					|| !java.util.Objects.equals(text, previous.text)
					|| (hurtTime > 0 && previous.hurtTime == 0);
		}
	}

	private static final class PacketRateWindow {
		private long startedMillis;
		private int accepted;

		private PacketRateWindow(long startedMillis) {
			this.startedMillis = startedMillis;
		}

		private boolean tryAcquire(long now, int limit) {
			if (now - startedMillis >= 1_000L) {
				startedMillis = now;
				accepted = 0;
			}
			if (accepted >= limit) return false;
			accepted++;
			return true;
		}
	}
}

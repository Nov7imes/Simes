package cn.simmc.simpricedisplay;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** SimMC fermentation and ordinary cookware client assistant. */
public final class CookwareAssistant {
	private static final long SCAN_MS = 250L;
	private static final long PENDING_MS = 1_500L;
	private static final long WITHDRAW_SETTLE_MS = 150L;
	private static final long DEPOSIT_CONFIRM_MS = 2_500L;
	private static final long COOK_ESTIMATE_MS = 60_000L;
	private static final int MAX_ITEMS_PER_TYPE = 10;
	private static final int MAX_VISIBLE_ITEMS = 5;
	private static final Pattern INGREDIENT = Pattern.compile("^[-－]\\s*\\[([^]]+)]\\s*[xX×]\\s*(\\d+).*$");
	private static final Pattern REMAINING = Pattern.compile(".*剩余时间[：:]\\s*(.+?)(?:[。.]|正在腌制|$).*");
	private static final Pattern PRODUCT = Pattern.compile(".*正在腌制[：:]?\\s*\\[([^]]+)].*");
	private static final Map<BlockPos, Fermenter> fermenters = new HashMap<>();
	private static final Map<BlockPos, Cooker> cookers = new HashMap<>();
	private static final Map<String, Integer> clockIngredients = new LinkedHashMap<>();
	private static final List<DepositIntent> depositIntents = new ArrayList<>();
	private static Map<String, InventoryEntry> depositBaseline = Map.of();
	private static final Map<String, Integer> depositAccounted = new HashMap<>();
	private static PendingWithdrawal pendingWithdrawal;
	private static BlockPos clockTarget;
	private static BlockPos lastFermentationTarget;
	private static long lastFermentationInteractionAt;
	private static long lastScan;
	private static boolean collectingIngredients;
	private static List<ProjectedPanel> projectedPanels = List.of();

	private CookwareAssistant() {}

	public static void register() {
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (player != MinecraftClient.getInstance().player || hand != Hand.MAIN_HAND) return ActionResult.PASS;
			ItemStack held = player.getStackInHand(hand);
			BlockPos pos = hit.getBlockPos().toImmutable();
			boolean fermentationBarrel = isFermentationBarrelAt(pos);
			if (fermentationBarrel) {
				lastFermentationTarget = pos;
				lastFermentationInteractionAt = System.currentTimeMillis();
			}
			DebugRecorder.recordCookware("USE_DECISION", "pos=" + pos.toShortString()
					+ " | held=" + details(held)
					+ " | heldCount=" + held.getCount()
					+ " | barrelDetected=" + fermentationBarrel
					+ " | locallyTracked=" + fermenters.containsKey(pos)
					+ " | localState=" + fermenterSummary(pos));
			if (isClock(held)) {
				clockTarget = fermentationBarrel ? pos : null;
				collectingIngredients = false;
				clockIngredients.clear();
			} else if (held.isEmpty() && fermenters.containsKey(pos)) {
				// A withdrawal makes the inventory count rise. If the preceding deposit
				// reconciliation is still alive, it interprets that rise as a rejected
				// deposit and removes the same local item a second time.
				finishDepositTracking("withdraw-start", pos);
				long now = System.currentTimeMillis();
				if (pendingWithdrawal != null && pendingWithdrawal.pos.equals(pos)
						&& now - pendingWithdrawal.lastInteractionAt <= PENDING_MS) {
					pendingWithdrawal.lastInteractionAt = now;
					pendingWithdrawal.interactions++;
					DebugRecorder.recordCookware("WITHDRAW_REFRESH", "pos=" + pos.toShortString()
							+ " | interactions=" + pendingWithdrawal.interactions
							+ " | baselineRetained=" + snapshotSummary(pendingWithdrawal.inventory)
							+ " | accounted=" + pendingWithdrawal.accounted);
				} else {
					pendingWithdrawal = new PendingWithdrawal(pos, inventorySnapshot(player), now);
					DebugRecorder.recordCookware("WITHDRAW_START", "pos=" + pos.toShortString()
							+ " | baseline=" + snapshotSummary(pendingWithdrawal.inventory)
							+ " | localState=" + fermenterSummary(pos));
				}
			} else if (SimesConfig.get().fermentationAssistantEnabled
					&& fermentationBarrel && !held.isEmpty()) {
				// Starting a deposit makes the inventory count fall. An older withdrawal
				// session would interpret that fall as a withdrawal rollback and restore
				// the same local item while the deposit tracker adds it.
				finishWithdrawalTracking("deposit-start", pos);
				if (depositIntents.isEmpty()) {
					depositBaseline = inventorySnapshot(player);
					depositAccounted.clear();
					DebugRecorder.recordCookware("DEPOSIT_BASELINE", "pos=" + pos.toShortString()
							+ " | inventory=" + snapshotSummary(depositBaseline));
				}
				String itemKey = details(held);
				DepositIntent existing = null;
				for (DepositIntent intent : depositIntents) {
					if (intent.pos.equals(pos) && intent.itemKey.equals(itemKey)) {
						existing = intent;
						break;
					}
				}
				if (existing == null) {
					Fermenter state = fermenters.computeIfAbsent(pos, Fermenter::new);
					depositIntents.add(new DepositIntent(pos, itemKey, held.copyWithCount(1),
							state.count(held), System.currentTimeMillis()));
					DebugRecorder.recordCookware("DEPOSIT_INTENT", "pos=" + pos.toShortString()
							+ " | item=" + itemKey + " | heldCount=" + held.getCount()
							+ " | initialLocalCount=" + state.count(held));
				} else {
					existing.at = System.currentTimeMillis();
					DebugRecorder.recordCookware("DEPOSIT_INTENT_REFRESH", "pos=" + pos.toShortString()
							+ " | item=" + itemKey + " | heldCount=" + held.getCount());
				}
			}
			return ActionResult.PASS;
		});
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (!overlay) readClockMessage(message.getString());
		});
		ClientTickEvents.END_CLIENT_TICK.register(CookwareAssistant::tick);
		WorldRenderEvents.AFTER_ENTITIES.register(CookwareAssistant::projectPanels);
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS,
				Identifier.of(SimesClient.MOD_ID, "cookware_assistant"), CookwareAssistant::renderHud);
	}

	public static void reset() {
		fermenters.clear();
		cookers.clear();
		clockIngredients.clear();
		depositIntents.clear();
		depositBaseline = Map.of();
		depositAccounted.clear();
		pendingWithdrawal = null;
		clockTarget = null;
		lastFermentationTarget = null;
		lastFermentationInteractionAt = 0L;
		lastScan = 0L;
		collectingIngredients = false;
		projectedPanels = List.of();
	}

	private static void tick(MinecraftClient client) {
		if (client.player == null || client.world == null) return;
		long now = System.currentTimeMillis();
		confirmDeposit(client, now);
		confirmWithdrawal(client, now);
		if (now - lastScan < SCAN_MS) return;
		lastScan = now;
		discoverTargetedFermenter(client);
		scanCookers(client, now);
	}

	private static void discoverTargetedFermenter(MinecraftClient client) {
		if (!SimesConfig.get().fermentationAssistantEnabled
				|| !(client.crosshairTarget instanceof BlockHitResult target)) return;
		BlockPos pos = target.getBlockPos().toImmutable();
		if (fermenters.containsKey(pos) || !isFermentationBarrelAt(pos)) return;
		fermenters.put(pos, new Fermenter(pos));
		DebugRecorder.recordCookware("FERMENTER_DISCOVERED",
				"pos=" + pos.toShortString() + " | source=crosshair | state=unknown");
	}

	private static void confirmDeposit(MinecraftClient client, long now) {
		if (depositIntents.isEmpty()) return;
		Map<String, InventoryEntry> current = inventorySnapshot(client.player);
		for (Map.Entry<String, InventoryEntry> original : depositBaseline.entrySet()) {
			int currentCount = current.containsKey(original.getKey()) ? current.get(original.getKey()).count : 0;
			int totalDecrease = Math.max(0, original.getValue().count - currentCount);
			DepositIntent intent = null;
			for (int i = depositIntents.size() - 1; i >= 0; i--) {
				DepositIntent candidate = depositIntents.get(i);
				if (candidate.itemKey.equals(original.getKey()) && now - candidate.at <= DEPOSIT_CONFIRM_MS) {
					intent = candidate;
					break;
				}
			}
			if (intent != null) {
				String sessionKey = intent.pos.asLong() + "|" + intent.itemKey;
				int applied = depositAccounted.getOrDefault(sessionKey, 0);
				int desired = Math.min(totalDecrease, Math.max(0, MAX_ITEMS_PER_TYPE - intent.initialCount));
				int correction = desired - applied;
				Fermenter state = fermenters.computeIfAbsent(intent.pos, Fermenter::new);
				if (correction > 0) state.add(intent.item, correction);
				else if (correction < 0) state.remove(intent.item, -correction);
				depositAccounted.put(sessionKey, desired);
				if (correction != 0) {
					DebugRecorder.recordCookware("DEPOSIT_APPLY", "pos=" + intent.pos.toShortString()
							+ " | item=" + intent.itemKey + " | baseline=" + original.getValue().count
							+ " | current=" + currentCount + " | totalDecrease=" + totalDecrease
							+ " | initialLocal=" + intent.initialCount + " | desired=" + desired
							+ " | previouslyApplied=" + applied + " | correction=" + correction
							+ " | localAfter=" + fermenterSummary(intent.pos));
				}
			}
		}
		depositIntents.removeIf(intent -> now - intent.at > DEPOSIT_CONFIRM_MS);
		if (depositIntents.isEmpty()) {
			DebugRecorder.recordCookware("DEPOSIT_END", "currentInventory=" + snapshotSummary(current));
			depositBaseline = Map.of();
			depositAccounted.clear();
		}
	}

	private static void finishWithdrawalTracking(String reason, BlockPos pos) {
		if (pendingWithdrawal == null) return;
		DebugRecorder.recordCookware("WITHDRAW_END", "reason=" + reason
				+ " | pos=" + pendingWithdrawal.pos.toShortString()
				+ " | nextPos=" + pos.toShortString()
				+ " | interactions=" + pendingWithdrawal.interactions
				+ " | accounted=" + pendingWithdrawal.accounted
				+ " | localState=" + fermenterSummary(pendingWithdrawal.pos));
		pendingWithdrawal = null;
	}

	private static void finishDepositTracking(String reason, BlockPos pos) {
		if (!depositIntents.isEmpty()) {
			DebugRecorder.recordCookware("DEPOSIT_END", "reason=" + reason
					+ " | pos=" + pos.toShortString()
					+ " | activeIntents=" + depositIntents.size()
					+ " | accounted=" + depositAccounted
					+ " | localState=" + fermenterSummary(pos));
		}
		depositIntents.clear();
		depositBaseline = Map.of();
		depositAccounted.clear();
	}

	private static void confirmWithdrawal(MinecraftClient client, long now) {
		if (pendingWithdrawal == null) return;
		if (now - pendingWithdrawal.lastInteractionAt > PENDING_MS) {
			DebugRecorder.recordCookware("WITHDRAW_END", "reason=timeout | pos="
					+ pendingWithdrawal.pos.toShortString() + " | accounted=" + pendingWithdrawal.accounted
					+ " | interactions=" + pendingWithdrawal.interactions
					+ " | localState=" + fermenterSummary(pendingWithdrawal.pos));
			pendingWithdrawal = null;
			return;
		}
		if (now - pendingWithdrawal.startedAt < WITHDRAW_SETTLE_MS) return;
		Map<String, InventoryEntry> current = inventorySnapshot(client.player);
		Fermenter state = fermenters.get(pendingWithdrawal.pos);
		if (state == null) {
			pendingWithdrawal = null;
			return;
		}
		Map<String, InventoryEntry> candidates = new HashMap<>(pendingWithdrawal.inventory);
		candidates.putAll(current);
		for (Map.Entry<String, InventoryEntry> entry : candidates.entrySet()) {
			InventoryEntry previous = pendingWithdrawal.inventory.get(entry.getKey());
			InventoryEntry latest = current.get(entry.getKey());
			int before = previous == null ? 0 : previous.count;
			int after = latest == null ? 0 : latest.count;
			int desired = Math.max(0, after - before);
			int applied = pendingWithdrawal.accounted.getOrDefault(entry.getKey(), 0);
			int correction = desired - applied;
			ItemStack stack = latest != null ? latest.stack : entry.getValue().stack;
			if (correction > 0) {
				int removed = state.remove(stack, correction);
				DebugRecorder.recordCookware("WITHDRAW_APPLY", "pos=" + pendingWithdrawal.pos.toShortString()
						+ " | item=" + entry.getKey() + " | baseline=" + before + " | current=" + after
						+ " | desired=" + desired + " | previouslyApplied=" + applied
						+ " | correction=" + correction + " | actuallyRemoved=" + removed
						+ " | localAfter=" + fermenterSummary(pendingWithdrawal.pos));
				if (removed > 0) {
					pendingWithdrawal.accounted.put(entry.getKey(), applied + removed);
					pendingWithdrawal.items.put(entry.getKey(), stack.copyWithCount(1));
				}
			} else if (correction < 0 && applied > 0) {
				int restored = Math.min(-correction, applied);
				ItemStack recorded = pendingWithdrawal.items.getOrDefault(entry.getKey(), stack);
				state.add(recorded, restored);
				DebugRecorder.recordCookware("WITHDRAW_ROLLBACK", "pos=" + pendingWithdrawal.pos.toShortString()
						+ " | item=" + entry.getKey() + " | baseline=" + before + " | current=" + after
						+ " | desired=" + desired + " | previouslyApplied=" + applied
						+ " | restored=" + restored + " | localAfter=" + fermenterSummary(pendingWithdrawal.pos));
				int remaining = applied - restored;
				if (remaining == 0) {
					pendingWithdrawal.accounted.remove(entry.getKey());
					pendingWithdrawal.items.remove(entry.getKey());
				} else {
					pendingWithdrawal.accounted.put(entry.getKey(), remaining);
				}
			}
		}
	}

	private static Map<String, InventoryEntry> inventorySnapshot(
			net.minecraft.entity.player.PlayerEntity player) {
		Map<String, InventoryEntry> result = new HashMap<>();
		for (int slot = 0; slot < player.getInventory().size(); slot++) {
			ItemStack stack = player.getInventory().getStack(slot);
			if (stack.isEmpty()) continue;
			String key = details(stack);
			InventoryEntry previous = result.get(key);
			result.put(key, new InventoryEntry(stack.copyWithCount(1),
					stack.getCount() + (previous == null ? 0 : previous.count)));
		}
		return result;
	}

	private static String snapshotSummary(Map<String, InventoryEntry> snapshot) {
		StringBuilder value = new StringBuilder("{");
		snapshot.forEach((key, entry) -> {
			if (value.length() > 1) value.append(", ");
			value.append(key).append("=").append(entry.count);
		});
		return value.append('}').toString();
	}

	private static String fermenterSummary(BlockPos pos) {
		Fermenter state = fermenters.get(pos);
		if (state == null) return "<none>";
		StringBuilder value = new StringBuilder("{");
		state.items.forEach((key, entry) -> {
			if (value.length() > 1) value.append(", ");
			value.append(key).append("=").append(entry.count);
		});
		return value.append('}').toString();
	}

	private static void scanCookers(MinecraftClient client, long now) {
		if (!SimesConfig.get().cookwareAssistantEnabled) return;
		Map<BlockPos, List<DisplayEntity.ItemDisplayEntity>> groups = new HashMap<>();
		Box area = client.player.getBoundingBox().expand(20);
		for (DisplayEntity.ItemDisplayEntity display :
				client.world.getEntitiesByClass(DisplayEntity.ItemDisplayEntity.class, area, entity -> !entity.isRemoved())) {
			if (!display.getItemStack().isEmpty()) {
				BlockPos pos = BlockPos.ofFloored(display.getX(), display.getY(), display.getZ());
				groups.computeIfAbsent(pos, ignored -> new ArrayList<>()).add(display);
			}
		}
		for (Map.Entry<BlockPos, List<DisplayEntity.ItemDisplayEntity>> group : groups.entrySet()) {
			if (group.getValue().stream().anyMatch(display ->
					isFermentationBarrel(display.getItemStack()))) continue;
			if (group.getValue().stream().noneMatch(display ->
					isCookingVessel(display.getItemStack()))) continue;
			ItemStack vessel = group.getValue().stream().map(DisplayEntity.ItemDisplayEntity::getItemStack)
					.filter(CookwareAssistant::isCookingVessel).findFirst()
					.orElse(ItemStack.EMPTY);
			String cookwareName = normalizedCookwareName(vessel);
			boolean open = isOpenCookingVessel(vessel);
			List<ItemStack> contents = group.getValue().stream()
					.map(DisplayEntity.ItemDisplayEntity::getItemStack)
					.filter(stack -> !isCookware(stack))
					.map(stack -> stack.copyWithCount(1)).toList();
			cookers.computeIfAbsent(group.getKey(), Cooker::new).observe(cookwareName, open, contents, now);
		}
		cookers.entrySet().removeIf(entry -> now - entry.getValue().lastSeen > 3_000L);
	}

	private static void readClockMessage(String raw) {
		if (raw == null) return;
		String line = raw.strip();
		if (line.contains("腌制已中断")) {
			Fermenter state = clockMessageFermenter();
			if (state != null) {
				applyClockIngredients(state, "interrupted");
				state.invalidateServerTime("已中断");
				DebugRecorder.recordCookware("FERMENTATION_INTERRUPTED",
						"pos=" + state.pos.toShortString() + " | server time invalidated");
			}
			return;
		}
		if (line.contains("腌制已开始")) {
			Fermenter state = clockMessageFermenter();
			if (state != null) {
				applyClockIngredients(state, "started");
				state.invalidateServerTime("发酵中 · 待校准");
				DebugRecorder.recordCookware("FERMENTATION_RESTARTED",
						"pos=" + state.pos.toShortString() + " | clock calibration required");
			}
			return;
		}
		// Some server replies arrive after the display entity has briefly been
		// replaced (open/closed animation). Keep the recent interaction as a
		// fallback instead of dropping the authoritative reply.
		if (clockTarget == null) {
			Fermenter recent = recentFermenter();
			if (recent == null) return;
			clockTarget = recent.pos;
		}
		if (line.contains("当前桶内的材料")) {
			collectingIngredients = true;
			clockIngredients.clear();
			return;
		}
		Matcher item = INGREDIENT.matcher(line);
		if (collectingIngredients && item.matches()) {
			clockIngredients.put(item.group(1), Integer.parseInt(item.group(2)));
			return;
		}
		Fermenter state = fermenters.computeIfAbsent(clockTarget, Fermenter::new);
		boolean updated = false;
		Matcher remaining = REMAINING.matcher(line);
		if (remaining.matches()) {
			state.remaining = remaining.group(1).strip();
			Matcher product = PRODUCT.matcher(line);
			if (product.matches()) state.product = product.group(1);
			if (FermentationCountdown.isServerComplete(state.remaining)) {
				state.markServerComplete();
			} else {
				long parsedRemaining = state.calibrate(state.remaining);
				if (parsedRemaining == 0L) state.markServerComplete();
				else state.status = "发酵中";
			}
			updated = true;
		} else if (FermentationCountdown.isServerComplete(line)) {
			state.markServerComplete();
			updated = true;
		} else if (line.contains("腌制已开始")) {
			state.status = "发酵中";
			updated = true;
		}
		if (updated) {
			applyClockIngredients(state, "authoritative-time");
			state.serverUpdatedAt = System.currentTimeMillis();
			state.needsServerRefresh = false;
		}
	}

	private static Fermenter clockMessageFermenter() {
		if (collectingIngredients && clockTarget != null) {
			return fermenters.computeIfAbsent(clockTarget, Fermenter::new);
		}
		return recentFermenter();
	}

	private static void applyClockIngredients(Fermenter state, String reason) {
		if (state != null && collectingIngredients && !clockIngredients.isEmpty()) {
			state.replace(clockIngredients);
			DebugRecorder.recordCookware("CLOCK_INGREDIENTS_APPLY",
					"pos=" + state.pos.toShortString() + " | reason=" + reason
							+ " | ingredients=" + clockIngredients);
		}
		collectingIngredients = false;
		clockIngredients.clear();
	}

	private static Fermenter recentFermenter() {
		long now = System.currentTimeMillis();
		if (lastFermentationTarget == null || now - lastFermentationInteractionAt > 5_000L) return null;
		return fermenters.computeIfAbsent(lastFermentationTarget, Fermenter::new);
	}

	private static void renderHud(DrawContext context, net.minecraft.client.render.RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.world == null) return;
		for (ProjectedPanel projected : projectedPanels) {
			drawPanel(context, client.textRenderer, projected.panel, projected.x, projected.y);
		}
	}

	private static void projectPanels(net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.world == null) {
			projectedPanels = List.of();
			return;
		}
		if (!(client.crosshairTarget instanceof BlockHitResult target)) {
			projectedPanels = List.of();
			return;
		}
		BlockPos targetPos = target.getBlockPos();
		List<Panel> panels = new ArrayList<>();
		if (SimesConfig.get().fermentationAssistantEnabled) {
			fermenters.values().forEach(value -> panels.add(value.panel()));
		}
		if (SimesConfig.get().cookwareAssistantEnabled) {
			cookers.values().stream().filter(Cooker::hasContents).forEach(value -> panels.add(value.panel()));
		}
		var cameraPos = context.camera().getPos();
		int scaledWidth = client.getWindow().getScaledWidth();
		int scaledHeight = client.getWindow().getScaledHeight();
		List<ProjectedPanel> projected = new ArrayList<>(1);
		panels.stream()
				.filter(panel -> panel.pos.equals(targetPos))
				.filter(panel -> panel.pos.toCenterPos().squaredDistanceTo(client.player.getPos()) <= 10 * 10)
				.limit(1)
				.forEach(panel -> {
					Vector4f point = new Vector4f(
							(float)(panel.pos.getX() + .5 - cameraPos.x),
							(float)(panel.pos.getY() + 1.35 - cameraPos.y),
							(float)(panel.pos.getZ() + .5 - cameraPos.z), 1.0F);
					context.positionMatrix().transform(point);
					context.projectionMatrix().transform(point);
					if (point.w <= 0.01F) return;
					float ndcX = point.x / point.w;
					float ndcY = point.y / point.w;
					if (ndcX < -1.15F || ndcX > 1.15F || ndcY < -1.15F || ndcY > 1.15F) return;
					int screenX = Math.round((ndcX + 1.0F) * .5F * scaledWidth);
					int screenY = Math.round((1.0F - ndcY) * .5F * scaledHeight);
					projected.add(new ProjectedPanel(panel, screenX, screenY));
				});
		projectedPanels = List.copyOf(projected);
	}

	private static void drawPanel(DrawContext context, TextRenderer text, Panel panel, int centerX, int anchorY) {
		int contentWidth = panel.lines.stream().mapToInt(line ->
				text.getWidth(line.text) + (line.stack.isEmpty() ? 0 : 20)).max().orElse(100);
		int width = Math.max(96, Math.max(contentWidth, text.getWidth(panel.title)) + 10);
		int height = 23 + panel.lines.size() * 18;
		int x = centerX - width / 2;
		int y = anchorY - 8 - height;

		if (x < 2) x = 2;
		if (x + width > context.getScaledWindowWidth() - 2) {
			x = context.getScaledWindowWidth() - width - 2;
		}
		if (y < 2) y = 2;

		context.fill(x - 2, y - 2, x + width + 2, y + height + 2, 0xB0000000);
		context.fill(x, y, x + width, y + height, 0x73000000);
		context.fill(x, y, x + width, y + 1, panel.color);
		context.drawCenteredTextWithShadow(text, panel.title, x + width / 2, y + 5, panel.color);

		int rowY = y + 20;
		for (Line line : panel.lines) {
			int textX = x + 7;
			if (!line.stack.isEmpty()) {
				context.drawItem(line.stack, x + 6, rowY - 3);
				textX += 20;
			}
			context.drawTextWithShadow(text, line.text, textX, rowY + 1, 0xFFFFFFFF);
			rowY += 18;
		}
	}

	private static boolean sameItem(ItemStack a, ItemStack b) {
		return !a.isEmpty() && !b.isEmpty() && ItemStack.areItemsAndComponentsEqual(a, b);
	}

	private static boolean isClock(ItemStack stack) {
		String value = details(stack);
		return value.contains("pocket_watch") || value.contains("烹饪钟") || value.contains("厨房钟");
	}

	private static boolean isCookware(ItemStack stack) {
		String value = details(stack);
		return value.contains("kitchenware") || value.contains("蒸锅") || value.contains("煮锅")
				|| value.contains("炖锅") || value.contains("煎锅") || value.contains("炒锅");
	}

	private static boolean isFermentationBarrelAt(BlockPos pos) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null) return false;
		/*
		 * SimMC renders this custom block with an item-display entity. Its
		 * centre is commonly half a block sideways and one block above the
		 * supporting block returned by the crosshair hit. A strict new
		 * Box(pos) therefore misses a perfectly visible barrel, especially
		 * after relogging when no local state has been learned yet.
		 */
		Box associationBox = new Box(pos).expand(0.55D, 1.1D, 0.55D);
		return !client.world.getEntitiesByClass(DisplayEntity.ItemDisplayEntity.class, associationBox,
				display -> isFermentationBarrel(display.getItemStack())).isEmpty();
	}

	private static boolean isFermentationBarrel(ItemStack stack) {
		return details(stack).contains("kitchenware_3/fermentation_barrel");
	}

	private static boolean isCookingVessel(ItemStack stack) {
		String value = details(stack);
		return value.contains("smc:kitchenware_2/cookware")
				|| value.contains("smc:kitchenware_2/steamer")
				|| value.contains("smc:kitchenware_2/skillet");
	}

	private static boolean isOpenCookingVessel(ItemStack stack) {
		String value = details(stack);
		return value.contains("smc:kitchenware_2/cookware_open")
				|| value.contains("smc:kitchenware_2/steamer_open")
				|| value.contains("smc:kitchenware_2/skillet_open");
	}

	private static String normalizedCookwareName(ItemStack stack) {
		String value = details(stack);
		if (value.contains("smc:kitchenware_2/cookware_open")) return "炖锅 无盖";
		if (value.contains("smc:kitchenware_2/cookware")) return "炖锅";
		return stack == null || stack.isEmpty() ? "厨具" : stack.getName().getString();
	}

	private static String details(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return "";
		return (Registries.ITEM.getId(stack.getItem()) + "|" + stack.getName().getString() + "|"
				+ stack.getComponents()).toLowerCase(Locale.ROOT);
	}

	private static List<Entry> aggregate(List<ItemStack> stacks) {
		Map<String, Entry> result = new LinkedHashMap<>();
		for (ItemStack stack : stacks) {
			String key = details(stack);
			Entry old = result.get(key);
			result.put(key, new Entry(stack, old == null ? 1 : old.count + 1));
		}
		return new ArrayList<>(result.values());
	}

	private static String elapsed(long at) {
		long seconds = Math.max(0, (System.currentTimeMillis() - at) / 1_000);
		return seconds < 60 ? seconds + " 秒前" : seconds / 60 + " 分钟前";
	}

	private static final class DepositIntent {
		private final BlockPos pos;
		private final String itemKey;
		private final ItemStack item;
		private final int initialCount;
		private long at;

		private DepositIntent(BlockPos pos, String itemKey, ItemStack item, int initialCount, long at) {
			this.pos = pos;
			this.itemKey = itemKey;
			this.item = item;
			this.initialCount = initialCount;
			this.at = at;
		}
	}
	private static final class PendingWithdrawal {
		private final BlockPos pos;
		private final Map<String, InventoryEntry> inventory;
		private final long startedAt;
		private long lastInteractionAt;
		private int interactions = 1;
		private final Map<String, Integer> accounted = new HashMap<>();
		private final Map<String, ItemStack> items = new HashMap<>();

		private PendingWithdrawal(BlockPos pos, Map<String, InventoryEntry> inventory, long at) {
			this.pos = pos;
			this.inventory = inventory;
			this.startedAt = at;
			this.lastInteractionAt = at;
		}
	}
	private record InventoryEntry(ItemStack stack, int count) {}
	private record Entry(ItemStack stack, int count) {}
	private record Line(ItemStack stack, String text) {}
	private record Panel(BlockPos pos, String title, int color, List<Line> lines) {}
	private record ProjectedPanel(Panel panel, int x, int y) {}

	private static final class Fermenter {
		private final BlockPos pos;
		private final Map<String, Entry> items = new LinkedHashMap<>();
		private String remaining = "请使用烹饪钟更新";
		private String product = "";
		private String status = "状态未知";
		private long serverUpdatedAt;
		private boolean needsServerRefresh;
		private long calibratedRemainingMillis = -1L;
		private long calibratedAtNanos;
		private boolean serverConfirmedComplete;

		private Fermenter(BlockPos pos) { this.pos = pos; }

		private void add(ItemStack stack, int count) {
			String key = details(stack);
			Entry old = items.get(key);
			int previous = old == null ? 0 : old.count;
			int accepted = Math.min(MAX_ITEMS_PER_TYPE, previous + Math.max(0, count));
			if (accepted > previous) {
				items.put(key, new Entry(stack.copyWithCount(1), accepted));
			}
		}

		private int count(ItemStack stack) {
			Entry entry = items.get(details(stack));
			return entry == null ? 0 : entry.count;
		}

		private void replace(Map<String, Integer> names) {
			items.clear();
			names.forEach((name, count) -> items.put(name, new Entry(ItemStack.EMPTY, count)));
		}

		private int remove(ItemStack stack, int count) {
			String exactKey = details(stack);
			String key = items.containsKey(exactKey) ? exactKey : null;
			if (key == null) {
				String displayName = stack.getName().getString();
				for (Map.Entry<String, Entry> candidate : items.entrySet()) {
					Entry value = candidate.getValue();
					String knownName = value.stack.isEmpty()
							? candidate.getKey() : value.stack.getName().getString();
					if (knownName.equals(displayName)) {
						key = candidate.getKey();
						break;
					}
				}
			}
			if (key == null) return 0;
			Entry old = items.get(key);
			int removed = Math.min(old.count, Math.max(0, count));
			int remainingCount = old.count - removed;
			if (remainingCount <= 0) items.remove(key);
			else items.put(key, new Entry(old.stack, remainingCount));
			return removed;
		}

		private void invalidateServerTime(String newStatus) {
			remaining = "请使用烹饪钟重新校准";
			product = "";
			status = newStatus;
			serverUpdatedAt = 0L;
			needsServerRefresh = true;
			calibratedRemainingMillis = -1L;
			calibratedAtNanos = 0L;
			serverConfirmedComplete = false;
		}

		private long calibrate(String serverRemaining) {
			calibratedRemainingMillis = FermentationCountdown.parseMillis(serverRemaining);
			calibratedAtNanos = calibratedRemainingMillis >= 0L ? System.nanoTime() : 0L;
			serverConfirmedComplete = false;
			return calibratedRemainingMillis;
		}

		private void markServerComplete() {
			remaining = "已完成";
			calibratedRemainingMillis = 0L;
			calibratedAtNanos = System.nanoTime();
			status = "已完成";
			needsServerRefresh = false;
			serverConfirmedComplete = true;
		}

		private Panel panel() {
			List<Line> lines = new ArrayList<>();
			int shown = 0;
			for (Map.Entry<String, Entry> mapped : items.entrySet()) {
				if (shown++ >= MAX_VISIBLE_ITEMS) break;
				Entry entry = mapped.getValue();
				String name = entry.stack.isEmpty() ? mapped.getKey() : entry.stack.getName().getString();
				lines.add(new Line(entry.stack, name + " ×" + entry.count));
			}
			if (items.size() > MAX_VISIBLE_ITEMS) {
				lines.add(new Line(ItemStack.EMPTY, "以及其他 " + (items.size() - MAX_VISIBLE_ITEMS) + " 种食材"));
			}
			if (lines.isEmpty()) lines.add(new Line(ItemStack.EMPTY, "桶内物品：等待记录"));
			if (!product.isEmpty()) lines.add(new Line(ItemStack.EMPTY, "产物：" + product));
			long projectedRemaining = FermentationCountdown.remainingMillis(
					calibratedRemainingMillis, calibratedAtNanos, System.nanoTime());
			String timer = needsServerRefresh ? "请使用烹饪钟重新校准"
					: serverConfirmedComplete ? "已完成（服务器确认）"
					: projectedRemaining > 0L
					? FermentationCountdown.formatMillis(projectedRemaining) + "（服务器校准·本地推算）"
					: calibratedRemainingMillis >= 0L
					? "预计已到，等待服务器确认"
					: "请使用烹饪钟更新".equals(remaining) ? "等待烹饪钟" : remaining + "（服务器）";
			lines.add(new Line(ItemStack.EMPTY, "时间：" + timer));
			lines.add(new Line(ItemStack.EMPTY, needsServerRefresh
					? "更新：服务器时间已失效"
					: serverUpdatedAt == 0 ? "更新：未校准" : "更新：" + elapsed(serverUpdatedAt)));
			return new Panel(pos, "发酵桶 · " + status, 0xFFFFB45E, lines);
		}
	}

	private static final class Cooker {
		private final BlockPos pos;
		private String cookwareName = "厨具";
		private List<Entry> contents = List.of();
		private String signature = "";
		private long estimateStarted;
		private long lastSeen;
		private boolean completed;
		private boolean open;
		private boolean lidStateKnown;

		private Cooker(BlockPos pos) { this.pos = pos; }

		private boolean hasContents() {
			return !contents.isEmpty();
		}

		private void observe(String observedCookwareName, boolean observedOpen, List<ItemStack> observed, long now) {
			lastSeen = now;
			if (observedCookwareName != null && !observedCookwareName.isBlank()) cookwareName = observedCookwareName;
			boolean closedNow = !observedOpen;
			boolean justClosed = lidStateKnown && open && closedNow;
			open = observedOpen;
			lidStateKnown = true;
			String current = observed.stream().map(CookwareAssistant::details).sorted()
					.reduce("", (left, right) -> left + "|" + right);
			if (open) {
				estimateStarted = 0L;
				completed = false;
			} else if (!observed.isEmpty() && (justClosed || estimateStarted == 0L)) {
				estimateStarted = now;
			}
			if (current.equals(signature)) return;
			boolean wasCooking = contents.stream().mapToInt(Entry::count).sum() > 1;
			completed = wasCooking && observed.size() == 1;
			if (!open && !observed.isEmpty()
					&& (!wasCooking || observed.size() > contents.stream().mapToInt(Entry::count).sum())) {
				estimateStarted = now;
			}
			contents = aggregate(observed);
			signature = current;
		}

		private Panel panel() {
			List<Line> lines = new ArrayList<>();
			for (int index = 0; index < Math.min(MAX_VISIBLE_ITEMS, contents.size()); index++) {
				Entry entry = contents.get(index);
				lines.add(new Line(entry.stack, entry.stack.getName().getString() + " ×" + entry.count));
			}
			if (contents.size() > MAX_VISIBLE_ITEMS) {
				lines.add(new Line(ItemStack.EMPTY, "以及其他 " + (contents.size() - MAX_VISIBLE_ITEMS) + " 种食材"));
			}
			long remaining = estimateStarted == 0L ? 0L
					: Math.max(0, COOK_ESTIMATE_MS - (System.currentTimeMillis() - estimateStarted));
			String timer = open ? "无盖状态：未开始计时"
					: completed ? "服务器已完成" : remaining > 0
					? String.format(Locale.ROOT, "预计：%.1f 秒（本地）", remaining / 1_000D)
					: "预计时间已到，等待服务器";
			lines.add(new Line(ItemStack.EMPTY, timer));
			return new Panel(pos, open ? cookwareName
					: completed ? cookwareName + " · 已完成" : cookwareName + " · 烹饪中",
					0xFF74E6FF, lines);
		}
	}
}

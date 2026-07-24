package cn.simmc.simpricedisplay;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public final class ArcaneCooldownHud {
	private static final Pattern COMPLETE = Pattern.compile("^(.+?)\\s+冷却完成$");
	private static final Pattern SHIELD_BAR_PREFIX = Pattern.compile("^\\s*(?:🟥\\s*)+");
	private static final Pattern ARCANE_INPUT_HINT_ONLY = Pattern.compile("(?i)^\\s*(?:(?:上|下|丌|Shift)\\s*)+$");
	private static final Pattern ARCANE_INPUT_HINT = Pattern.compile("(?i)(?<!\\S)(?:上|下|丌|Shift)(?!\\S)");
	private static final int ICON_SLOT = 16;
	private static final int NAME_WIDTH = 52;
	private static final int BAR_WIDTH = 88;
	private static final int ROW_HEIGHT = 19;
	private static final int TOTAL_WIDTH = ICON_SLOT + 3 + NAME_WIDTH + 4 + BAR_WIDTH;
	private static final String ICON_PATH = "textures/gui/arcane/";
	private static final Identifier UNKNOWN_ICON = icon("21_red_barrier.png");
	private static final Map<String, Identifier> ARCANE_ICONS = Map.ofEntries(
			Map.entry("混乱射线", icon("01_purple_orb.png")),
			Map.entry("腾云术", icon("02_cyan_eye.png")),
			Map.entry("火球术", icon("03_pink_shard.png")),
			Map.entry("克敌先机", icon("04_yellow_bolt_rune.png")),
			Map.entry("引力术", icon("05_purple_chain.png")),
			Map.entry("治愈术", icon("06_cyan_plus.png")),
			Map.entry("治疗射线", icon("07_blue_cross_sword.png")),
			Map.entry("冰刃术", icon("08_blue_crescent.png")),
			Map.entry("寒冰吐息", icon("09_blue_streaks.png")),
			Map.entry("跳跃术", icon("10_cyan_cursor.png")),
			Map.entry("凌步术", icon("11_cyan_hook.png")),
			Map.entry("雷击", icon("12_golden_symbol.png")),
			Map.entry("斥力术", icon("13_purple_x.png")),
			Map.entry("激流术", icon("14_blue_crystal.png")),
			Map.entry("蜘化术", icon("15_green_grid.png")),
			Map.entry("火焰吐息", icon("16_pink_slash.png")),
			Map.entry("火陨术", icon("17_pink_hook.png")),
			Map.entry("御风术", icon("18_cyan_wing.png")),
			Map.entry("雷电射线", icon("19_yellow_lightning.png")),
			Map.entry("后撤步", icon("20_orange_rune.png"))
	);
	private static final Map<String, Cooldown> cooldowns = new LinkedHashMap<>();
	private static volatile List<String> equippedArcanes = List.of();
	private static ArcaneHudConfig config;
	private static KeyBinding settingsKey;
	private static boolean wandHeld;
	private static int lastWandComponentsHash;
	private static int lastWandIdentity;

	private ArcaneCooldownHud() {}

	public static void register() {
		config = ArcaneHudConfig.load();
		settingsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.simes.settings", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_O, "category.simes"));
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
				dispatcher.register(literal("simes").executes(context -> openSettings())
						.then(literal("hud").executes(context -> openSettings()))
						.then(literal("diagnose").executes(context -> SimesDiagnostics.createCommand()))));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (settingsKey.wasPressed()) openSettings();
			updateEquippedArcanes(client);
			cleanup();
		});
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (overlay) return;
			Matcher complete = COMPLETE.matcher(message.getString().trim());
			if (complete.matches()) finish(complete.group(1).trim());
		});
		HudElementRegistry.attachElementAfter(VanillaHudElements.ARMOR_BAR,
				Identifier.of(SimesClient.MOD_ID, "arcane_cooldowns"), ArcaneCooldownHud::render);
	}

	public static boolean handleActionBar(Text text) {
		if (config == null || !config.arcaneEnabled) {
			if (DebugRecorder.isRecording()) DebugRecorder.recordArcaneDecision("result=PASS reason=ARCANE_DISABLED");
			return false;
		}
		String raw = text.getString();
		boolean shieldEquipped = shieldEquipped();
		Matcher shieldPrefix = SHIELD_BAR_PREFIX.matcher(raw);
		boolean shieldBar = shieldEquipped && (raw.isBlank() || shieldPrefix.find());
		String cleaned = shieldBar ? SHIELD_BAR_PREFIX.matcher(raw).replaceFirst("").trim() : raw;
		if (shieldBar && cleaned.isEmpty()) {
			if (DebugRecorder.isRecording()) DebugRecorder.recordArcaneDecision("result=CANCEL reason=SHIELD_ONLY raw=\"" + raw + "\"");
			return true;
		}
		ArcaneCooldownParser.Result parsed = ArcaneCooldownParser.parse(cleaned);
		if (parsed.values().isEmpty()) {
			if (customModeActive() && isArcaneInputHintOnly(cleaned)) {
				if (DebugRecorder.isRecording()) DebugRecorder.recordArcaneDecision(
						"result=CANCEL reason=LEGACY_INPUT_HINT raw=\"" + raw + "\"");
				return true;
			}
			if (DebugRecorder.isRecording()) DebugRecorder.recordArcaneDecision("result=PASS reason=FORMAT_NOT_MATCHED raw=\"" + raw
					+ "\" cleaned=\"" + cleaned + "\" codePoints=" + DebugRecorder.codePoints(cleaned));
			return false;
		}
		long now = System.nanoTime();
		Set<String> seen = new HashSet<>();
		synchronized (cooldowns) {
			for (ArcaneCooldownParser.Value value : parsed.values()) {
				seen.add(value.name());
				Cooldown old = cooldowns.get(value.name());
				if (old == null || value.remaining() > old.remainingAt(now) + 0.35) {
					double total = Math.ceil((value.remaining() + 0.05) * 5.0) / 5.0;
					cooldowns.put(value.name(), new Cooldown(value.name(), value.remaining(), Math.max(total, 0.2), now, cooldowns.size()));
				} else {
					old.update(value.remaining(), now);
				}
			}
			for (Cooldown value : cooldowns.values()) {
				if (!seen.contains(value.name) && value.exitStarted == 0L) value.exitStarted = now;
			}
		}
		if (!customModeActive()) {
			if (DebugRecorder.isRecording()) DebugRecorder.recordArcaneDecision("result=PASS reason=VANILLA_MODE matched=" + parsed.values().size()
					+ " shield=" + shieldBar + " values=" + parsed.values());
			if (shieldBar) {
				MinecraftClient.getInstance().inGameHud.setOverlayMessage(Text.literal(cleaned), false);
				return true;
			}
			return false;
		}
		String residual = stripArcaneInputHints(parsed.residual());
		if (!residual.isEmpty()) {
			MinecraftClient.getInstance().inGameHud.setOverlayMessage(Text.literal(residual), false);
		}
		if (DebugRecorder.isRecording()) DebugRecorder.recordArcaneDecision("result=CANCEL reason=SIMES_HUD matched=" + parsed.values().size()
				+ " shield=" + shieldBar + " residual=\"" + residual + "\" values=" + parsed.values()
				+ " cooldownCount=" + cooldowns.size());
		return true;
	}

	static boolean isArcaneInputHintOnly(String value) {
		return value != null && ARCANE_INPUT_HINT_ONLY.matcher(value).matches();
	}

	static String stripArcaneInputHints(String value) {
		if (value == null || value.isBlank()) return "";
		return ARCANE_INPUT_HINT.matcher(value).replaceAll(" ").trim().replaceAll("\\s{2,}", " ");
	}

	private static boolean shieldEquipped() {
		MinecraftClient client = MinecraftClient.getInstance();
		return client.player != null && (client.player.getMainHandStack().isOf(net.minecraft.item.Items.SHIELD)
				|| client.player.getOffHandStack().isOf(net.minecraft.item.Items.SHIELD));
	}

	private static void finish(String name) {
		synchronized (cooldowns) {
			Cooldown value = cooldowns.get(name);
			if (value != null && value.exitStarted == 0L) value.exitStarted = System.nanoTime();
		}
	}

	private static boolean customModeActive() {
		return config != null && config.arcaneEnabled && config.simesMode;
	}

	private static int openSettings() {
		MinecraftClient client = MinecraftClient.getInstance();
		client.send(() -> client.setScreen(new ArcaneHudSettingsScreen(client.currentScreen)));
		return 1;
	}

	private static void cleanup() {
		synchronized (cooldowns) {
			if (cooldowns.isEmpty()) return;
			long now = System.nanoTime();
			for (Cooldown value : cooldowns.values()) {
				if (value.exitStarted == 0L && value.remainingAt(now) <= 0.05) value.exitStarted = now;
			}
			cooldowns.values().removeIf(value -> value.exitStarted != 0L && now - value.exitStarted > 250_000_000L);
		}
	}

	private static List<Cooldown> cooldownSnapshot() {
		synchronized (cooldowns) {
			return new ArrayList<>(cooldowns.values());
		}
	}

	private static void updateEquippedArcanes(MinecraftClient client) {
		ItemStack stack = client.player == null ? ItemStack.EMPTY : client.player.getMainHandStack();
		if (!ManaHud.isArcaneCodex(stack)) {
			wandHeld = false;
			lastWandIdentity = 0;
			lastWandComponentsHash = 0;
			equippedArcanes = List.of();
			return;
		}
		wandHeld = true;
		int stackIdentity = System.identityHashCode(stack);
		int componentsHash = stack.getComponents().hashCode();
		if (stackIdentity == lastWandIdentity && componentsHash == lastWandComponentsHash) return;
		lastWandIdentity = stackIdentity;
		lastWandComponentsHash = componentsHash;

		List<String> detected = extractEquippedArcanes(stack);
		if (!detected.isEmpty()) equippedArcanes = List.copyOf(detected);
	}

	/**
	 * The server stores the three equipped spells in three ordered lore rows:
	 * left click, Shift, right click. Read those rows directly instead of scanning
	 * the complete component serialization, whose custom-name section changes to
	 * the most recently cast spell and can therefore reorder the HUD.
	 */
	private static List<String> extractEquippedArcanes(ItemStack stack) {
		LoreComponent lore = stack.get(DataComponentTypes.LORE);
		if (lore == null) return List.of();
		List<String> detected = new ArrayList<>(3);
		for (Text line : lore.lines()) {
			String plain = line.getString();
			String matched = arcaneNameIn(plain);
			if (matched != null && !detected.contains(matched)) {
				detected.add(matched);
				if (detected.size() == 3) break;
			}
		}
		return detected;
	}

	private static String arcaneNameIn(String text) {
		for (String name : ARCANE_ICONS.keySet()) {
			if (text.contains(name)) return name;
		}
		return null;
	}

	private static void render(DrawContext context, net.minecraft.client.render.RenderTickCounter tickCounter) {
		if (!customModeActive()) {
			if (DebugRecorder.isRecording()) DebugRecorder.recordArcaneRender("result=SKIP reason=MODE_DISABLED");
			return;
		}
		if (!wandHeld || equippedArcanes.isEmpty()) {
			if (DebugRecorder.isRecording()) DebugRecorder.recordArcaneRender("result=SKIP reason=NO_WAND_ARCANES");
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		int width = client.getWindow().getScaledWidth();
		int height = client.getWindow().getScaledHeight();
		int baseX = configuredX(width);
		int baseY = configuredY(height);
		if (DebugRecorder.isRecording()) {
			DebugRecorder.recordArcaneRender("result=RENDERED entries=" + cooldowns.size()
					+ " position=" + baseX + "," + baseY + " scale=" + config.scalePercent
					+ " hudHidden=" + client.options.hudHidden);
		}
		renderRows(context, baseX, baseY, config.scalePercent / 100.0f,
				System.nanoTime(), List.copyOf(equippedArcanes), false);
	}

	static void renderPreview(DrawContext context, int x, int y, float scale) {
		renderRows(context, x, y, scale, System.nanoTime(),
				List.of("治愈术", "火球术", "雷电射线"), true);
	}

	private static void renderRows(DrawContext context, int x, int baseY, float scale,
			long now, List<String> rows, boolean preview) {
		MinecraftClient client = MinecraftClient.getInstance();
		context.getMatrices().pushMatrix();
		context.getMatrices().scale(scale, scale);
		int sx = Math.round(x / scale);
		int sy = Math.round(baseY / scale);
		for (int index = 0; index < rows.size(); index++) {
			String name = rows.get(index);
			Cooldown value;
			synchronized (cooldowns) { value = cooldowns.get(name); }
			double remaining = preview ? (index == 0 ? 0.0 : index == 1 ? 1.2 : 0.6)
					: value == null ? 0.0 : Math.max(0.0, value.remainingAt(now));
			double total = preview ? (index == 0 ? 1.0 : index == 1 ? 2.0 : 1.6)
					: value == null ? 1.0 : value.total;
			// baseY remains the bottom anchor used by existing configs, while the
			// equipped order is rendered top-to-bottom (slot 1, slot 2, slot 3).
			int rowY = sy - (rows.size() - 1 - index) * ROW_HEIGHT;
			float barAlpha = preview ? (index == 0 ? 0.0f : 1.0f)
					: value == null ? 0.0f : value.alpha(now);
			drawRow(context, client, sx, rowY, name, remaining, total, barAlpha);
		}
		context.getMatrices().popMatrix();
	}

	private static void drawRow(DrawContext context, MinecraftClient client, int x, int y,
			String name, double remaining, double total, float barAlpha) {
		int a = Math.max(0, Math.min(255, Math.round(barAlpha * 255)));
		int barX = x + ICON_SLOT + 3 + NAME_WIDTH + 4;
		int barY = y - 14;
		int color = ArcaneColors.forName(name).primary();
		context.drawTexture(RenderPipelines.GUI_TEXTURED, iconFor(name), x, y - 16,
				0.0f, 0.0f, ICON_SLOT, ICON_SLOT, 32, 32, 32, 32);
		String shownName = trimName(client, name, NAME_WIDTH);
		Text coloredName = Text.literal(shownName).styled(style -> style.withBold(true).withColor(color));
		context.drawText(client.textRenderer, coloredName, x + ICON_SLOT + 3, y - 12, 0xFFFFFFFF, false);
		if (a > 0) {
			context.fill(barX, barY, barX + BAR_WIDTH, barY + 12, (a << 24) | 0x111111);
			context.fill(barX + 1, barY + 1, barX + BAR_WIDTH - 1, barY + 11, (a << 24) | 0x555555);
			context.fill(barX + 3, barY + 3, barX + BAR_WIDTH - 3, barY + 9, (a << 24) | 0x241A12);
			int inner = BAR_WIDTH - 6;
			int fill = Math.max(0, Math.min(inner, (int)Math.round(inner * remaining / Math.max(total, 0.1))));
			if (fill > 0) context.fill(barX + 3, barY + 3, barX + 3 + fill, barY + 9, (a << 24) | (color & 0xFFFFFF));
			if (remaining > 0.05) {
				String time = remaining >= 10.0 ? String.format(Locale.ROOT, "%.0fs", Math.ceil(remaining))
						: String.format(Locale.ROOT, "%.1fs", remaining);
				int tx = barX + (BAR_WIDTH - client.textRenderer.getWidth(time)) / 2;
				context.drawTextWithShadow(client.textRenderer, time, tx, barY + 2, (a << 24) | 0xFFFFFF);
			}
		}
	}

	private static String trimName(MinecraftClient client, String name, int width) {
		if (client.textRenderer.getWidth(name) <= width) return name;
		return client.textRenderer.trimToWidth(name, width - client.textRenderer.getWidth("…")) + "…";
	}

	private static Identifier icon(String file) {
		return Identifier.of(SimesClient.MOD_ID, ICON_PATH + file);
	}

	static Identifier iconFor(String name) {
		return ARCANE_ICONS.getOrDefault(name, UNKNOWN_ICON);
	}

	public static ArcaneHudConfig config() { return config; }
	public static Text settingsKeyText() {
		return settingsKey == null ? Text.literal("O") : settingsKey.getBoundKeyLocalizedText();
	}
	public static KeyBinding settingsKeyBinding() { return settingsKey; }
	static int totalWidth() { return TOTAL_WIDTH; }
	static int configuredX(int width) { return config.x < 0 ? width / 2 - 91 : (int)Math.round(config.x * width); }
	static int configuredY(int height) { return config.y < 0 ? height - 55 : (int)Math.round(config.y * height); }

	private static final class Cooldown {
		private final String name;
		private double serverRemaining;
		private double total;
		private long updatedAt;
		private final long createdAt;
		private long exitStarted;
		private float visualIndex;
		private long visualUpdatedAt;

		private Cooldown(String name, double remaining, double total, long now, int index) {
			this.name = name; this.serverRemaining = remaining; this.total = total; this.updatedAt = now; this.createdAt = now;
			this.visualIndex = index; this.visualUpdatedAt = now;
		}
		private void update(double remaining, long now) {
			// The server can deliver a slightly older cooldown sample after the 0-second sample.
			// Clamp ordinary corrections to the locally predicted value so one cooldown never runs backwards.
			double predicted = remainingAt(now);
			serverRemaining = remaining <= 0.05 ? 0.0 : Math.min(remaining, predicted);
			updatedAt = now;
			total = Math.max(total, remaining);
			exitStarted = serverRemaining <= 0.05 ? now : 0L;
		}
		private double remainingAt(long now) { return Math.max(0.0, serverRemaining - (now - updatedAt) / 1_000_000_000.0); }
		private float alpha(long now) {
			if (exitStarted != 0L) return 1.0f - ease(Math.min(1.0f, (now - exitStarted) / 250_000_000.0f));
			return ease(Math.min(1.0f, (now - createdAt) / 180_000_000.0f));
		}
		private float visualIndex(long now, int target) {
			float elapsed = Math.min(1.0f, (now - visualUpdatedAt) / 180_000_000.0f);
			visualIndex += (target - visualIndex) * ease(elapsed);
			visualUpdatedAt = now;
			return visualIndex;
		}
		private float ease(float t) { float q = 1.0f - t; return 1.0f - q * q * q; }
	}
}

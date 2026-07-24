package cn.simmc.simpricedisplay;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.network.packet.s2c.play.BossBarS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Tracks SimMC's server-authored casting/effect bars and the short local global cooldown. */
public final class ArcaneStatusHud {
	private static final Pattern CASTING = Pattern.compile("^\\s*正在吟唱\\s+(.+?)\\s*$");
	private static final Pattern DURATION = Pattern.compile("^\\s*(.+?)剩余\\s*[:：]\\s*(\\d+)\\s*tick\\s*$", Pattern.CASE_INSENSITIVE);
	private static final Pattern RELEASED = Pattern.compile("^\\s*释放\\s+(.+?)\\s*$");
	private static final Pattern GLOBAL_HINT = Pattern.compile("^\\s*.+?\\s+处于公共冷却中[，,]\\s*剩余\\s*([0-9.]+)\\s*秒\\s*$");
	private static final Pattern ARCANE_LEVEL = Pattern.compile("^\\s*(.+?)\\s+Lv\\s*5(?:\\s+MAX(?:/MAX)?)?\\s*$", Pattern.CASE_INSENSITIVE);
	private static final int ICON_SIZE = 16;
	private static final int LABEL_WIDTH = 96;
	private static final int BAR_WIDTH = 88;
	private static final int ROW_HEIGHT = 19;
	private static final int TOTAL_WIDTH = ICON_SIZE + 3 + LABEL_WIDTH + 4 + BAR_WIDTH;
	private static final int GLOBAL_LABEL_WIDTH = 72;
	private static final int GLOBAL_BAR_WIDTH = 48;
	private static final int GLOBAL_TOTAL_WIDTH = ICON_SIZE + 3 + GLOBAL_LABEL_WIDTH + 4 + GLOBAL_BAR_WIDTH;
	private static final long EXIT_NANOS = 220_000_000L;

	private static final Map<String, Double> GLOBAL_COOLDOWNS = Map.ofEntries(
			Map.entry("混乱射线", 0.5), Map.entry("腾云术", 11.0), Map.entry("火球术", 0.5),
			Map.entry("克敌先机", 0.5), Map.entry("引力术", 18.0), Map.entry("治愈术", 0.5),
			Map.entry("治疗射线", 0.5), Map.entry("冰刃术", 0.5), Map.entry("寒冰吐息", 0.5),
			Map.entry("跳跃术", 2.5), Map.entry("凌步术", 32.0), Map.entry("雷击", 0.5),
			Map.entry("斥力术", 0.0), Map.entry("激流术", 30.0), Map.entry("蛛化术", 3.5),
			Map.entry("火焰吐息", 0.5), Map.entry("火陨术", 0.5), Map.entry("御风术", 40.0),
			Map.entry("雷电射线", 0.5), Map.entry("后撤步", 0.0)
	);
	private static final Map<String, String> ALIASES = Map.ofEntries(
			Map.entry("腾云", "腾云术"), Map.entry("凌步", "凌步术"), Map.entry("御风", "御风术"),
			Map.entry("跳跃", "跳跃术"), Map.entry("蛛化", "蛛化术"), Map.entry("激流", "激流术"),
			Map.entry("斥力", "斥力术"), Map.entry("引力", "引力术"), Map.entry("火陨", "火陨术")
	);

	private static final Map<UUID, Status> statuses = new LinkedHashMap<>();
	private static final Set<UUID> hiddenArcaneLevelBars = new HashSet<>();
	/**
	 * IDs whose ADD packet was cancelled. Every following packet for such an ID
	 * must also be cancelled until REMOVE, even if its local animation state has
	 * already expired; vanilla never created a ClientBossBar for the ID.
	 */
	private static final SuppressedBossBarIds suppressedBossBars = new SuppressedBossBarIds();
	private static GlobalCooldown globalCooldown;

	private ArcaneStatusHud() {}

	public static void register() {
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (!overlay) handleGameMessage(message.getString());
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> cleanup());
		HudElementRegistry.attachElementAfter(VanillaHudElements.ARMOR_BAR,
				Identifier.of(SimesClient.MOD_ID, "arcane_status"), ArcaneStatusHud::render);
	}

	public static boolean handleBossBar(BossBarS2CPacket packet) {
		boolean[] recognized = {false};
		boolean[] suppress = {false};
		long now = System.nanoTime();
		packet.accept(new BossBarS2CPacket.Consumer() {
			@Override
			public void add(UUID id, Text name, float percent, BossBar.Color color, BossBar.Style style,
					boolean darkenSky, boolean dragonMusic, boolean thickenFog) {
				String raw = name.getString();
				Matcher casting = CASTING.matcher(raw);
				Matcher level = ARCANE_LEVEL.matcher(raw);
				boolean hide = shouldSuppressRecognizedBars();
				if (level.matches() && GLOBAL_COOLDOWNS.containsKey(canonical(level.group(1)))) {
					if (hide) { hiddenArcaneLevelBars.add(id); suppressedBossBars.suppress(id); }
					recognized[0] = true;
					suppress[0] = hide;
				} else if (casting.matches() && isKnownArcane(casting.group(1))) {
					statuses.put(id, Status.casting(canonical(casting.group(1)), percent, now, hide));
					if (hide) suppressedBossBars.suppress(id);
					recognized[0] = true;
					suppress[0] = hide;
				} else if (raw.isBlank() && style == BossBar.Style.NOTCHED_10 && percent >= 0.99f) {
					// Unknown blank bars must remain visible. SimMC uses this packet shape for
					// teleports, boss mechanics and arcane duration bars alike, so classification
					// is deferred until a later name packet contains a known arcane name.
					statuses.put(id, Status.pending(now, false));
				}
			}

			@Override public void remove(UUID id) {
				if (suppressedBossBars.release(id)) {
					hiddenArcaneLevelBars.remove(id);
					Status value = statuses.get(id);
					if (value != null) value.finish(now, value.kind == Kind.CASTING && value.progress < 0.995f);
					recognized[0] = true;
					suppress[0] = true;
					return;
				}
				if (hiddenArcaneLevelBars.remove(id)) {
					recognized[0] = true;
					suppress[0] = true;
					return;
				}
				Status value = statuses.get(id);
				if (value != null) {
					value.finish(now, value.kind == Kind.CASTING && value.progress < 0.995f);
					recognized[0] = true;
					suppress[0] = value.suppressed;
				}
			}

			@Override public void updateProgress(UUID id, float percent) {
				if (suppressedBossBars.contains(id)) {
					Status value = statuses.get(id);
					if (value != null) value.progress = clamp(percent);
					recognized[0] = true; suppress[0] = true; return;
				}
				if (hiddenArcaneLevelBars.contains(id)) { recognized[0] = true; suppress[0] = true; return; }
				Status value = statuses.get(id);
				if (value != null) {
					value.progress = clamp(percent);
					recognized[0] = true;
					suppress[0] = value.suppressed;
				}
			}

			@Override public void updateName(UUID id, Text name) {
				if (suppressedBossBars.contains(id)) {
					updateSuppressedName(id, name, now);
					recognized[0] = true; suppress[0] = true; return;
				}
				if (hiddenArcaneLevelBars.contains(id)) { recognized[0] = true; suppress[0] = true; return; }
				Status value = statuses.get(id);
				if (value == null) return;
				String raw = name.getString();
				Matcher casting = CASTING.matcher(raw);
				Matcher duration = DURATION.matcher(raw);
				if (casting.matches() && isKnownArcane(casting.group(1))) {
					boolean hide = shouldSuppressRecognizedBars();
					value.suppressed = hide;
					value.activate(Kind.CASTING, canonical(casting.group(1)), 0, now);
					if (hide) suppressExistingBossBar(id);
					recognized[0] = true;
					suppress[0] = hide;
				} else if (duration.matches() && isKnownArcane(duration.group(1))) {
					int ticks = Integer.parseInt(duration.group(2));
					boolean hide = shouldSuppressRecognizedBars();
					value.suppressed = hide;
					value.activate(Kind.DURATION, canonical(duration.group(1)), ticks, now);
					if (hide) suppressExistingBossBar(id);
					recognized[0] = true;
					suppress[0] = hide;
				} else if (value.kind != Kind.PENDING) {
					recognized[0] = true;
					suppress[0] = value.suppressed;
				} else {
					// The deferred bar is not an arcane bar. Forget it and leave vanilla's
					// complete packet stream untouched from this point onward.
					statuses.remove(id);
				}
			}

			@Override public void updateStyle(UUID id, BossBar.Color color, BossBar.Style style) {
				if (suppressedBossBars.contains(id)) { recognized[0] = true; suppress[0] = true; return; }
				if (hiddenArcaneLevelBars.contains(id)) { recognized[0] = true; suppress[0] = true; return; }
				Status value = statuses.get(id); recognized[0] = value != null; suppress[0] = value != null && value.suppressed;
			}
			@Override public void updateProperties(UUID id, boolean darkenSky, boolean dragonMusic, boolean thickenFog) {
				if (suppressedBossBars.contains(id)) { recognized[0] = true; suppress[0] = true; return; }
				if (hiddenArcaneLevelBars.contains(id)) { recognized[0] = true; suppress[0] = true; return; }
				Status value = statuses.get(id); recognized[0] = value != null; suppress[0] = value != null && value.suppressed;
			}
		});
		return recognized[0] && suppress[0];
	}

	private static void updateSuppressedName(UUID id, Text name, long now) {
		Status value = statuses.get(id);
		if (value == null) return;
		String raw = name.getString();
		Matcher casting = CASTING.matcher(raw);
		Matcher duration = DURATION.matcher(raw);
		if (casting.matches()) value.activate(Kind.CASTING, canonical(casting.group(1)), 0, now);
		else if (duration.matches()) value.activate(Kind.DURATION, canonical(duration.group(1)),
				Integer.parseInt(duration.group(2)), now);
	}

	private static boolean isKnownArcane(String name) {
		return GLOBAL_COOLDOWNS.containsKey(canonical(name));
	}

	private static void suppressExistingBossBar(UUID id) {
		suppressedBossBars.suppress(id);
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.inGameHud != null) {
			client.inGameHud.getBossBarHud().handlePacket(BossBarS2CPacket.remove(id));
		}
	}

	private static void handleGameMessage(String raw) {
		if (!enabled()) return;
		Matcher released = RELEASED.matcher(raw);
		if (released.matches()) {
			String name = canonical(released.group(1));
			double seconds = GLOBAL_COOLDOWNS.getOrDefault(name, 0.0);
			globalCooldown = seconds > 0.0 ? new GlobalCooldown(name, seconds, seconds, System.nanoTime()) : null;
			return;
		}
		Matcher hint = GLOBAL_HINT.matcher(raw);
		if (hint.matches()) {
			double remaining = Double.parseDouble(hint.group(1));
			long now = System.nanoTime();
			if (globalCooldown == null) globalCooldown = new GlobalCooldown("公共冷却", remaining, remaining, now);
			else globalCooldown.update(remaining, now);
		}
	}

	public static void reset() {
		statuses.clear();
		hiddenArcaneLevelBars.clear();
		suppressedBossBars.clear();
		globalCooldown = null;
	}

	private static void cleanup() {
		if (statuses.isEmpty() && globalCooldown == null) return;
		long now = System.nanoTime();
		statuses.entrySet().removeIf(entry -> {
			Status value = entry.getValue();
			if (value.kind == Kind.PENDING && now - value.createdAt > 1_000_000_000L) return true;
			return value.exitAt != 0L && now - value.exitAt > EXIT_NANOS;
		});
		if (globalCooldown != null && globalCooldown.remaining(now) <= 0.0) globalCooldown = null;
	}

	private static boolean enabled() {
		ArcaneHudConfig config = ArcaneCooldownHud.config();
		return config != null && config.arcaneEnabled && config.arcaneStatusEnabled;
	}

	private static boolean shouldSuppressRecognizedBars() {
		MinecraftClient client = MinecraftClient.getInstance();
		ArcaneHudConfig config = ArcaneCooldownHud.config();
		return enabled() && config.simesMode && config.hideRecognizedArcaneBossBars
				&& ServerGate.isTarget(client.getCurrentServerEntry());
	}

	private static void render(DrawContext context, net.minecraft.client.render.RenderTickCounter tickCounter) {
		ArcaneHudConfig config = ArcaneCooldownHud.config();
		if (!enabled() || !config.simesMode) return;
		if (statuses.isEmpty() && globalCooldown == null) return;
		long now = System.nanoTime();
		List<Row> statusRows = statusRows(now);
		List<Row> cooldownRows = globalCooldownRows(now);
		MinecraftClient client = MinecraftClient.getInstance();
		if (!statusRows.isEmpty()) renderRows(context, configuredX(client.getWindow().getScaledWidth()),
				configuredY(client.getWindow().getScaledHeight()), config.arcaneStatusScalePercent / 100.0f, statusRows);
		if (!cooldownRows.isEmpty()) renderGlobalRows(context, configuredGlobalX(client.getWindow().getScaledWidth()),
				configuredGlobalY(client.getWindow().getScaledHeight()), config.globalCooldownScalePercent / 100.0f, cooldownRows);
	}

	static void renderPreview(DrawContext context, int x, int y, float scale) {
		renderRows(context, x, y, scale, List.of(
				new Row("火陨术", "吟唱 火陨术", 0.62f, false),
				new Row("御风术", "御风 持续 13.2s", 0.53f, false)));
	}

	static void renderGlobalPreview(DrawContext context, int x, int y, float scale) {
		renderGlobalRows(context, x, y, scale, List.of(
				new Row("引力术", "公共冷却 8.4s", 0.47f, false)));
	}

	private static List<Row> statusRows(long now) {
		List<Row> rows = new ArrayList<>();
		for (Status value : statuses.values()) {
			if (value.kind == Kind.PENDING) continue;
			float alpha = value.exitAt == 0L ? 1.0f : 1.0f - Math.min(1.0f, (now - value.exitAt) / (float)EXIT_NANOS);
			String label;
			if (value.kind == Kind.CASTING) label = "吟唱 " + value.name;
			else label = displayName(value.name) + " 持续 " + formatSeconds(value.remainingTicks / 20.0);
			rows.add(new Row(value.name, label, value.progress, value.interrupted, alpha));
		}
		return rows;
	}

	private static List<Row> globalCooldownRows(long now) {
		if (globalCooldown != null) {
			double remaining = globalCooldown.remaining(now);
			if (remaining >= 1.0) return List.of(new Row(globalCooldown.name,
					"公共冷却 " + formatSeconds(remaining), (float)(remaining / globalCooldown.total), false));
		}
		return List.of();
	}

	private static void renderRows(DrawContext context, int x, int baseY, float scale, List<Row> rows) {
		renderRows(context, x, baseY, scale, rows, LABEL_WIDTH, BAR_WIDTH);
	}

	private static void renderGlobalRows(DrawContext context, int x, int baseY, float scale, List<Row> rows) {
		renderRows(context, x, baseY, scale, rows, GLOBAL_LABEL_WIDTH, GLOBAL_BAR_WIDTH);
	}

	private static void renderRows(DrawContext context, int x, int baseY, float scale, List<Row> rows,
			int labelWidth, int barWidth) {
		MinecraftClient client = MinecraftClient.getInstance();
		context.getMatrices().pushMatrix();
		context.getMatrices().scale(scale, scale);
		int sx = Math.round(x / scale), sy = Math.round(baseY / scale);
		for (int i = 0; i < rows.size(); i++) drawRow(context, client, sx, sy - i * ROW_HEIGHT, rows.get(i), labelWidth, barWidth);
		context.getMatrices().popMatrix();
	}

	private static void drawRow(DrawContext context, MinecraftClient client, int x, int y, Row row,
			int labelWidth, int barWidth) {
		int alpha = Math.max(0, Math.min(255, Math.round(row.alpha * 255.0f)));
		int color = row.interrupted ? 0xEF3B3B : ArcaneColors.forName(row.arcaneName).primary();
		context.drawTexture(RenderPipelines.GUI_TEXTURED, ArcaneCooldownHud.iconFor(row.arcaneName), x, y - 16,
				0, 0, ICON_SIZE, ICON_SIZE, 32, 32, 32, 32);
		String label = client.textRenderer.trimToWidth(row.label, labelWidth);
		context.drawTextWithShadow(client.textRenderer,
				Text.literal(label).styled(style -> style.withBold(true).withColor(color)),
				x + ICON_SIZE + 3, y - 12, (alpha << 24) | 0xFFFFFF);
		int barX = x + ICON_SIZE + 3 + labelWidth + 4, barY = y - 14;
		context.fill(barX, barY, barX + barWidth, barY + 12, (alpha << 24) | 0x111111);
		context.fill(barX + 1, barY + 1, barX + barWidth - 1, barY + 11, (alpha << 24) | 0x555555);
		context.fill(barX + 3, barY + 3, barX + barWidth - 3, barY + 9, (alpha << 24) | 0x241A12);
		int fill = Math.round((barWidth - 6) * clamp(row.progress));
		if (fill > 0) context.fill(barX + 3, barY + 3, barX + 3 + fill, barY + 9,
				(alpha << 24) | (color & 0xFFFFFF));
	}

	private static String canonical(String raw) {
		String name = raw == null ? "" : raw.trim();
		return ALIASES.getOrDefault(name, name);
	}

	private static String displayName(String canonical) {
		return canonical.endsWith("术") ? canonical.substring(0, canonical.length() - 1) : canonical;
	}

	private static String formatSeconds(double seconds) {
		return seconds >= 10.0 ? String.format(Locale.ROOT, "%.0fs", Math.ceil(seconds))
				: String.format(Locale.ROOT, "%.1fs", seconds);
	}

	private static float clamp(float value) { return Math.max(0.0f, Math.min(1.0f, value)); }
	static int totalWidth() { return TOTAL_WIDTH; }
	static int globalTotalWidth() { return GLOBAL_TOTAL_WIDTH; }
	static int previewHeight() { return ROW_HEIGHT * 2; }
	static int globalPreviewHeight() { return ROW_HEIGHT; }
	static int configuredX(int width) {
		ArcaneHudConfig c = ArcaneCooldownHud.config();
		return c.arcaneStatusX < 0 ? width / 2 - 91 : (int)Math.round(c.arcaneStatusX * width);
	}
	static int configuredY(int height) {
		ArcaneHudConfig c = ArcaneCooldownHud.config();
		return c.arcaneStatusY < 0 ? height - 118 : (int)Math.round(c.arcaneStatusY * height);
	}
	static int configuredGlobalX(int width) {
		ArcaneHudConfig c = ArcaneCooldownHud.config();
		return c.globalCooldownX < 0 ? width / 2 - 91 : (int)Math.round(c.globalCooldownX * width);
	}
	static int configuredGlobalY(int height) {
		ArcaneHudConfig c = ArcaneCooldownHud.config();
		return c.globalCooldownY < 0 ? height - 175 : (int)Math.round(c.globalCooldownY * height);
	}

	private enum Kind { PENDING, CASTING, DURATION }

	private static final class Status {
		private Kind kind;
		private String name;
		private float progress;
		private int totalTicks;
		private int remainingTicks;
		private final long createdAt;
		private long exitAt;
		private boolean interrupted;
		private boolean suppressed;

		private Status(Kind kind, String name, float progress, long now, boolean suppressed) {
			this.kind = kind; this.name = name; this.progress = clamp(progress); this.createdAt = now;
			this.suppressed = suppressed;
		}
		static Status pending(long now, boolean suppressed) { return new Status(Kind.PENDING, "", 1.0f, now, suppressed); }
		static Status casting(String name, float progress, long now, boolean suppressed) {
			return new Status(Kind.CASTING, name, progress, now, suppressed);
		}
		void activate(Kind newKind, String newName, int ticks, long now) {
			kind = newKind; name = newName; exitAt = 0L; interrupted = false;
			if (newKind == Kind.DURATION) {
				remainingTicks = ticks;
				totalTicks = Math.max(totalTicks, ticks);
				progress = totalTicks == 0 ? 0.0f : ticks / (float)totalTicks;
			}
		}
		void finish(long now, boolean wasInterrupted) { exitAt = now; interrupted = wasInterrupted; }
	}

	private static final class GlobalCooldown {
		private final String name;
		private double remaining;
		private double total;
		private long updatedAt;
		private GlobalCooldown(String name, double remaining, double total, long now) {
			this.name = name; this.remaining = remaining; this.total = Math.max(remaining, total); this.updatedAt = now;
		}
		double remaining(long now) { return Math.max(0.0, remaining - (now - updatedAt) / 1_000_000_000.0); }
		void update(double value, long now) {
			remaining = value; total = Math.max(total, value); updatedAt = now;
		}
	}

	private record Row(String arcaneName, String label, float progress, boolean interrupted, float alpha) {
		private Row(String arcaneName, String label, float progress, boolean interrupted) {
			this(arcaneName, label, progress, interrupted, 1.0f);
		}
	}
}

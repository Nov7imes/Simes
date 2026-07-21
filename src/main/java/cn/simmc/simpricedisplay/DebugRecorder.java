package cn.simmc.simpricedisplay;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
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

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/** A short, explicitly enabled local trace for investigating server-driven cooldown displays. */
public final class DebugRecorder {
	private static final Duration MAX_DURATION = Duration.ofSeconds(60);
	private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
			.withZone(ZoneId.systemDefault());
	private static final DateTimeFormatter LINE_TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
			.withZone(ZoneId.systemDefault());
	private static final List<String> lines = new ArrayList<>();
	private static boolean recording;
	private static Instant startedAt;
	private static String lastState = "";
	private static String lastValuePanelState = "";
	private static long lastStateCaptureMillis;

	private DebugRecorder() {
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
		ClientTickEvents.END_CLIENT_TICK.register(DebugRecorder::tick);
	}

	public static void recordActionBar(Text message) {
		if (recording) {
			append("ACTION_BAR_PACKET", message.getString());
		}
	}

	public static void recordValuePanel(String state) {
		if (!recording || state == null || state.equals(lastValuePanelState)) return;
		lastValuePanelState = state;
		append("VALUE_PANEL", state);
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
		lastStateCaptureMillis = 0L;
		append("DEBUG", "START | Minecraft client trace | max=60s");
		captureState(client, true);
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
		append("DEBUG", timedOut ? "AUTO END | 60 second limit" : "END");
		recording = false;
		Path file = writeLog();
		if (file == null) {
			localMessage(client, "§c[Simes] Debug 日志写入失败，请查看 latest.log");
			return 0;
		}
		localMessage(client, (timedOut ? "§e[Simes] Debug 已达到 60 秒并自动结束：" : "§a[Simes] Debug 记录已结束：")
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
		lines.add("[" + LINE_TIME.format(now) + "][+" + elapsed + "ms][" + type + "] " + clean(value));
	}

	private static String clean(String value) {
		return value == null ? "" : value.replace('\r', ' ').replace('\n', ' ');
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
}

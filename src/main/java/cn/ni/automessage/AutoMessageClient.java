package cn.ni.automessage;

import cn.simmc.simpricedisplay.ArcaneCooldownHud;
import cn.simmc.simpricedisplay.ArcaneHudConfig;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public final class AutoMessageClient implements ClientModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("Simes/AutoMessage");
	private static final int HUD_HEIGHT = 14;
	private static AutoMessageConfig config;

	@Override
	public void onInitializeClient() {
		config = AutoMessageConfig.load();
		if (config.enabled) scheduleNext();
		registerCommands();
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			if (config.enabled) scheduleNext();
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (!config.enabled || client.player == null || client.getNetworkHandler() == null) return;
			if (config.nextSendAtEpochMillis <= 0L) {
				scheduleNext();
				return;
			}
			if (System.currentTimeMillis() >= config.nextSendAtEpochMillis) {
				send(client);
				scheduleNext();
			}
		});
		HudElementRegistry.attachElementAfter(VanillaHudElements.INFO_BAR,
				Identifier.of("simes", "automessage_countdown"), AutoMessageClient::renderCountdownHud);
	}

	private static void registerCommands() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
				dispatcher.register(commandRoot()));
	}

	private static LiteralArgumentBuilder<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource>
	commandRoot() {
		return literal("atmsg")
				.executes(context -> openSettings())
				.then(literal("st").executes(context -> toggleRunning(context.getSource())));
	}

	private static int openSettings() {
		MinecraftClient client = MinecraftClient.getInstance();
		client.send(() -> client.setScreen(new AutoMessageScreen()));
		return 1;
	}

	private static int toggleRunning(
			net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source
	) {
		setEnabled(!config.enabled);
		source.sendFeedback(Text.literal(config.enabled
				? "§a[自动消息] 已启动，倒计时 HUD 已显示"
				: "§e[自动消息] 已停止"));
		return 1;
	}

	private static void renderCountdownHud(DrawContext context,
			net.minecraft.client.render.RenderTickCounter tickCounter) {
		if (config == null || !config.enabled) return;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.options.hudHidden) return;
		ArcaneHudConfig hud = ArcaneCooldownHud.config();
		float scale = hud.autoMessageScalePercent / 100.0f;
		renderHudAt(context, configuredX(client.getWindow().getScaledWidth()),
				configuredY(client.getWindow().getScaledHeight()), scale, false);
	}

	public static void renderPreview(DrawContext context, int x, int y, float scale) {
		renderHudAt(context, x, y, scale, true);
	}

	private static void renderHudAt(DrawContext context, int x, int y, float scale, boolean preview) {
		MinecraftClient client = MinecraftClient.getInstance();
		String value = "§6[自动消息] §7下次发送：§e" + (preview ? 350 : secondsUntilNextSend()) + " 秒";
		Text text = Text.literal(value);
		int textWidth = client.textRenderer.getWidth(text);
		context.getMatrices().pushMatrix();
		context.getMatrices().scale(scale, scale);
		int sx = Math.round(x / scale);
		int sy = Math.round(y / scale);
		context.fill(sx, sy, sx + textWidth + 10, sy + HUD_HEIGHT, 0xB0000000);
		context.drawBorder(sx, sy, textWidth + 10, HUD_HEIGHT, 0x80555555);
		context.drawTextWithShadow(client.textRenderer, text, sx + 5, sy + 3, 0xFFFFFFFF);
		context.getMatrices().popMatrix();
	}

	public static int previewWidth() {
		MinecraftClient client = MinecraftClient.getInstance();
		return client.textRenderer.getWidth(Text.literal("[自动消息] 下次发送：350 秒")) + 10;
	}

	public static int previewHeight() { return HUD_HEIGHT; }

	public static int configuredX(int screenWidth) {
		ArcaneHudConfig hud = ArcaneCooldownHud.config();
		float scale = hud.autoMessageScalePercent / 100.0f;
		return hud.autoMessageX < 0
				? (screenWidth - Math.round(previewWidth() * scale)) / 2
				: (int)Math.round(hud.autoMessageX * screenWidth);
	}

	public static int configuredY(int screenHeight) {
		ArcaneHudConfig hud = ArcaneCooldownHud.config();
		return hud.autoMessageY < 0 ? screenHeight - 49 : (int)Math.round(hud.autoMessageY * screenHeight);
	}

	private static void send(MinecraftClient client) {
		if (client.getNetworkHandler() == null || config.message == null || config.message.isBlank()) return;
		if (config.message.startsWith("/")) {
			client.getNetworkHandler().sendChatCommand(config.message.substring(1));
		} else {
			client.getNetworkHandler().sendChatMessage(config.message);
		}
	}

	private static void scheduleNext() {
		config.nextSendAtEpochMillis = System.currentTimeMillis() + config.intervalSeconds * 1000L;
		config.save();
	}

	public static AutoMessageConfig config() { return config; }

	public static void saveSettings(String message, int intervalSeconds) {
		config.message = message;
		config.intervalSeconds = intervalSeconds;
		if (config.enabled) scheduleNext(); else config.save();
	}

	public static void setEnabled(boolean enabled) {
		config.enabled = enabled;
		if (enabled) {
			scheduleNext();
			LOGGER.info("Automatic messaging enabled; next send in {} seconds", config.intervalSeconds);
		} else {
			config.nextSendAtEpochMillis = 0L;
			config.save();
			LOGGER.info("Automatic messaging disabled");
		}
	}

	public static boolean isEnabled() { return config != null && config.enabled; }

	public static long secondsUntilNextSend() {
		if (config == null || config.nextSendAtEpochMillis <= 0L) return config == null ? 0 : config.intervalSeconds;
		long remaining = Math.max(0L, config.nextSendAtEpochMillis - System.currentTimeMillis());
		return Math.max(1L, (remaining + 999L) / 1000L);
	}
}

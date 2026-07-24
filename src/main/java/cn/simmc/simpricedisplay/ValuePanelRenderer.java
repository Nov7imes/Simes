package cn.simmc.simpricedisplay;

import cn.simmc.simpricedisplay.InventoryValueCalculator.ValueSummary;
import cn.simmc.simpricedisplay.market.MarketDataManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.text.Text;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ValuePanelRenderer {
	private static final ThreadLocal<DecimalFormat> MONEY = ThreadLocal.withInitial(() ->
			new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.US)));
	private static final int PANEL_WIDTH = 126;
	private static final int INVENTORY_PREVIEW_HEIGHT = 30;
	private static final int CONTAINER_PREVIEW_HEIGHT = 63;
	private static final int CONTAINER_BACKPACK_PREVIEW_HEIGHT = 52;
	private static final long VALUE_CACHE_NANOS = 250_000_000L;
	private static Object cachedHandler;
	private static long cachedMarketRevision = Long.MIN_VALUE;
	private static long cacheExpiresAt;
	private static ValueSummary cachedPlayer;
	private static ValueSummary cachedContainer;

	private ValuePanelRenderer() {}

	public static void render(HandledScreen<?> screen, DrawContext context, int screenX, int screenY,
			int backgroundWidth, int backgroundHeight) {
		MarketDataManager dataManager = SimesClient.marketDataManager();
		MinecraftClient client = MinecraftClient.getInstance();
		boolean debugging = DebugRecorder.isRecording();
		String screenInfo = debugging ? "screen=" + screen.getClass().getName()
				+ " | handler=" + screen.getScreenHandler().getClass().getName() : "";
		boolean valueEnabled = ValuePanelController.enabled();
		boolean balanceEnabled = SimesConfig.get().balanceTrackingEnabled;
		if (!valueEnabled && !balanceEnabled) {
			if (debugging) DebugRecorder.recordValuePanel("SKIP valuePanel=false balanceTracking=false | " + screenInfo);
			return;
		}
		if (dataManager == null || !dataManager.isActiveOnTargetServer() || client.player == null) {
			if (debugging) DebugRecorder.recordValuePanel("SKIP prerequisites | " + screenInfo);
			return;
		}
		boolean playerInventoryScreen = screen instanceof InventoryScreen
				|| screen.getScreenHandler() instanceof PlayerScreenHandler;
		boolean containerScreen = screen instanceof GenericContainerScreen
				|| screen.getScreenHandler() instanceof GenericContainerScreenHandler;
		if (!playerInventoryScreen && !containerScreen) {
			if (debugging) DebugRecorder.recordValuePanel("SKIP unsupportedScreen | " + screenInfo);
			return;
		}

		refreshValueCache(screen, client, dataManager, containerScreen);
		if (playerInventoryScreen) {
			renderInventorySummary(context, client, screenX, screenY, backgroundWidth, backgroundHeight,
					cachedPlayer, dataManager.itemCount(), valueEnabled, balanceEnabled, screenInfo);
			return;
		}
		renderContainerSummaries(context, client, screenX, screenY, backgroundWidth,
				cachedContainer, cachedPlayer, dataManager, valueEnabled, balanceEnabled, screenInfo);
	}

	private static void renderContainerSummaries(DrawContext context, MinecraftClient client,
			int screenX, int screenY, int backgroundWidth, ValueSummary container, ValueSummary player,
			MarketDataManager dataManager, boolean valueEnabled, boolean balanceEnabled, String screenInfo) {
		List<Line> containerLines = List.of(
				new Line("仓库价值：$" + money(container.total()), 0xFF55FF55),
				new Line("总计：$" + money(container.total() + player.total()), 0xFFFFAA00),
				new Line("仓库已估价：" + container.valuedSlots() + "/" + container.occupiedSlots() + " 格", 0xFFAAAAAA),
				new Line("仓库未估价：" + container.unvaluedSlots() + " 格", 0xFFAAAAAA),
				new Line("数据更新：" + MarketTooltip.relativeTime(dataManager.dataUpdatedAt()), 0xFF777777));
		List<Line> backpackLines = new ArrayList<>();
		if (valueEnabled) backpackLines.add(new Line("随身背包：$" + money(player.total()), 0xFFFFFFFF));
		if (balanceEnabled && BalanceTracker.hasBalance()) {
			backpackLines.add(new Line("当前余额：" + BalanceTracker.currentText(), 0xFFFFFFFF));
			backpackLines.add(new Line("今日余额变化：" + BalanceTracker.todayChangeText(), BalanceTracker.todayChangeColor()));
		} else if (balanceEnabled) {
			backpackLines.add(new Line("收益记账：等待余额更新", 0xFFAAAAAA));
		}

		int containerHeight = 10 + containerLines.size() * 11;
		int backpackHeight = 10 + backpackLines.size() * 11;
		int screenWidth = client.getWindow().getScaledWidth();
		int screenHeight = client.getWindow().getScaledHeight();
		float containerScale = containerValueScale();
		float backpackScale = inventoryScale();
		int containerX = configuredContainerX(screenWidth, screenX + backgroundWidth + 8, containerScale);
		int containerY = configuredContainerY(screenHeight, Math.max(4, screenY), containerHeight, containerScale);
		int backpackX = configuredInventoryX(screenWidth, screenX + backgroundWidth + 8, backpackScale);
		int backpackFallbackY = valueEnabled
				? containerY + Math.round(containerHeight * containerScale) + 6
				: Math.max(4, screenY);
		int backpackY = configuredInventoryY(screenHeight,
				backpackFallbackY, backpackHeight, backpackScale);
		if (DebugRecorder.isRecording()) {
			DebugRecorder.recordValuePanel("RENDER container | value=" + money(container.total())
					+ " | valueEnabled=" + valueEnabled + " | balanceEnabled=" + balanceEnabled
					+ " | warehousePanel=" + containerX + "," + containerY
					+ " | backpackPanel=" + backpackX + "," + backpackY + " | " + screenInfo);
		}
		if (valueEnabled) {
			drawPanel(context, client, containerLines, containerX, containerY, PANEL_WIDTH, containerHeight, containerScale);
		}
		if (!backpackLines.isEmpty()) {
			drawPanel(context, client, backpackLines, backpackX, backpackY, PANEL_WIDTH, backpackHeight, backpackScale);
		}
	}

	private static void refreshValueCache(HandledScreen<?> screen, MinecraftClient client,
			MarketDataManager dataManager, boolean includeContainer) {
		long now = System.nanoTime();
		long revision = dataManager.snapshotRevision();
		Object handler = screen.getScreenHandler();
		boolean missingContainer = includeContainer && cachedContainer == null;
		if (handler == cachedHandler && revision == cachedMarketRevision && now < cacheExpiresAt && !missingContainer) return;
		cachedHandler = handler;
		cachedMarketRevision = revision;
		cacheExpiresAt = now + VALUE_CACHE_NANOS;
		cachedPlayer = InventoryValueCalculator.playerInventory(screen.getScreenHandler(), client.player.getInventory(), dataManager);
		cachedContainer = includeContainer
				? InventoryValueCalculator.containerInventory(screen.getScreenHandler(), client.player.getInventory(), dataManager)
				: null;
	}

	private static void renderInventorySummary(DrawContext context, MinecraftClient client,
			int screenX, int screenY, int backgroundWidth, int backgroundHeight, ValueSummary player,
			int marketItems, boolean valueEnabled, boolean balanceEnabled, String screenInfo) {
		List<Line> lines = new ArrayList<>();
		if (valueEnabled) lines.add(new Line("背包价值：$" + money(player.total()), 0xFF55FF55));
		if (balanceEnabled && BalanceTracker.hasBalance()) {
			lines.add(new Line("今日收益：" + BalanceTracker.todayChangeText(), BalanceTracker.todayChangeColor()));
		} else if (balanceEnabled) {
			lines.add(new Line("收益记账：等待余额更新", 0xFFAAAAAA));
		}
		if (lines.isEmpty()) return;
		int panelHeight = 10 + lines.size() * 11;
		float scale = inventoryScale();
		int panelX = configuredInventoryX(client.getWindow().getScaledWidth(),
				screenX + backgroundWidth - PANEL_WIDTH, scale);
		int panelY = configuredInventoryY(client.getWindow().getScaledHeight(),
				screenY + backgroundHeight + 4, panelHeight, scale);
		if (DebugRecorder.isRecording()) {
			DebugRecorder.recordValuePanel("RENDER inventory | marketItems=" + marketItems
					+ " | value=" + money(player.total()) + " | valueEnabled=" + valueEnabled
					+ " | balanceEnabled=" + balanceEnabled + " | panel=" + panelX + "," + panelY + " | " + screenInfo);
		}
		drawPanel(context, client, lines, panelX, panelY, PANEL_WIDTH, panelHeight, scale);
	}

	static void renderPreview(DrawContext context, int x, int y, float scale) {
		drawPanel(context, MinecraftClient.getInstance(), List.of(
				new Line("背包价值：$18,320.9", 0xFF55FF55),
				new Line("今日收益：+$10,000", 0xFF55FF55)),
				x, y, PANEL_WIDTH, INVENTORY_PREVIEW_HEIGHT, scale);
	}

	static void renderContainerPreview(DrawContext context, int x, int y, float scale) {
		drawPanel(context, MinecraftClient.getInstance(), List.of(
				new Line("仓库价值：$82,640", 0xFF55FF55),
				new Line("总计：$100,960.9", 0xFFFFAA00),
				new Line("仓库已估价：36/41 格", 0xFFAAAAAA),
				new Line("仓库未估价：5 格", 0xFFAAAAAA),
				new Line("数据更新：3分钟前", 0xFF777777)),
				x, y, PANEL_WIDTH, CONTAINER_PREVIEW_HEIGHT, scale);
	}

	static void renderContainerBackpackPreview(DrawContext context, int x, int y, float scale) {
		drawPanel(context, MinecraftClient.getInstance(), List.of(
				new Line("随身背包：$18,320.9", 0xFFFFFFFF),
				new Line("当前余额：$52,000", 0xFFFFFFFF),
				new Line("今日余额变化：+$10,000", 0xFF55FF55)),
				x, y, PANEL_WIDTH, CONTAINER_BACKPACK_PREVIEW_HEIGHT, scale);
	}

	static int previewWidth() { return PANEL_WIDTH; }
	static int previewHeight() { return INVENTORY_PREVIEW_HEIGHT; }
	static int containerPreviewHeight() { return CONTAINER_PREVIEW_HEIGHT; }
	static int containerBackpackPreviewHeight() { return CONTAINER_BACKPACK_PREVIEW_HEIGHT; }
	static float inventoryScale() { return ArcaneCooldownHud.config().valuePanelScalePercent / 100.0f; }
	static float valueScale() { return inventoryScale(); }
	static float containerValueScale() { return ArcaneCooldownHud.config().containerValueScalePercent / 100.0f; }
	static float containerBackpackScale() { return inventoryScale(); }

	static int configuredPreviewX(int screenWidth) {
		return configuredInventoryX(screenWidth, screenWidth - PANEL_WIDTH - 12, inventoryScale());
	}
	static int configuredPreviewY(int screenHeight) {
		return configuredInventoryY(screenHeight, Math.max(24, screenHeight / 2), INVENTORY_PREVIEW_HEIGHT, inventoryScale());
	}
	static int configuredContainerPreviewX(int screenWidth) {
		return configuredContainerX(screenWidth, screenWidth - PANEL_WIDTH - 12, containerValueScale());
	}
	static int configuredContainerPreviewY(int screenHeight) {
		return configuredContainerY(screenHeight, 36, CONTAINER_PREVIEW_HEIGHT, containerValueScale());
	}
	static int configuredContainerBackpackPreviewX(int screenWidth) {
		return configuredContainerBackpackX(screenWidth, screenWidth - PANEL_WIDTH - 12, containerBackpackScale());
	}
	static int configuredContainerBackpackPreviewY(int screenHeight) {
		int fallback = configuredContainerPreviewY(screenHeight)
				+ Math.round(CONTAINER_PREVIEW_HEIGHT * containerValueScale()) + 6;
		return configuredContainerBackpackY(screenHeight, fallback, CONTAINER_BACKPACK_PREVIEW_HEIGHT, containerBackpackScale());
	}

	private static int configuredInventoryX(int screenWidth, int fallback, float scale) {
		return configuredCoordinate(ArcaneCooldownHud.config().valuePanelX, screenWidth, fallback,
				screenWidth - Math.round(PANEL_WIDTH * scale) - 4);
	}
	private static int configuredInventoryY(int screenHeight, int fallback, int panelHeight, float scale) {
		return configuredCoordinate(ArcaneCooldownHud.config().valuePanelY, screenHeight, fallback,
				screenHeight - Math.round(panelHeight * scale) - 4);
	}
	private static int configuredContainerX(int screenWidth, int fallback, float scale) {
		return configuredCoordinate(ArcaneCooldownHud.config().containerValueX, screenWidth, fallback,
				screenWidth - Math.round(PANEL_WIDTH * scale) - 4);
	}
	private static int configuredContainerY(int screenHeight, int fallback, int panelHeight, float scale) {
		return configuredCoordinate(ArcaneCooldownHud.config().containerValueY, screenHeight, fallback,
				screenHeight - Math.round(panelHeight * scale) - 4);
	}
	private static int configuredContainerBackpackX(int screenWidth, int fallback, float scale) {
		return configuredCoordinate(ArcaneCooldownHud.config().containerBackpackX, screenWidth, fallback,
				screenWidth - Math.round(PANEL_WIDTH * scale) - 4);
	}
	private static int configuredContainerBackpackY(int screenHeight, int fallback, int panelHeight, float scale) {
		return configuredCoordinate(ArcaneCooldownHud.config().containerBackpackY, screenHeight, fallback,
				screenHeight - Math.round(panelHeight * scale) - 4);
	}
	private static int configuredCoordinate(double configured, int dimension, int fallback, int max) {
		int value = configured < 0 ? fallback : (int)Math.round(configured * dimension);
		return Math.max(4, Math.min(value, Math.max(4, max)));
	}

	private static void drawPanel(DrawContext context, MinecraftClient client, List<Line> lines,
			int x, int y, int width, int height, float scale) {
		context.getMatrices().pushMatrix();
		context.getMatrices().scale(scale, scale);
		int sx = Math.round(x / scale);
		int sy = Math.round(y / scale);
		context.fill(sx, sy, sx + width, sy + height, 0xD0101010);
		context.drawBorder(sx, sy, width, height, 0xFF555555);
		int textY = sy + 5;
		for (Line line : lines) {
			context.drawTextWithShadow(client.textRenderer, Text.literal(line.text()), sx + 6, textY, line.color());
			textY += 11;
		}
		context.getMatrices().popMatrix();
	}

	private static String money(double value) { return MONEY.get().format(value); }
	private record Line(String text, int color) {}
}

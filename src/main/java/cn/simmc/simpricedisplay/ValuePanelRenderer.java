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

	private ValuePanelRenderer() {
	}

	public static void render(
			HandledScreen<?> screen,
			DrawContext context,
			int screenX,
			int screenY,
			int backgroundWidth,
			int backgroundHeight
	) {
		MarketDataManager dataManager = SimesClient.marketDataManager();
		MinecraftClient client = MinecraftClient.getInstance();
		String screenInfo = "screen=" + screen.getClass().getName()
				+ " | handler=" + screen.getScreenHandler().getClass().getName();
		if (!ValuePanelController.enabled()) {
			DebugRecorder.recordValuePanel("SKIP valuePanelEnabled=false | " + screenInfo);
			return;
		}
		if (dataManager == null) {
			DebugRecorder.recordValuePanel("SKIP dataManager=null | " + screenInfo);
			return;
		}
		if (!dataManager.isActiveOnTargetServer()) {
			DebugRecorder.recordValuePanel("SKIP targetServer=false | marketItems=" + dataManager.itemCount() + " | " + screenInfo);
			return;
		}
		if (client.player == null) {
			DebugRecorder.recordValuePanel("SKIP player=null | marketItems=" + dataManager.itemCount() + " | " + screenInfo);
			return;
		}
		// Some client mods replace the visual Screen class while retaining the vanilla
		// screen handler. Detect the underlying inventory type so those layouts work too.
		boolean playerInventoryScreen = screen instanceof InventoryScreen
				|| screen.getScreenHandler() instanceof PlayerScreenHandler;
		boolean containerScreen = screen instanceof GenericContainerScreen
				|| screen.getScreenHandler() instanceof GenericContainerScreenHandler;
		if (!playerInventoryScreen && !containerScreen) {
			DebugRecorder.recordValuePanel("SKIP unsupportedScreen | marketItems=" + dataManager.itemCount()
					+ " | enabled=true | targetServer=true | " + screenInfo);
			return;
		}

		ValueSummary player = InventoryValueCalculator.playerInventory(
				screen.getScreenHandler(), client.player.getInventory(), dataManager);
		if (playerInventoryScreen) {
			renderInventorySummary(context, client, screenX, screenY, backgroundWidth, backgroundHeight,
					player, dataManager.itemCount(), screenInfo);
			return;
		}
		List<Line> lines = new ArrayList<>();
		ValueSummary container = InventoryValueCalculator.containerInventory(
				screen.getScreenHandler(), client.player.getInventory(), dataManager);
		lines.add(new Line("仓库价值：$" + money(container.total()), 0xFF55FF55));
		lines.add(new Line("随身背包：$" + money(player.total()), 0xFFFFFFFF));
		lines.add(new Line("总计：$" + money(container.total() + player.total()), 0xFFFFAA00));
		lines.add(new Line("", 0xFFFFFFFF));
		lines.add(new Line("仓库已估价：" + container.valuedSlots() + "/" + container.occupiedSlots() + " 格", 0xFFAAAAAA));
		lines.add(new Line("仓库未估价：" + container.unvaluedSlots() + " 格", 0xFFAAAAAA));
		if (BalanceTracker.hasBalance()) {
			lines.add(new Line("", 0xFFFFFFFF));
			lines.add(new Line("当前余额：$" + BalanceTracker.currentText(), 0xFFFFFFFF));
			lines.add(new Line("今日余额变化：" + BalanceTracker.todayChangeText(), BalanceTracker.todayChangeColor()));
		}
		Instant updatedAt = dataManager.dataUpdatedAt();
		lines.add(new Line("数据更新：" + MarketTooltip.relativeTime(updatedAt), 0xFF777777));

		int panelHeight = 10 + lines.size() * 11;
		int scaledWidth = client.getWindow().getScaledWidth();
		int panelX = screenX + backgroundWidth + 8;
		panelX = Math.min(panelX, Math.max(4, scaledWidth - PANEL_WIDTH - 4));
		int panelY = Math.max(4, screenY);
		DebugRecorder.recordValuePanel("RENDER container | marketItems=" + dataManager.itemCount()
				+ " | value=" + money(container.total()) + " | panel=" + panelX + "," + panelY
				+ "," + PANEL_WIDTH + "," + panelHeight + " | " + screenInfo);
		context.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + panelHeight, 0xD0101010);
		context.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + 1, 0xFF555555);
		context.fill(panelX, panelY + panelHeight - 1, panelX + PANEL_WIDTH, panelY + panelHeight, 0xFF555555);
		int textY = panelY + 6;
		for (Line line : lines) {
			if (!line.text().isEmpty()) {
				context.drawTextWithShadow(client.textRenderer, Text.literal(line.text()), panelX + 6, textY, line.color());
			}
			textY += 11;
		}
	}

	private static void renderInventorySummary(
			DrawContext context,
			MinecraftClient client,
			int screenX,
			int screenY,
			int backgroundWidth,
			int backgroundHeight,
			ValueSummary player,
			int marketItems,
			String screenInfo
	) {
		int panelWidth = 126;
		int panelHeight = 30;
		int panelX = screenX + backgroundWidth - panelWidth;
		int desiredY = screenY + backgroundHeight + 4;
		int panelY = Math.min(desiredY, client.getWindow().getScaledHeight() - panelHeight - 4);
		panelX = Math.max(4, Math.min(panelX, client.getWindow().getScaledWidth() - panelWidth - 4));
		panelY = Math.max(4, panelY);
		DebugRecorder.recordValuePanel("RENDER inventory | marketItems=" + marketItems
				+ " | value=" + money(player.total()) + " | occupied=" + player.occupiedSlots()
				+ " | valued=" + player.valuedSlots() + " | balanceLoaded=" + BalanceTracker.hasBalance()
				+ " | panel=" + panelX + "," + panelY + "," + panelWidth + "," + panelHeight
				+ " | gui=" + screenX + "," + screenY + "," + backgroundWidth + "," + backgroundHeight
				+ " | " + screenInfo);
		context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xD0101010);
		context.drawBorder(panelX, panelY, panelWidth, panelHeight, 0xFF555555);
		context.drawTextWithShadow(client.textRenderer, Text.literal("背包价值：$" + money(player.total())),
				panelX + 6, panelY + 5, 0xFF55FF55);
		context.drawTextWithShadow(client.textRenderer, Text.literal("今日收益：" + BalanceTracker.todayChangeText()),
				panelX + 6, panelY + 16, BalanceTracker.todayChangeColor());
	}

	private static String money(double value) {
		return MONEY.get().format(value);
	}

	private record Line(String text, int color) {
	}
}

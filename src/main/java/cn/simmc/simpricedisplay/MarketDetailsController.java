package cn.simmc.simpricedisplay;

import cn.simmc.simpricedisplay.market.MarketDetails.DetailedOffer;
import cn.simmc.simpricedisplay.market.MarketDetails.ItemDetails;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MarketDetailsController {
	private static final DecimalFormat MONEY = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.US));
	private static final int PANEL_WIDTH = 356;
	private static final int PANEL_HEIGHT = 151;
	private static final int ROW_HEIGHT = 20;
	private static Object lockedHandler;
	private static String lockedName;
	private static final List<HitRow> hitRows = new ArrayList<>();
	private static HitRow hovered;
	private static KeyBinding detailsKey;

	private MarketDetailsController() {}

	public static void register() {
		detailsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.simes.market_details", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_CONTROL, "category.simes"));
	}

	public static Text boundKeyText() {
		return detailsKey == null ? Text.literal("Ctrl") : detailsKey.getBoundKeyLocalizedText();
	}

	public static KeyBinding keyBinding() { return detailsKey; }

	public static boolean isLocked() { return lockedName != null; }

	public static boolean handleKey(HandledScreen<?> screen, Slot focusedSlot, int keyCode, int scanCode) {
		if (!SimesConfig.get().marketTooltipEnabled) return false;
		if (keyCode == GLFW.GLFW_KEY_ESCAPE && lockedName != null) { unlock(); return true; }
		if (detailsKey != null && detailsKey.matchesKey(keyCode, scanCode)) {
			if (focusedSlot != null && focusedSlot.hasStack()) {
				String name = Formatting.strip(focusedSlot.getStack().getName().getString());
				if (name != null && !name.isBlank()) {
					if (lockedHandler == screen.getScreenHandler() && name.equals(lockedName)) unlock();
					else { lockedHandler = screen.getScreenHandler(); lockedName = name; }
					return true;
				}
			}
			if (lockedName != null) { unlock(); return true; }
		}
		if (hovered != null && ((hovered.offer.side() == cn.simmc.simpricedisplay.market.MarketDetails.Side.SELL
				&& CoordinateCopyController.matchesSellKey(keyCode, scanCode))
				|| (hovered.offer.side() == cn.simmc.simpricedisplay.market.MarketDetails.Side.BUY
				&& CoordinateCopyController.matchesBuyKey(keyCode, scanCode)))) {
			return CoordinateCopyController.copyDetailedOffer(hovered.offer);
		}
		return false;
	}

	public static boolean handleMouse(double mouseX, double mouseY, int button) {
		if (lockedName == null) return false;
		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && hovered != null) {
			CoordinateCopyController.copyDetailedOffer(hovered.offer);
		}
		// While the details panel is locked, consume every mouse click so slots
		// underneath the overlay cannot be picked up, moved or quick-clicked.
		return true;
	}

	public static void render(HandledScreen<?> screen, DrawContext context, int mouseX, int mouseY) {
		if (lockedName == null) return;
		if (!SimesConfig.get().marketTooltipEnabled) { unlock(); return; }
		if (lockedHandler != screen.getScreenHandler()) { unlock(); return; }
		var manager = SimesClient.marketDataManager();
		if (manager == null || !manager.isActiveOnTargetServer()) { unlock(); return; }
		ItemDetails details = manager.details(lockedName).orElse(null);
		MinecraftClient client = MinecraftClient.getInstance();
		int width = client.getWindow().getScaledWidth();
		int height = client.getWindow().getScaledHeight();
		int x = Math.max(4, (width - PANEL_WIDTH) / 2);
		int y = Math.max(4, Math.min((height - PANEL_HEIGHT) / 2, height - PANEL_HEIGHT - 4));
		context.fill(x, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, 0xF0101010);
		context.drawBorder(x, y, PANEL_WIDTH, PANEL_HEIGHT, 0xFF777777);
		context.drawTextWithShadow(client.textRenderer, Text.literal("🔒 " + trim(lockedName, 30)), x + 8, y + 7, 0xFFFFFFFF);
		context.drawTextWithShadow(client.textRenderer, Text.literal("再次 " + boundKeyText().getString() + " / Esc 关闭"), x + PANEL_WIDTH - 112, y + 7, 0xFF888888);
		context.drawTextWithShadow(client.textRenderer, Text.literal("最低出售"), x + 8, y + 22, 0xFF55FF55);
		context.drawTextWithShadow(client.textRenderer, Text.literal("最高收购"), x + 182, y + 22, 0xFFFFAA00);
		hitRows.clear();
		hovered = null;
		if (details == null) {
			context.drawTextWithShadow(client.textRenderer, Text.literal("未找到 Simall 报价"), x + 8, y + 48, 0xFFFF5555);
			return;
		}
		drawOffers(context, client, details.sells(), x + 6, y + 35, 166, mouseX, mouseY);
		drawOffers(context, client, details.buys(), x + 180, y + 35, 170, mouseX, mouseY);
		String freshness = "最佳价 " + relative(details.indexUpdatedAt()) + " · 全量 " + relative(details.fullUpdatedAt());
		context.drawTextWithShadow(client.textRenderer, Text.literal(freshness), x + 8, y + 137, 0xFF888888);
	}

	private static void drawOffers(DrawContext context, MinecraftClient client, List<DetailedOffer> offers,
			int x, int y, int width, int mouseX, int mouseY) {
		if (offers.isEmpty()) {
			context.drawTextWithShadow(client.textRenderer, Text.literal("暂无报价"), x + 3, y + 5, 0xFF777777);
			return;
		}
		for (int i = 0; i < offers.size(); i++) {
			DetailedOffer offer = offers.get(i);
			int rowY = y + i * ROW_HEIGHT;
			boolean over = mouseX >= x && mouseX < x + width && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
			if (over) context.fill(x, rowY, x + width, rowY + ROW_HEIGHT - 1, 0x80555555);
			String prefix = offer.latestIndex() ? "★" : Integer.toString(i + 1);
			String first = prefix + " $" + MONEY.format(offer.price()) + "  " + trim(offer.owner(), 12);
			String second = trim(offer.port(), 8) + " 库存" + offer.amount() + "  " + coordinate(offer);
			context.drawTextWithShadow(client.textRenderer, Text.literal(first), x + 3, rowY + 2,
					offer.latestIndex() ? 0xFF55FFFF : 0xFFFFFFFF);
			context.drawTextWithShadow(client.textRenderer, Text.literal(second), x + 3, rowY + 11, 0xFFAAAAAA);
			HitRow hit = new HitRow(x, rowY, width, ROW_HEIGHT, offer);
			hitRows.add(hit);
			if (over) hovered = hit;
		}
	}

	private static String coordinate(DetailedOffer offer) {
		if (!offer.hasCoordinates()) return "无坐标";
		String y = Double.isFinite(offer.y()) ? "/" + (int)offer.y() : "";
		return (int)offer.x() + y + "/" + (int)offer.z();
	}

	private static String trim(String value, int maximum) {
		if (value == null) return "";
		int[] points = value.codePoints().toArray();
		return points.length <= maximum ? value : new String(points, 0, maximum - 1) + "…";
	}

	private static String relative(Instant value) {
		if (value == null || value.equals(Instant.EPOCH)) return "等待更新";
		return MarketTooltip.relativeTime(value);
	}

	private static void unlock() { lockedHandler = null; lockedName = null; hovered = null; hitRows.clear(); }
	private record HitRow(int x, int y, int width, int height, DetailedOffer offer) {}
}

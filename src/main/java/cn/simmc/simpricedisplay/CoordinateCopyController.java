package cn.simmc.simpricedisplay;

import cn.simmc.simpricedisplay.market.MarketDataManager;
import cn.simmc.simpricedisplay.market.MarketModels.MarketMatch;
import cn.simmc.simpricedisplay.market.MarketModels.Offer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class CoordinateCopyController {
	private static final DecimalFormat COORDINATE_FORMAT =
			new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.US));
	private static MarketDataManager dataManager;
	private static KeyBinding copySellKey;
	private static KeyBinding copyBuyKey;

	private CoordinateCopyController() {
	}

	public static void register(MarketDataManager manager) {
		dataManager = manager;
		copySellKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.simes.copy_sell", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_C, "category.simes"));
		copyBuyKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.simes.copy_buy", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, "category.simes"));
	}

	public static Text sellKeyText() {
		return copySellKey == null ? Text.literal("C") : copySellKey.getBoundKeyLocalizedText();
	}

	public static Text buyKeyText() {
		return copyBuyKey == null ? Text.literal("V") : copyBuyKey.getBoundKeyLocalizedText();
	}

	public static boolean handleKey(Slot hoveredSlot, int keyCode, int scanCode) {
		if (copySellKey != null && copySellKey.matchesKey(keyCode, scanCode)) {
			return copyOffer(hoveredSlot, true);
		}
		if (copyBuyKey != null && copyBuyKey.matchesKey(keyCode, scanCode)) {
			return copyOffer(hoveredSlot, false);
		}
		return false;
	}

	public static boolean handleMouse(Slot hoveredSlot, int button) {
		if (copySellKey != null && copySellKey.matchesMouse(button)) {
			return copyOffer(hoveredSlot, true);
		}
		if (copyBuyKey != null && copyBuyKey.matchesMouse(button)) {
			return copyOffer(hoveredSlot, false);
		}
		return false;
	}

	private static boolean copyOffer(Slot hoveredSlot, boolean sell) {
		if (hoveredSlot == null || !hoveredSlot.hasStack() || dataManager == null
				|| !dataManager.isActiveOnTargetServer()) {
			return false;
		}
		ItemStack stack = hoveredSlot.getStack();
		String visibleName = Formatting.strip(stack.getName().getString());
		MarketMatch match = visibleName == null ? null : dataManager.find(visibleName).orElse(null);
		if (match == null) {
			notifyPlayer(Text.literal("未找到此物品的 Simall 报价").formatted(Formatting.RED));
			return true;
		}
		Offer offer = sell ? match.data().lowestSell() : match.data().highestBuy();
		String direction = sell ? "出售" : "收购";
		if (offer == null) {
			notifyPlayer(Text.literal("此物品暂无" + direction + "报价").formatted(Formatting.YELLOW));
			return true;
		}
		if (!offer.hasCoordinates()) {
			notifyPlayer(Text.literal("该" + direction + "店铺暂无坐标数据").formatted(Formatting.YELLOW));
			return true;
		}
		String x = formatCoordinate(offer.x());
		String z = formatCoordinate(offer.z());
		MinecraftClient.getInstance().keyboard.setClipboard(x + " " + z);
		notifyPlayer(Text.literal("已复制" + direction + "坐标：X " + x + "，Z " + z)
				.formatted(Formatting.GREEN));
		return true;
	}

	private static String formatCoordinate(double coordinate) {
		synchronized (COORDINATE_FORMAT) {
			return COORDINATE_FORMAT.format(coordinate);
		}
	}

	private static void notifyPlayer(Text message) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player != null) {
			client.player.sendMessage(message, true);
		}
	}
}

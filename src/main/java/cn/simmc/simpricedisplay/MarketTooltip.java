package cn.simmc.simpricedisplay;

import cn.simmc.simpricedisplay.market.MarketDataManager;
import cn.simmc.simpricedisplay.market.MarketModels.ItemMarketData;
import cn.simmc.simpricedisplay.market.MarketModels.MarketMatch;
import cn.simmc.simpricedisplay.market.MarketModels.Offer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

public final class MarketTooltip {
	private static final ThreadLocal<DecimalFormat> PRICE_FORMAT = ThreadLocal.withInitial(() ->
			new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.US))
	);

	private MarketTooltip() {
	}

	public static void register(MarketDataManager dataManager) {
		ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
			if (!SimesConfig.get().marketTooltipEnabled || !dataManager.isActiveOnTargetServer() || stack.isEmpty()) {
				return;
			}

			String visibleName = Formatting.strip(stack.getName().getString());
			if (visibleName == null || visibleName.isBlank()) {
				return;
			}

			MarketMatch match = dataManager.find(visibleName).orElse(null);
			if (match == null) {
				return;
			}

			ItemMarketData market = match.data();
			lines.add(Text.empty());
			lines.add(Text.literal("── Simes · Simall ──").formatted(Formatting.DARK_GRAY));
			lines.add(offerLine("最低出售：", market.lowestSell(), "出售店铺：", Formatting.GREEN));
			lines.add(coordinateLine("出售坐标：", market.lowestSell(), CoordinateCopyController.sellKeyText()));
			lines.add(offerLine("最高收购：", market.highestBuy(), "收购店铺：", Formatting.GOLD));
			lines.add(coordinateLine("收购坐标：", market.highestBuy(), CoordinateCopyController.buyKeyText()));
			if (match.fuzzy()) {
				lines.add(Text.literal("匹配物品：" + market.displayName()).formatted(Formatting.DARK_GRAY));
			}
			lines.add(Text.literal("数据更新：" + relativeTime(dataManager.dataUpdatedAt()))
					.formatted(Formatting.GRAY));
			lines.add(Text.literal("数据来源：Simall").formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
		});
	}

	private static Text offerLine(String priceLabel, Offer offer, String ownerLabel, Formatting priceColor) {
		MutableText line = Text.literal(priceLabel).formatted(Formatting.GRAY);
		if (offer == null) {
			return line.append(Text.literal("暂无").formatted(Formatting.DARK_GRAY));
		}

		line.append(Text.literal("$" + PRICE_FORMAT.get().format(offer.price())).formatted(priceColor));
		line.append(Text.literal("  " + ownerLabel).formatted(Formatting.GRAY));
		line.append(Text.literal(offer.owner()).formatted(Formatting.WHITE));
		line.append(Text.literal("  港口：").formatted(Formatting.GRAY));
		line.append(Text.literal(offer.port()).formatted(Formatting.AQUA));
		line.append(Text.literal("  库存：").formatted(Formatting.GRAY));
		line.append(Text.literal(Long.toString(offer.amount())).formatted(Formatting.WHITE));
		return line;
	}

	private static Text coordinateLine(String label, Offer offer, Text keyText) {
		MutableText line = Text.literal(label).formatted(Formatting.GRAY);
		if (offer == null) {
			return line.append(Text.literal("暂无").formatted(Formatting.DARK_GRAY));
		}
		if (!offer.hasCoordinates()) {
			return line.append(Text.literal("等待 API 提供").formatted(Formatting.DARK_GRAY));
		}
		line.append(Text.literal(formatCoordinate(offer.x()) + " " + formatCoordinate(offer.z()))
				.formatted(Formatting.AQUA));
		line.append(Text.literal("（").formatted(Formatting.DARK_GRAY));
		line.append(keyText.copy().formatted(Formatting.YELLOW));
		line.append(Text.literal(" 复制）").formatted(Formatting.DARK_GRAY));
		return line;
	}

	private static String formatCoordinate(double coordinate) {
		if (coordinate == Math.rint(coordinate)) {
			return Long.toString((long) coordinate);
		}
		return PRICE_FORMAT.get().format(coordinate);
	}

	static String relativeTime(Instant updatedAt) {
		if (updatedAt == null || updatedAt.equals(Instant.EPOCH)) {
			return "未知";
		}
		long seconds = Math.max(0, Duration.between(updatedAt, Instant.now()).getSeconds());
		if (seconds < 60) {
			return "刚刚";
		}
		long minutes = seconds / 60;
		if (minutes < 60) {
			return minutes + "分钟前";
		}
		long hours = minutes / 60;
		if (hours < 24) {
			return hours + "小时前";
		}
		return hours / 24 + "天前";
	}
}

package cn.simmc.simpricedisplay;

import cn.simmc.simpricedisplay.market.MarketDataManager;
import cn.simmc.simpricedisplay.market.MarketModels.MarketMatch;
import cn.simmc.simpricedisplay.market.MarketModels.Offer;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.Formatting;

public final class InventoryValueCalculator {
	private InventoryValueCalculator() {
	}

	public static ValueSummary playerInventory(
			ScreenHandler handler,
			PlayerInventory playerInventory,
			MarketDataManager dataManager
	) {
		return calculate(handler, playerInventory, dataManager, true);
	}

	public static ValueSummary containerInventory(
			ScreenHandler handler,
			PlayerInventory playerInventory,
			MarketDataManager dataManager
	) {
		return calculate(handler, playerInventory, dataManager, false);
	}

	private static ValueSummary calculate(
			ScreenHandler handler,
			PlayerInventory playerInventory,
			MarketDataManager dataManager,
			boolean playerSlots
	) {
		double total = 0.0;
		int occupiedSlots = 0;
		int valuedSlots = 0;
		int unvaluedSlots = 0;

		for (Slot slot : handler.slots) {
			if ((slot.inventory == playerInventory) != playerSlots) {
				continue;
			}
			ItemStack stack = slot.getStack();
			if (stack.isEmpty()) {
				continue;
			}
			occupiedSlots++;
			String visibleName = Formatting.strip(stack.getName().getString());
			MarketMatch match = visibleName == null ? null : dataManager.find(visibleName).orElse(null);
			Offer buy = match == null ? null : match.data().highestBuy();
			if (buy == null || !Double.isFinite(buy.price()) || buy.price() <= 0) {
				unvaluedSlots++;
				continue;
			}
			total += buy.price() * stack.getCount();
			valuedSlots++;
		}
		return new ValueSummary(total, occupiedSlots, valuedSlots, unvaluedSlots);
	}

	public record ValueSummary(double total, int occupiedSlots, int valuedSlots, int unvaluedSlots) {
		public ValueSummary plus(ValueSummary other) {
			return new ValueSummary(
					total + other.total,
					occupiedSlots + other.occupiedSlots,
					valuedSlots + other.valuedSlots,
					unvaluedSlots + other.unvaluedSlots
			);
		}
	}
}

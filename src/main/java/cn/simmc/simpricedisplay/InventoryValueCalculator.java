package cn.simmc.simpricedisplay;

import cn.simmc.simpricedisplay.market.MarketDataManager;
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
			Double estimatedPrice = visibleName == null ? null : dataManager.estimatedBuyPrice(visibleName).orElse(null);
			if (estimatedPrice == null || !Double.isFinite(estimatedPrice) || estimatedPrice <= 0) {
				unvaluedSlots++;
				continue;
			}
			total += estimatedPrice * stack.getCount();
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

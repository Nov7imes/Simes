package cn.simmc.simpricedisplay;

import cn.simmc.simpricedisplay.InventoryValueCalculator.ValueSummary;
import cn.simmc.simpricedisplay.market.MarketDataManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.BlockState;
import net.minecraft.block.BarrelBlock;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.enums.ChestType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public final class ContainerCalculationTracker {
	private static final long LOCATION_TIMEOUT_NANOS = 5_000_000_000L;
	private static final DecimalFormat MONEY =
			new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.US));
	private static final Set<String> recordedContainers = new HashSet<>();
	private static MarketDataManager dataManager;
	private static boolean recording;
	private static int recordedCount;
	private static double recordedTotal;
	private static ContainerLocation pendingLocation;
	private static long pendingLocationAt;
	private static HandledScreen<?> trackedScreen;
	private static ContainerLocation trackedLocation;
	private static boolean trackedScreenRecorded;

	private ContainerCalculationTracker() {
	}

	public static void register(MarketDataManager manager) {
		dataManager = manager;
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
				dispatcher.register(literal("cal").executes(context -> toggleRecording())));
		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			MinecraftClient client = MinecraftClient.getInstance();
			if (player == client.player) {
				ContainerLocation location = resolveChestLocation(world, hitResult.getBlockPos());
				if (location != null) {
					pendingLocation = location;
					pendingLocationAt = System.nanoTime();
				}
			}
			return ActionResult.PASS;
		});
		ClientTickEvents.END_CLIENT_TICK.register(ContainerCalculationTracker::tick);
	}

	private static int toggleRecording() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || dataManager == null || !dataManager.isActiveOnTargetServer()) {
			localMessage(client, "§c[Simes] 仅在 play.simmc.cn 中可使用箱子估值记录");
			return 0;
		}
		if (!recording) {
			recording = true;
			recordedContainers.clear();
			recordedCount = 0;
			recordedTotal = 0.0;
			trackedScreenRecorded = false;
			localMessage(client, "§a[Simes] 已开始记录箱子价值，再次输入 /cal 结束");
			return 1;
		}

		recordTrackedScreen(client);
		recording = false;
		localMessage(client, "§6[Simes] 记录结束");
		localMessage(client, "§7箱子数量：§f" + recordedCount);
		localMessage(client, "§7总价值：§a$" + money(recordedTotal));
		recordedContainers.clear();
		trackedScreenRecorded = true;
		return 1;
	}

	private static void tick(MinecraftClient client) {
		HandledScreen<?> current = client.currentScreen instanceof GenericContainerScreen
				? (HandledScreen<?>)client.currentScreen
				: null;
		if (current == trackedScreen) {
			return;
		}
		recordTrackedScreen(client);
		trackedScreen = current;
		trackedScreenRecorded = false;
		trackedLocation = current == null ? null : consumePendingLocation();
	}

	private static ContainerLocation consumePendingLocation() {
		if (pendingLocation == null || System.nanoTime() - pendingLocationAt > LOCATION_TIMEOUT_NANOS) {
			pendingLocation = null;
			return null;
		}
		ContainerLocation location = pendingLocation;
		pendingLocation = null;
		return location;
	}

	private static void recordTrackedScreen(MinecraftClient client) {
		if (!recording || trackedScreen == null || trackedScreenRecorded || client.player == null) {
			return;
		}
		trackedScreenRecorded = true;
		if (trackedLocation == null) {
			localMessage(client, "§e[Simes] 无法识别箱子坐标，本次跳过");
			return;
		}
		if (!recordedContainers.add(trackedLocation.key())) {
			localMessage(client, "§e[Simes] 该箱子已经记录，本次跳过");
			localMessage(client, coordinateText(trackedLocation.displayPos()));
			return;
		}

		ValueSummary value = InventoryValueCalculator.containerInventory(
				trackedScreen.getScreenHandler(), client.player.getInventory(), dataManager);
		recordedCount++;
		recordedTotal += value.total();
		localMessage(client, "§a[Simes] 已记录箱子 #" + recordedCount);
		localMessage(client, coordinateText(trackedLocation.displayPos()));
		localMessage(client, "§7箱子价值：§a$" + money(value.total())
				+ " §8| §7当前累计：§6$" + money(recordedTotal));
	}

	private static ContainerLocation resolveChestLocation(World world, BlockPos clickedPos) {
		BlockState state = world.getBlockState(clickedPos);
		String dimension = world.getRegistryKey().getValue().toString();
		if (state.getBlock() instanceof BarrelBlock) {
			return new ContainerLocation(dimension + "|barrel|" + positionKey(clickedPos), clickedPos.toImmutable());
		}
		if (!(state.getBlock() instanceof ChestBlock) || !state.contains(ChestBlock.CHEST_TYPE)) {
			return null;
		}
		ChestType type = state.get(ChestBlock.CHEST_TYPE);
		if (type == ChestType.SINGLE) {
			return new ContainerLocation(dimension + "|" + positionKey(clickedPos), clickedPos.toImmutable());
		}

		BlockPos partner = null;
		for (Direction direction : Direction.Type.HORIZONTAL) {
			BlockPos candidatePos = clickedPos.offset(direction);
			BlockState candidate = world.getBlockState(candidatePos);
			if (candidate.getBlock() != state.getBlock()
					|| !candidate.contains(ChestBlock.CHEST_TYPE)
					|| !candidate.contains(ChestBlock.FACING)) {
				continue;
			}
			ChestType candidateType = candidate.get(ChestBlock.CHEST_TYPE);
			if (candidateType != ChestType.SINGLE
					&& candidateType != type
					&& candidate.get(ChestBlock.FACING) == state.get(ChestBlock.FACING)) {
				partner = candidatePos.toImmutable();
				break;
			}
		}
		if (partner == null) {
			return new ContainerLocation(dimension + "|" + positionKey(clickedPos), clickedPos.toImmutable());
		}
		BlockPos first = StreamlessPositions.min(clickedPos, partner);
		BlockPos second = first.equals(clickedPos) ? partner : clickedPos.toImmutable();
		return new ContainerLocation(
				dimension + "|" + positionKey(first) + "|" + positionKey(second),
				first
		);
	}

	private static String coordinateText(BlockPos pos) {
		return "§7坐标：§fX " + pos.getX() + "，Y " + pos.getY() + "，Z " + pos.getZ();
	}

	private static String positionKey(BlockPos pos) {
		return pos.getX() + "," + pos.getY() + "," + pos.getZ();
	}

	private static String money(double value) {
		synchronized (MONEY) {
			return MONEY.format(value);
		}
	}

	private static void localMessage(MinecraftClient client, String message) {
		if (client.player != null) {
			client.player.sendMessage(Text.literal(message), false);
		}
	}

	private record ContainerLocation(String key, BlockPos displayPos) {
	}

	private static final class StreamlessPositions {
		private static final Comparator<BlockPos> ORDER = Comparator
				.comparingInt(BlockPos::getX)
				.thenComparingInt(BlockPos::getY)
				.thenComparingInt(BlockPos::getZ);

		private static BlockPos min(BlockPos left, BlockPos right) {
			return ORDER.compare(left, right) <= 0 ? left.toImmutable() : right.toImmutable();
		}
	}
}

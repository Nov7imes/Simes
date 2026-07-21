package cn.simmc.simpricedisplay;

import cn.simmc.simpricedisplay.market.MarketDataManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SimesClient implements ClientModInitializer {
	public static final String MOD_ID = "simes";
	public static final Logger LOGGER = LoggerFactory.getLogger("Simes");
	private static MarketDataManager dataManager;

	@Override
	public void onInitializeClient() {
		dataManager = new MarketDataManager();
		dataManager.initialize();
		MarketTooltip.register(dataManager);
		CoordinateCopyController.register(dataManager);
		ContainerCalculationTracker.register(dataManager);
		ValuePanelController.register();
		BalanceTracker.register();
		DebugRecorder.register();
		ArcaneCooldownHud.register();

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			boolean targetServer = ServerGate.isTarget(client.getCurrentServerEntry());
			dataManager.setActiveOnTargetServer(targetServer);
			BalanceTracker.onJoin(targetServer);
			if (targetServer) {
				LOGGER.info("Connected to {}; Simall market tools enabled", ServerGate.TARGET_HOST);
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			dataManager.setActiveOnTargetServer(false);
			BalanceTracker.onDisconnect();
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> dataManager.close());
	}

	public static MarketDataManager marketDataManager() {
		return dataManager;
	}
}

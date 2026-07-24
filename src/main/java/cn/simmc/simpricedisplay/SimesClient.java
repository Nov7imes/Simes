package cn.simmc.simpricedisplay;

import cn.simmc.simpricedisplay.market.MarketDataManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.text.Text;
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
		MarketDetailsController.register();
		CoordinateCopyController.register(dataManager);
		ContainerCalculationTracker.register(dataManager);
		ValuePanelController.register();
		BalanceTracker.register();
		DebugRecorder.register();
		ManaHud.register();
		ArcaneCooldownHud.register();
		ArcaneStatusHud.register();
		AutoMessageModule.register();
		CookwareAssistant.register();
		for (String warning : SimesDiagnostics.compatibilityWarnings()) {
			LOGGER.warn("Compatibility check: {}", warning);
		}

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			boolean targetServer = ServerGate.isTarget(client.getCurrentServerEntry());
			dataManager.setActiveOnTargetServer(targetServer);
			BalanceTracker.onJoin(targetServer);
			if (targetServer) {
				LOGGER.info("Connected to {}; Simall market tools enabled", ServerGate.TARGET_HOST);
				if (client.player != null && !SimesDiagnostics.compatibilityWarnings().isEmpty()) {
					client.player.sendMessage(Text.literal("§e[Simes] 检测到兼容性风险，可输入 /simes diagnose 生成诊断包"), false);
				}
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			dataManager.setActiveOnTargetServer(false);
			BalanceTracker.onDisconnect();
			ArcaneStatusHud.reset();
			CookwareAssistant.reset();
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> dataManager.close());
	}

	public static MarketDataManager marketDataManager() {
		return dataManager;
	}
}

package cn.simmc.simpricedisplay;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ArcaneHudConfig {
	private static final int CURRENT_CONFIG_VERSION = 1;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("simes").resolve("hud.json");
	public int configVersion = CURRENT_CONFIG_VERSION;
	public boolean simesMode;
	public boolean arcaneEnabled = true;
	public boolean arcaneStatusEnabled = true;
	public boolean hideRecognizedArcaneBossBars = true;
	public double x = -1.0;
	public double y = -1.0;
	public int scalePercent = 100;
	public double autoMessageX = -1.0;
	public double autoMessageY = -1.0;
	public int autoMessageScalePercent = 100;
	public double valuePanelX = -1.0;
	public double valuePanelY = -1.0;
	public int valuePanelScalePercent = 100;
	public double containerValueX = -1.0;
	public double containerValueY = -1.0;
	public int containerValueScalePercent = 100;
	public double containerBackpackX = -1.0;
	public double containerBackpackY = -1.0;
	public int containerBackpackScalePercent = 100;
	public double arcaneStatusX = -1.0;
	public double arcaneStatusY = -1.0;
	public int arcaneStatusScalePercent = 100;
	public double globalCooldownX = -1.0;
	public double globalCooldownY = -1.0;
	public int globalCooldownScalePercent = 100;
	public boolean manaHudEnabled = true;
	public double manaHudX = -1.0;
	public double manaHudY = -1.0;
	public int manaHudScalePercent = 100;

	public static ArcaneHudConfig load() {
		if (!Files.isRegularFile(FILE)) return new ArcaneHudConfig();
		try (Reader reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
			ArcaneHudConfig config = GSON.fromJson(reader, ArcaneHudConfig.class);
			if (config == null) return new ArcaneHudConfig();
			boolean migrated = config.configVersion < CURRENT_CONFIG_VERSION;
			config.normalize();
			config.configVersion = CURRENT_CONFIG_VERSION;
			if (migrated) config.save();
			return config;
		} catch (Exception error) {
			SimesClient.LOGGER.warn("Failed to read HUD config", error);
			return new ArcaneHudConfig();
		}
	}

	private void normalize() {
		x = coordinate(x);
		y = coordinate(y);
		autoMessageX = coordinate(autoMessageX);
		autoMessageY = coordinate(autoMessageY);
		valuePanelX = coordinate(valuePanelX);
		valuePanelY = coordinate(valuePanelY);
		containerValueX = coordinate(containerValueX);
		containerValueY = coordinate(containerValueY);
		containerBackpackX = coordinate(containerBackpackX);
		containerBackpackY = coordinate(containerBackpackY);
		arcaneStatusX = coordinate(arcaneStatusX);
		arcaneStatusY = coordinate(arcaneStatusY);
		globalCooldownX = coordinate(globalCooldownX);
		globalCooldownY = coordinate(globalCooldownY);
		manaHudX = coordinate(manaHudX);
		manaHudY = coordinate(manaHudY);
		scalePercent = scale(scalePercent);
		autoMessageScalePercent = scale(autoMessageScalePercent);
		valuePanelScalePercent = scale(valuePanelScalePercent);
		containerValueScalePercent = scale(containerValueScalePercent);
		containerBackpackScalePercent = scale(containerBackpackScalePercent);
		arcaneStatusScalePercent = scale(arcaneStatusScalePercent);
		globalCooldownScalePercent = scale(globalCooldownScalePercent);
		manaHudScalePercent = scale(manaHudScalePercent);
	}

	private static double coordinate(double value) {
		return Double.isFinite(value) && value >= -1.0 && value <= 1.0 ? value : -1.0;
	}

	private static int scale(int value) {
		return Math.max(50, Math.min(200, value));
	}

	public void save() {
		try {
			Files.createDirectories(FILE.getParent());
			try (Writer writer = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException error) {
			SimesClient.LOGGER.error("Failed to save HUD config", error);
		}
	}

	public void resetPosition() {
		x = -1.0;
		y = -1.0;
	}

	public void resetAutoMessagePosition() {
		autoMessageX = -1.0;
		autoMessageY = -1.0;
	}

	public void resetValuePanelPosition() {
		valuePanelX = -1.0;
		valuePanelY = -1.0;
	}

	public void resetContainerValuePosition() {
		containerValueX = -1.0;
		containerValueY = -1.0;
	}

	public void resetContainerBackpackPosition() {
		containerBackpackX = -1.0;
		containerBackpackY = -1.0;
	}

	public void resetArcaneStatusPosition() {
		arcaneStatusX = -1.0;
		arcaneStatusY = -1.0;
	}

	public void resetGlobalCooldownPosition() {
		globalCooldownX = -1.0;
		globalCooldownY = -1.0;
	}

	public void resetManaHudPosition() {
		manaHudX = -1.0;
		manaHudY = -1.0;
	}

}

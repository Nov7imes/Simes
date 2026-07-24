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

public final class SimesConfig {
	private static final int CURRENT_CONFIG_VERSION = 1;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("simes").resolve("simes.json");
	private static SimesConfig instance;
	public int configVersion = CURRENT_CONFIG_VERSION;

	public boolean valuePanelEnabled = true;
	public boolean marketTooltipEnabled = true;
	public boolean coordinateCopyEnabled = true;
	public boolean containerCalculationEnabled = true;
	public boolean balanceTrackingEnabled = true;
	public boolean fermentationAssistantEnabled = true;
	public boolean cookwareAssistantEnabled = true;
	public String balanceDate = "";
	public String balanceBaseline = "";
	public String currentBalance = "";

	private SimesConfig() {}

	public static SimesConfig get() {
		if (instance == null) instance = load();
		return instance;
	}

	private static SimesConfig load() {
		if (!Files.isRegularFile(FILE)) return new SimesConfig();
		try (Reader reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
			SimesConfig config = GSON.fromJson(reader, SimesConfig.class);
			if (config == null) return new SimesConfig();
			boolean migrated = config.configVersion < CURRENT_CONFIG_VERSION;
			config.balanceDate = safe(config.balanceDate);
			config.balanceBaseline = safe(config.balanceBaseline);
			config.currentBalance = safe(config.currentBalance);
			config.configVersion = CURRENT_CONFIG_VERSION;
			if (migrated) config.save();
			return config;
		} catch (Exception error) {
			SimesClient.LOGGER.warn("Failed to read Simes config", error);
			return new SimesConfig();
		}
	}

	private static String safe(String value) {
		return value == null ? "" : value;
	}

	public void save() {
		try {
			Files.createDirectories(FILE.getParent());
			try (Writer writer = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException error) {
			SimesClient.LOGGER.error("Failed to save Simes config", error);
		}
	}

	public void toggle(String feature) {
		switch (feature) {
			case "market" -> marketTooltipEnabled = !marketTooltipEnabled;
			case "coordinates" -> coordinateCopyEnabled = !coordinateCopyEnabled;
			case "value" -> valuePanelEnabled = !valuePanelEnabled;
			case "containers" -> containerCalculationEnabled = !containerCalculationEnabled;
			case "balance" -> balanceTrackingEnabled = !balanceTrackingEnabled;
			case "fermentation" -> fermentationAssistantEnabled = !fermentationAssistantEnabled;
			case "cookware" -> cookwareAssistantEnabled = !cookwareAssistantEnabled;
			default -> throw new IllegalArgumentException("Unknown feature: " + feature);
		}
		save();
	}
}

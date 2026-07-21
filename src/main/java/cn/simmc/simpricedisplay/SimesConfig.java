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
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("simes").resolve("simes.json");
	private static SimesConfig instance;

	public boolean valuePanelEnabled = true;
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
			return config == null ? new SimesConfig() : config;
		} catch (Exception error) {
			SimesClient.LOGGER.warn("Failed to read Simes config", error);
			return new SimesConfig();
		}
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
}

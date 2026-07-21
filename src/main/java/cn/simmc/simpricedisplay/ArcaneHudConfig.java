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
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("simes").resolve("hud.json");
	public boolean simesMode;
	public double x = -1.0;
	public double y = -1.0;
	public int scalePercent = 100;
	public double autoMessageX = -1.0;
	public double autoMessageY = -1.0;
	public int autoMessageScalePercent = 100;

	public static ArcaneHudConfig load() {
		if (!Files.isRegularFile(FILE)) return new ArcaneHudConfig();
		try (Reader reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
			ArcaneHudConfig config = GSON.fromJson(reader, ArcaneHudConfig.class);
			return config == null ? new ArcaneHudConfig() : config;
		} catch (Exception error) {
			SimesClient.LOGGER.warn("Failed to read HUD config", error);
			return new ArcaneHudConfig();
		}
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
}

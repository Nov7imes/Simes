package cn.ni.automessage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import java.nio.charset.StandardCharsets;

public final class AutoMessageConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("simes").resolve("automessage.json");
	private static final Path LEGACY_PATH = FabricLoader.getInstance().getConfigDir().resolve("automessage.json");

	public String message = "这是一条自动消息";
	public int intervalSeconds = 60;
	public boolean enabled;
	public long nextSendAtEpochMillis;

	public static AutoMessageConfig load() {
		Path source = Files.exists(PATH) ? PATH : LEGACY_PATH;
		if (!Files.exists(source)) {
			return new AutoMessageConfig();
		}
		try (Reader reader = Files.newBufferedReader(source, StandardCharsets.UTF_8)) {
			AutoMessageConfig config = GSON.fromJson(reader, AutoMessageConfig.class);
			if (config == null) {
				return new AutoMessageConfig();
			}
			if (config.message == null || config.message.isBlank()) {
				config.message = "这是一条自动消息";
			}
			if (config.intervalSeconds < 1 || config.intervalSeconds > 86400) {
				config.intervalSeconds = 60;
			}
			if (!source.equals(PATH)) config.save();
			return config;
		} catch (Exception exception) {
			AutoMessageClient.LOGGER.error("读取配置失败，将使用默认配置", exception);
			return new AutoMessageConfig();
		}
	}

	public void save() {
		try {
			Files.createDirectories(PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(PATH, StandardCharsets.UTF_8)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException exception) {
			AutoMessageClient.LOGGER.error("保存配置失败", exception);
		}
	}
}

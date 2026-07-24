package cn.simmc.simpricedisplay;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Creates an intentionally bounded, locally stored and redacted support bundle. */
public final class SimesDiagnostics {
	private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
			.withZone(ZoneId.systemDefault());
	private static final Pattern UUID = Pattern.compile("(?i)\\b[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\\b");
	private static final Pattern IPV4 = Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}(?::\\d{1,5})?\\b");
	private static final int MAX_DEBUG_BYTES = 2 * 1024 * 1024;

	private SimesDiagnostics() {}

	public static int createCommand() {
		MinecraftClient client = MinecraftClient.getInstance();
		try {
			Path bundle = createBundle();
			message(client, "§a[Simes] 诊断包已生成：§f" + bundle.toAbsolutePath());
			message(client, "§7发送前仍建议自行确认压缩包内容；Simes 不会自动上传。");
			return 1;
		} catch (Exception error) {
			SimesClient.LOGGER.error("Failed to create diagnostic bundle", error);
			message(client, "§c[Simes] 诊断包生成失败，请查看 latest.log");
			return 0;
		}
	}

	static Path createBundle() throws IOException {
		Path simesDir = FabricLoader.getInstance().getConfigDir().resolve("simes");
		Path outputDir = simesDir.resolve("diagnostics");
		Files.createDirectories(outputDir);
		Path output = outputDir.resolve("simes-diagnostic-" + FILE_TIME.format(Instant.now()) + ".zip");
		try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(output), StandardCharsets.UTF_8)) {
			put(zip, "diagnostic.txt", diagnosticReport());
			putRedactedFile(zip, "config/simes.json", simesDir.resolve("simes.json"), Integer.MAX_VALUE);
			putRedactedFile(zip, "config/hud.json", simesDir.resolve("hud.json"), Integer.MAX_VALUE);
			Path latestDebug = latestFile(simesDir.resolve("debug"), "debug-", ".log");
			if (latestDebug != null) putRedactedFile(zip, "logs/latest-debug.log", latestDebug, MAX_DEBUG_BYTES);
		}
		return output;
	}

	static List<String> compatibilityWarnings() {
		FabricLoader loader = FabricLoader.getInstance();
		List<String> warnings = new ArrayList<>();
		if (!loader.isModLoaded("fabric-api")) warnings.add("未检测到 Fabric API");
		if (loader.isModLoaded("automessage")) warnings.add("检测到独立 AutoMessage，可能与 Simes 内置功能重复");
		if (loader.isModLoaded("simpricedisplay")) warnings.add("检测到旧 SimPriceDisplay，可能重复注入价格功能");
		if (Runtime.version().feature() != 21) warnings.add("当前 Java 主版本为 " + Runtime.version().feature() + "，目标版本为 21");
		return List.copyOf(warnings);
	}

	private static String diagnosticReport() {
		FabricLoader loader = FabricLoader.getInstance();
		StringBuilder report = new StringBuilder();
		report.append("Simes diagnostic bundle\n")
				.append("generatedAt=").append(Instant.now()).append('\n')
				.append("java=").append(Runtime.version()).append('\n')
				.append("os=").append(System.getProperty("os.name")).append(' ')
				.append(System.getProperty("os.version")).append(' ')
				.append(System.getProperty("os.arch")).append('\n');
		for (String warning : compatibilityWarnings()) report.append("warning=").append(warning).append('\n');
		report.append("mods:\n");
		loader.getAllMods().stream()
				.sorted(Comparator.comparing(mod -> mod.getMetadata().getId()))
				.forEach(mod -> report.append("- ").append(mod.getMetadata().getId()).append(' ')
						.append(mod.getMetadata().getVersion().getFriendlyString()).append('\n'));
		return redact(report.toString());
	}

	static String redact(String input) {
		if (input == null) return "";
		String result = input;
		String home = System.getProperty("user.home", "");
		if (!home.isBlank()) result = result.replace(home, "<USER_HOME>").replace(home.replace('\\', '/'), "<USER_HOME>");
		result = UUID.matcher(result).replaceAll("<UUID>");
		result = IPV4.matcher(result).replaceAll("<IP>");
		return result;
	}

	private static void putRedactedFile(ZipOutputStream zip, String entry, Path file, int limit) throws IOException {
		if (!Files.isRegularFile(file)) return;
		byte[] bytes = Files.readAllBytes(file);
		int start = Math.max(0, bytes.length - limit);
		put(zip, entry, redact(new String(bytes, start, bytes.length - start, StandardCharsets.UTF_8)));
	}

	private static void put(ZipOutputStream zip, String name, String value) throws IOException {
		zip.putNextEntry(new ZipEntry(name));
		zip.write(value.getBytes(StandardCharsets.UTF_8));
		zip.closeEntry();
	}

	private static Path latestFile(Path directory, String prefix, String suffix) throws IOException {
		if (!Files.isDirectory(directory)) return null;
		try (var files = Files.list(directory)) {
			return files.filter(Files::isRegularFile)
					.filter(path -> path.getFileName().toString().startsWith(prefix)
							&& path.getFileName().toString().endsWith(suffix))
					.max(Comparator.comparingLong(SimesDiagnostics::lastModified)).orElse(null);
		}
	}

	private static long lastModified(Path path) {
		try { return Files.getLastModifiedTime(path).toMillis(); }
		catch (IOException ignored) { return Long.MIN_VALUE; }
	}

	private static void message(MinecraftClient client, String value) {
		if (client.player != null) client.player.sendMessage(Text.literal(value), false);
	}
}

package cn.simmc.simpricedisplay.market;

import cn.simmc.simpricedisplay.market.MarketModels.ItemMarketData;
import cn.simmc.simpricedisplay.market.MarketModels.Offer;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;

public final class MarketParser {
	private static final int MAX_REASONABLE_ITEMS = 100_000;
	private static final ZoneId SIMALL_ZONE = ZoneId.of("Asia/Shanghai");
	private static final DateTimeFormatter SIMALL_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	public MarketSnapshot parse(Path file) throws IOException {
		try (Reader input = Files.newBufferedReader(file, StandardCharsets.UTF_8);
			 JsonReader reader = new JsonReader(input)) {
			return parse(reader, Files.getLastModifiedTime(file).toInstant());
		}
	}

	MarketSnapshot parse(JsonReader reader, Instant fallbackUpdateTime) throws IOException {
		Map<String, ItemMarketData> items = new HashMap<>();
		Instant updatedAt = Instant.EPOCH;

		if (reader.peek() != JsonToken.BEGIN_OBJECT) {
			throw new IOException("Simall price index root is not an object");
		}
		reader.beginObject();
		while (reader.hasNext()) {
			switch (reader.nextName()) {
				case "updated" -> updatedAt = parseUpdateTime(nextString(reader));
				case "items" -> readItems(reader, items);
				default -> reader.skipValue();
			}
		}
		reader.endObject();
		if (reader.peek() != JsonToken.END_DOCUMENT) {
			throw new IOException("Unexpected content after Simall price index");
		}
		if (items.isEmpty()) {
			throw new IOException("Simall price index contains no valid offers");
		}
		if (updatedAt.equals(Instant.EPOCH)) {
			updatedAt = fallbackUpdateTime;
		}
		return new MarketSnapshot(items, updatedAt, items.size());
	}

	private void readItems(JsonReader reader, Map<String, ItemMarketData> items) throws IOException {
		if (reader.peek() != JsonToken.BEGIN_OBJECT) {
			reader.skipValue();
			return;
		}
		reader.beginObject();
		int count = 0;
		while (reader.hasNext()) {
			if (++count > MAX_REASONABLE_ITEMS) {
				throw new IOException("Simall price index contains too many items");
			}
			String displayName = reader.nextName().strip();
			ItemMarketData data = readItem(reader, displayName);
			String normalizedName = ItemNameNormalizer.normalize(displayName);
			if (!normalizedName.isEmpty() && (data.lowestSell() != null || data.highestBuy() != null)) {
				items.put(normalizedName, data);
			}
		}
		reader.endObject();
	}

	private ItemMarketData readItem(JsonReader reader, String displayName) throws IOException {
		Offer sell = null;
		Offer buy = null;
		if (reader.peek() == JsonToken.BEGIN_OBJECT) {
			reader.beginObject();
			while (reader.hasNext()) {
				switch (reader.nextName()) {
					case "sell" -> sell = readOffer(reader);
					case "buy" -> buy = readOffer(reader);
					default -> reader.skipValue();
				}
			}
			reader.endObject();
		} else {
			reader.skipValue();
		}
		return new ItemMarketData(displayName, sell, buy);
	}

	private Offer readOffer(JsonReader reader) throws IOException {
		if (reader.peek() != JsonToken.BEGIN_OBJECT) {
			reader.skipValue();
			return null;
		}
		double price = Double.NaN;
		long amount = 0;
		double x = Double.NaN;
		double z = Double.NaN;
		String shop = "";
		String port = "";
		reader.beginObject();
		while (reader.hasNext()) {
			switch (reader.nextName()) {
				case "price" -> price = nextDouble(reader);
				case "shop" -> shop = nextString(reader);
				case "port" -> port = nextString(reader);
				case "amount" -> amount = nextLong(reader);
				case "x" -> x = nextDouble(reader);
				case "z" -> z = nextDouble(reader);
				default -> reader.skipValue();
			}
		}
		reader.endObject();
		if (!Double.isFinite(price) || price <= 0 || amount <= 0) {
			return null;
		}
		return new Offer(
				price,
				shop.isBlank() ? "未知" : shop.strip(),
				port.isBlank() ? "未知" : port.strip(),
				amount,
				x,
				z
		);
	}

	private static String nextString(JsonReader reader) throws IOException {
		if (reader.peek() == JsonToken.NULL) {
			reader.nextNull();
			return "";
		}
		try {
			return reader.nextString();
		} catch (IllegalStateException exception) {
			reader.skipValue();
			return "";
		}
	}

	private static double nextDouble(JsonReader reader) throws IOException {
		try {
			return Double.parseDouble(nextString(reader));
		} catch (NumberFormatException ignored) {
			return Double.NaN;
		}
	}

	private static long nextLong(JsonReader reader) throws IOException {
		double value = nextDouble(reader);
		return Double.isFinite(value) ? (long) value : 0L;
	}

	private static Instant parseUpdateTime(String value) {
		if (value == null || value.isBlank()) {
			return Instant.EPOCH;
		}
		try {
			return LocalDateTime.parse(value.strip(), SIMALL_TIME).atZone(SIMALL_ZONE).toInstant();
		} catch (DateTimeParseException ignored) {
			try {
				return Instant.parse(value.strip());
			} catch (DateTimeParseException ignoredAgain) {
				return Instant.EPOCH;
			}
		}
	}
}

package cn.simmc.simpricedisplay.market;

import cn.simmc.simpricedisplay.market.MarketDetails.DetailedOffer;
import cn.simmc.simpricedisplay.market.MarketDetails.Side;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class FullMarketParser {
	private static final int MAX_RECORDS = 1_000_000;
	private static final int LIMIT = 5;

	public FullMarketSnapshot parse(Path file) throws IOException {
		try (Reader input = Files.newBufferedReader(file, StandardCharsets.UTF_8); JsonReader reader = new JsonReader(input)) {
			return parse(reader, Files.getLastModifiedTime(file).toInstant());
		}
	}

	FullMarketSnapshot parse(JsonReader reader, Instant fallback) throws IOException {
		if (reader.peek() != JsonToken.BEGIN_ARRAY) throw new IOException("Simall shops root is not an array");
		Map<String, Accumulator> items = new HashMap<>();
		Instant newest = Instant.EPOCH;
		int records = 0;
		reader.beginArray();
		while (reader.hasNext()) {
			if (++records > MAX_RECORDS) throw new IOException("Simall shops contains too many records");
			Parsed parsed = readRecord(reader);
			if (parsed == null) continue;
			newest = parsed.offer.updatedAt().isAfter(newest) ? parsed.offer.updatedAt() : newest;
			items.computeIfAbsent(ItemNameNormalizer.normalize(parsed.item), ignored -> new Accumulator(parsed.item))
					.add(parsed.offer);
		}
		reader.endArray();
		Map<String, FullMarketSnapshot.Offers> result = new HashMap<>();
		items.forEach((key, value) -> result.put(key, value.finish()));
		return new FullMarketSnapshot(result, newest.equals(Instant.EPOCH) ? fallback : newest, records);
	}

	private Parsed readRecord(JsonReader reader) throws IOException {
		if (reader.peek() != JsonToken.BEGIN_OBJECT) { reader.skipValue(); return null; }
		String id = "", item = "", owner = "", region = "", type = "", updated = "";
		double price = Double.NaN, x = Double.NaN, y = Double.NaN, z = Double.NaN;
		long amount = 0;
		reader.beginObject();
		while (reader.hasNext()) {
			switch (reader.nextName()) {
				case "id" -> id = string(reader);
				case "item" -> item = string(reader);
				case "owner" -> owner = string(reader);
				case "region" -> region = string(reader);
				case "type" -> type = string(reader);
				case "updated" -> updated = string(reader);
				case "price" -> price = number(reader);
				case "amount" -> amount = (long) number(reader);
				case "x" -> x = number(reader);
				case "y" -> y = number(reader);
				case "z" -> z = number(reader);
				default -> reader.skipValue();
			}
		}
		reader.endObject();
		Side side = "SELL".equalsIgnoreCase(type) ? Side.SELL : "BUY".equalsIgnoreCase(type) ? Side.BUY : null;
		String normalized = ItemNameNormalizer.normalize(item);
		if (side == null || normalized.isEmpty() || !Double.isFinite(price) || price <= 0 || amount <= 0) return null;
		return new Parsed(item.strip(), new DetailedOffer(id, side, price,
				owner.isBlank() ? "未知" : owner.strip(), region.isBlank() ? "未知" : region.strip(), amount,
				x, y, z, instant(updated), false));
	}

	private static String string(JsonReader reader) throws IOException {
		if (reader.peek() == JsonToken.NULL) { reader.nextNull(); return ""; }
		try { return reader.nextString(); } catch (IllegalStateException error) { reader.skipValue(); return ""; }
	}

	private static double number(JsonReader reader) throws IOException {
		try { return Double.parseDouble(string(reader)); } catch (NumberFormatException error) { return Double.NaN; }
	}

	private static Instant instant(String value) {
		try { return Instant.parse(value); } catch (DateTimeParseException error) { return Instant.EPOCH; }
	}

	private record Parsed(String item, DetailedOffer offer) {}

	private static final class Accumulator {
		private final String displayName;
		private final List<DetailedOffer> sells = new ArrayList<>();
		private final List<DetailedOffer> buys = new ArrayList<>();
		private Accumulator(String displayName) { this.displayName = displayName; }
		private void add(DetailedOffer offer) {
			// Buy quotes with tiny capacity are not useful for inventory valuation and
			// must not displace genuine quotes from the retained top-five window.
			if (offer.side() == Side.BUY && offer.amount() < MarketValuation.MINIMUM_BUY_STOCK) return;
			List<DetailedOffer> target = offer.side() == Side.SELL ? sells : buys;
			target.add(offer);
			target.sort(offer.side() == Side.SELL
					? Comparator.comparingDouble(DetailedOffer::price)
					: Comparator.comparingDouble(DetailedOffer::price).reversed());
			if (target.size() > LIMIT) target.remove(target.size() - 1);
		}
		private FullMarketSnapshot.Offers finish() { return new FullMarketSnapshot.Offers(displayName, sells, buys); }
	}
}

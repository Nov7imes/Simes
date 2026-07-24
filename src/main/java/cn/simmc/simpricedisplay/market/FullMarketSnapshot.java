package cn.simmc.simpricedisplay.market;

import cn.simmc.simpricedisplay.market.MarketDetails.DetailedOffer;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class FullMarketSnapshot {
	public record Offers(String displayName, List<DetailedOffer> sells, List<DetailedOffer> buys) {
		public Offers {
			sells = List.copyOf(sells);
			buys = List.copyOf(buys);
		}
	}

	private static final FullMarketSnapshot EMPTY = new FullMarketSnapshot(Map.of(), Instant.EPOCH, 0);
	private final Map<String, Offers> byName;
	private final Instant updatedAt;
	private final int recordCount;

	public FullMarketSnapshot(Map<String, Offers> byName, Instant updatedAt, int recordCount) {
		this.byName = Map.copyOf(byName);
		this.updatedAt = updatedAt;
		this.recordCount = recordCount;
	}

	public static FullMarketSnapshot empty() { return EMPTY; }
	public boolean isEmpty() { return byName.isEmpty(); }
	public Instant updatedAt() { return updatedAt; }
	public int recordCount() { return recordCount; }
	public Offers exact(String displayName) { return byName.get(ItemNameNormalizer.normalize(displayName)); }
}

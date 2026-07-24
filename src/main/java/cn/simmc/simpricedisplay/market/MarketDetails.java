package cn.simmc.simpricedisplay.market;

import java.time.Instant;
import java.util.List;

public final class MarketDetails {
	private MarketDetails() {}

	public enum Side { SELL, BUY }

	public record DetailedOffer(String id, Side side, double price, String owner, String port,
			long amount, double x, double y, double z, Instant updatedAt, boolean latestIndex) {
		public boolean hasCoordinates() {
			return Double.isFinite(x) && Double.isFinite(z);
		}
	}

	public record ItemDetails(String displayName, List<DetailedOffer> sells, List<DetailedOffer> buys,
			Instant indexUpdatedAt, Instant fullUpdatedAt) {
		public ItemDetails {
			sells = List.copyOf(sells);
			buys = List.copyOf(buys);
		}
	}
}

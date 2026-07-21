package cn.simmc.simpricedisplay.market;

public final class MarketModels {
	private MarketModels() {
	}

	public record Offer(
			double price,
			String owner,
			String port,
			long amount,
			double x,
			double z
	) {
		public boolean hasCoordinates() {
			return Double.isFinite(x) && Double.isFinite(z);
		}
	}

	public record ItemMarketData(
			String displayName,
			Offer lowestSell,
			Offer highestBuy
	) {
	}

	public record MarketMatch(ItemMarketData data, boolean fuzzy) {
	}
}

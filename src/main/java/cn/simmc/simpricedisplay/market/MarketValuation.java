package cn.simmc.simpricedisplay.market;

import cn.simmc.simpricedisplay.market.MarketDetails.DetailedOffer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class MarketValuation {
	public static final long MINIMUM_BUY_STOCK = 100;

	private MarketValuation() {}

	/** Excludes the highest quote, then takes the median of ranks 2 through 5. */
	public static Optional<Double> buyMedianRanksTwoToFive(List<DetailedOffer> offers) {
		List<Double> prices = offers.stream()
				.filter(offer -> offer.amount() >= MINIMUM_BUY_STOCK)
				.map(DetailedOffer::price)
				.filter(price -> Double.isFinite(price) && price > 0)
				.sorted(Comparator.reverseOrder())
				.limit(5)
				.toList();
		if (prices.size() < 2) return Optional.empty();
		List<Double> candidates = new ArrayList<>(prices.subList(1, prices.size()));
		candidates.sort(Double::compareTo);
		int middle = candidates.size() / 2;
		return Optional.of(candidates.size() % 2 == 0
				? (candidates.get(middle - 1) + candidates.get(middle)) / 2.0
				: candidates.get(middle));
	}
}

package cn.simmc.simpricedisplay.market;

import cn.simmc.simpricedisplay.market.MarketDetails.DetailedOffer;
import cn.simmc.simpricedisplay.market.MarketDetails.Side;
import cn.simmc.simpricedisplay.market.MarketModels.ItemMarketData;
import cn.simmc.simpricedisplay.market.MarketModels.Offer;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MarketDetailMergerTest {
	@Test
	void indexOfferHasPriorityAndReplacesMatchingFullShop() {
		DetailedOffer stale = new DetailedOffer("old", Side.SELL, 20, "Alice", "旧港", 2,
				10, 64, 20, Instant.EPOCH, false);
		DetailedOffer second = new DetailedOffer("second", Side.SELL, 16, "Bob", "B港", 3,
				30, 65, 40, Instant.EPOCH, false);
		var full = new FullMarketSnapshot.Offers("钻石", List.of(second, stale), List.of());
		var index = new ItemMarketData("钻石", new Offer(15, "Alice", "新港", 99, 10, 20), null);
		var merged = MarketDetailMerger.merge(index, Instant.parse("2026-07-22T00:00:00Z"), full, Instant.EPOCH, List.of());
		assertEquals(2, merged.sells().size());
		assertEquals(15, merged.sells().getFirst().price());
		assertTrue(merged.sells().getFirst().latestIndex());
		assertEquals("新港", merged.sells().getFirst().port());
	}

	@Test
	void limitsMergedResultsToFive() {
		var full = new FullMarketSnapshot.Offers("钻石", List.of(
				offer(2), offer(3), offer(4), offer(5), offer(6)), List.of());
		var index = new ItemMarketData("钻石", new Offer(1, "latest", "港", 1, 99, 99), null);
		var merged = MarketDetailMerger.merge(index, Instant.now(), full, Instant.EPOCH, List.of());
		assertEquals(5, merged.sells().size());
		assertEquals(5, merged.sells().getLast().price());
	}

	@Test
	void fillsMissingFullPortFromNearestIndexAnchor() {
		DetailedOffer unknown = new DetailedOffer("shop", Side.BUY, 10, "Bob", "未知", 20,
				105, 64, 98, Instant.EPOCH, false);
		var full = new FullMarketSnapshot.Offers("item", List.of(), List.of(unknown));
		var index = new ItemMarketData("item", null, new Offer(99, "Other", "红山区天舟驿站", 1, 100, 100));
		var merged = MarketDetailMerger.merge(index, Instant.now(), full, Instant.EPOCH,
				List.of(new MarketSnapshot.PortAnchor(100, 100, "红山区天舟驿站")));
		assertEquals("红山区天舟驿站", merged.buys().get(1).port());
	}

	private static DetailedOffer offer(double price) {
		return new DetailedOffer("" + price, Side.SELL, price, "p" + price, "港", 1,
				price, 64, price, Instant.EPOCH, false);
	}
}

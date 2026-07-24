package cn.simmc.simpricedisplay.market;

import cn.simmc.simpricedisplay.market.MarketDetails.DetailedOffer;
import cn.simmc.simpricedisplay.market.MarketDetails.Side;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MarketValuationTest {
	@Test
	void ignoresHighestAndUsesMedianOfRanksTwoThroughFive() {
		assertEquals(35.0, MarketValuation.buyMedianRanksTwoToFive(List.of(
				offer(1_000_000), offer(50), offer(40), offer(30), offer(20), offer(10))).orElseThrow());
	}

	@Test
	void usesAvailableRanksWhenThereAreFewerThanFive() {
		assertEquals(20.0, MarketValuation.buyMedianRanksTwoToFive(List.of(offer(999), offer(20))).orElseThrow());
		assertTrue(MarketValuation.buyMedianRanksTwoToFive(List.of(offer(999))).isEmpty());
	}

	@Test
	void ignoresBuyQuotesWithLessThanOneHundredStock() {
		assertEquals(30.0, MarketValuation.buyMedianRanksTwoToFive(List.of(
				offer(9_999_999, 1), offer(50, 100), offer(40, 999), offer(30, 100), offer(20, 500)
		)).orElseThrow());
	}

	private static DetailedOffer offer(double price) {
		return offer(price, 100);
	}

	private static DetailedOffer offer(double price, long amount) {
		return new DetailedOffer("", Side.BUY, price, "", "", amount, 0, 0, 0, Instant.EPOCH, false);
	}
}

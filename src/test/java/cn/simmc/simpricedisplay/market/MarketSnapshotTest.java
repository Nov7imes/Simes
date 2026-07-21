package cn.simmc.simpricedisplay.market;

import cn.simmc.simpricedisplay.market.MarketModels.ItemMarketData;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketSnapshotTest {
	@Test
	void exactMatchWinsBeforeFuzzySearch() {
		ItemMarketData diamond = new ItemMarketData("钻石", null, null);
		ItemMarketData diamondBlock = new ItemMarketData("钻石块", null, null);
		MarketSnapshot snapshot = new MarketSnapshot(
				Map.of("钻石", diamond, "钻石块", diamondBlock),
				Instant.now(),
				2
		);

		var match = snapshot.find("钻石").orElseThrow();
		assertEquals("钻石", match.data().displayName());
		assertFalse(match.fuzzy());
	}

	@Test
	void acceptsOnlyUniqueHighConfidenceFuzzyMatch() {
		ItemMarketData shield = new ItemMarketData("工匠强化盾牌", null, null);
		MarketSnapshot snapshot = new MarketSnapshot(
				Map.of(ItemNameNormalizer.normalize(shield.displayName()), shield),
				Instant.now(),
				1
		);

		var match = snapshot.find("工匠强化盾").orElseThrow();
		assertTrue(match.fuzzy());
		assertEquals("工匠强化盾牌", match.data().displayName());
	}

	@Test
	void rejectsLowConfidenceShortName() {
		ItemMarketData diamondBlock = new ItemMarketData("钻石块", null, null);
		MarketSnapshot snapshot = new MarketSnapshot(
				Map.of(ItemNameNormalizer.normalize(diamondBlock.displayName()), diamondBlock),
				Instant.now(),
				1
		);

		assertTrue(snapshot.find("钻石").isEmpty());
	}

	@Test
	void keepsSymbolVariantsDistinctAndRejectsAmbiguousLooseMatch() {
		ItemMarketData left = new ItemMarketData("赠于你的星光←", null, null);
		ItemMarketData right = new ItemMarketData("赠于你的星光→", null, null);
		MarketSnapshot snapshot = new MarketSnapshot(
				Map.of(
						ItemNameNormalizer.normalize(left.displayName()), left,
						ItemNameNormalizer.normalize(right.displayName()), right
				),
				Instant.now(),
				2
		);

		assertEquals("赠于你的星光←", snapshot.find("赠于你的星光←").orElseThrow().data().displayName());
		assertEquals("赠于你的星光→", snapshot.find("赠于你的星光→").orElseThrow().data().displayName());
		assertTrue(snapshot.find("赠于你的星光").isEmpty());
	}
}

package cn.simmc.simpricedisplay.market;

import com.google.gson.stream.JsonReader;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MarketParserTest {
	@Test
	void parsesSummaryOffersCoordinatesAndShanghaiUpdateTime() throws Exception {
		String json = """
				{
				  "updated":"2026-07-19 17:29:12",
				  "items":{
				    "藤颈嫩芯":{
				      "sell":{"price":15,"shop":"卖家乙","port":"自由市场","amount":50,"x":120.5,"z":-340},
				      "buy":{"price":10,"shop":"收购甲","port":"东港","amount":30,"x":95,"z":0}
				    }
				  }
				}
				""";

		MarketSnapshot snapshot = parse(json);
		var item = snapshot.find("藤颈嫩芯").orElseThrow().data();
		assertEquals(15.0, item.lowestSell().price());
		assertEquals("卖家乙", item.lowestSell().owner());
		assertEquals("自由市场", item.lowestSell().port());
		assertEquals(50, item.lowestSell().amount());
		assertEquals(120.5, item.lowestSell().x());
		assertEquals(-340, item.lowestSell().z());
		assertEquals(10.0, item.highestBuy().price());
		assertEquals("收购甲", item.highestBuy().owner());
		assertEquals(Instant.parse("2026-07-19T09:29:12Z"), snapshot.dataUpdatedAt());
		assertEquals(1, snapshot.itemCount());
	}

	@Test
	void toleratesMissingSideAndCoordinates() throws Exception {
		MarketSnapshot snapshot = parse("""
				{"updated":"2026-07-19 17:29:12","items":{
				  "钻石":{"sell":{"price":1,"shop":"店主","port":"北港","amount":4}}
				}}
				""");
		var item = snapshot.find("钻石").orElseThrow().data();
		assertNull(item.highestBuy());
		assertFalse(item.lowestSell().hasCoordinates());
	}

	@Test
	void rejectsIndexWithoutSemanticOffers() throws Exception {
		assertThrows(IOException.class, () -> parse("""
				{"updated":"2026-07-19 17:29:12","items":{
				  "错误页面":{"sell":{"price":0,"shop":"","port":"","amount":0}}
				}}
				"""));
	}

	@Test
	void keepsVisibleSymbolAndCaseVariantsDistinct() throws Exception {
		MarketSnapshot snapshot = parse("""
				{"updated":"2026-07-19 17:29:12","items":{
				  "赠于你的星光←":{"sell":{"price":11,"shop":"左店","port":"左港","amount":1}},
				  "赠于你的星光→":{"sell":{"price":22,"shop":"右店","port":"右港","amount":1}},
				  "E.G.O":{"sell":{"price":33,"shop":"大写","port":"港口","amount":1}},
				  "e.g.o":{"sell":{"price":44,"shop":"小写","port":"港口","amount":1}}
				}}
				""");

		assertEquals(11, snapshot.find("赠于你的星光←").orElseThrow().data().lowestSell().price());
		assertEquals(22, snapshot.find("赠于你的星光→").orElseThrow().data().lowestSell().price());
		assertEquals(33, snapshot.find("E.G.O").orElseThrow().data().lowestSell().price());
		assertEquals(44, snapshot.find("e.g.o").orElseThrow().data().lowestSell().price());
	}

	private static MarketSnapshot parse(String json) throws Exception {
		try (JsonReader reader = new JsonReader(new StringReader(json))) {
			return new MarketParser().parse(reader, Instant.EPOCH);
		}
	}
}

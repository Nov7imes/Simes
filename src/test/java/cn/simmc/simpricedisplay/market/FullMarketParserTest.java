package cn.simmc.simpricedisplay.market;

import com.google.gson.stream.JsonReader;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class FullMarketParserTest {
	@Test
	void streamsAndKeepsOnlyFiveBestOffersPerSide() throws Exception {
		StringBuilder json = new StringBuilder("[");
		for (int i = 1; i <= 7; i++) {
			if (i > 1) json.append(',');
			json.append("{\"id\":\"s").append(i).append("\",\"item\":\"钻石\",\"owner\":\"p")
					.append(i).append("\",\"price\":").append(i).append(",\"amount\":10,\"type\":\"SELL\",")
					.append("\"updated\":\"2026-07-21T16:44:19Z\",\"x\":1,\"y\":64,\"z\":2}");
		}
		json.append(']');
		FullMarketSnapshot snapshot = new FullMarketParser().parse(
				new JsonReader(new StringReader(json.toString())), Instant.EPOCH);
		var offers = snapshot.exact("钻石");
		assertNotNull(offers);
		assertEquals(5, offers.sells().size());
		assertEquals(1.0, offers.sells().getFirst().price());
		assertEquals(5.0, offers.sells().getLast().price());
		assertEquals(64.0, offers.sells().getFirst().y());
	}

	@Test
	void rejectsZeroStockAndUnknownSides() throws Exception {
		String json = "[{\"item\":\"钻石\",\"price\":1,\"amount\":0,\"type\":\"SELL\"},"
				+ "{\"item\":\"钻石\",\"price\":2,\"amount\":5,\"type\":\"OTHER\"}]";
		FullMarketSnapshot snapshot = new FullMarketParser().parse(new JsonReader(new StringReader(json)), Instant.EPOCH);
		assertTrue(snapshot.isEmpty());
	}

	@Test
	void lowStockBuyQuotesDoNotDisplaceValuationCandidates() throws Exception {
		String json = "["
				+ "{\"id\":\"trap\",\"item\":\"钻石\",\"owner\":\"trap\",\"price\":999999,\"amount\":1,\"type\":\"BUY\"},"
				+ "{\"id\":\"b1\",\"item\":\"钻石\",\"owner\":\"p1\",\"price\":50,\"amount\":100,\"type\":\"BUY\"},"
				+ "{\"id\":\"b2\",\"item\":\"钻石\",\"owner\":\"p2\",\"price\":40,\"amount\":101,\"type\":\"BUY\"}]";
		FullMarketSnapshot snapshot = new FullMarketParser().parse(new JsonReader(new StringReader(json)), Instant.EPOCH);
		var buys = snapshot.exact("钻石").buys();
		assertEquals(2, buys.size());
		assertEquals("b1", buys.getFirst().id());
	}
}

package cn.simmc.simpricedisplay.market;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class LiveSimallIndexTest {
	@Test
	void parsesCurrentPriceIndexWhenFixtureIsProvided() throws Exception {
		String indexFile = System.getenv("SIMES_LIVE_INDEX");
		assumeTrue(indexFile != null && !indexFile.isBlank());

		MarketSnapshot snapshot = new MarketParser().parse(Path.of(indexFile));
		assertTrue(snapshot.itemCount() > 1_000, "Expected a substantial Simall price index");
		assertFalse(snapshot.find("钻石").isEmpty(), "Expected the live index to contain diamonds");
	}
}

package cn.simmc.simpricedisplay;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ManaPredictionTest {
	@Test
	void predictsContinuousRegenerationBetweenServerPackets() {
		long updated = 1_000_000_000L;
		assertEquals(50.08, ManaHud.predictedMana(50.0, 0.8, 180.0, updated, updated + 100_000_000L), 0.0001);
		assertEquals(50.8, ManaHud.predictedMana(50.0, 0.8, 180.0, updated, updated + 1_000_000_000L), 0.0001);
	}

	@Test
	void predictionNeverExceedsMaximum() {
		long updated = 1_000_000_000L;
		assertEquals(180.0, ManaHud.predictedMana(179.9, 0.8, 180.0, updated, updated + 2_000_000_000L), 0.0001);
	}
}

package cn.simmc.simpricedisplay;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SuppressedBossBarIdsTest {
	@Test
	void remainsSuppressedUntilMatchingRemoveArrives() {
		SuppressedBossBarIds tracker = new SuppressedBossBarIds();
		UUID id = UUID.randomUUID();
		tracker.suppress(id);
		assertTrue(tracker.contains(id), "late name/progress updates must remain hidden");
		assertTrue(tracker.release(id), "matching remove should release the hidden id");
		assertFalse(tracker.contains(id));
	}

	@Test
	void resetClearsSuppressedIdsAcrossDisconnects() {
		SuppressedBossBarIds tracker = new SuppressedBossBarIds();
		UUID id = UUID.randomUUID();
		tracker.suppress(id);
		tracker.clear();
		assertFalse(tracker.contains(id));
	}
}

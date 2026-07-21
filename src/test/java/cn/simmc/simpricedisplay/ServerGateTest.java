package cn.simmc.simpricedisplay;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerGateTest {
	@Test
	void acceptsConfiguredHostWithPortAndTrailingDot() {
		assertTrue(ServerGate.isTargetAddress("play.simmc.cn:25565"));
		assertTrue(ServerGate.isTargetAddress("PLAY.SIMMC.CN.:25565"));
	}

	@Test
	void rejectsLookalikeAndNumericHosts() {
		assertFalse(ServerGate.isTargetAddress("play.simmc.cn.evil.example"));
		assertFalse(ServerGate.isTargetAddress("127.0.0.1:25565"));
	}
}

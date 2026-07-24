package cn.simmc.simpricedisplay;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ArcaneInputHintFilterTest {
	@Test
	void recognizesLegacyStandaloneHints() {
		assertTrue(ArcaneCooldownHud.isArcaneInputHintOnly("上 "));
		assertTrue(ArcaneCooldownHud.isArcaneInputHintOnly("下 "));
		assertTrue(ArcaneCooldownHud.isArcaneInputHintOnly("丌 "));
		assertTrue(ArcaneCooldownHud.isArcaneInputHintOnly("Shift "));
		assertTrue(ArcaneCooldownHud.isArcaneInputHintOnly("上  Shift"));
		assertFalse(ArcaneCooldownHud.isArcaneInputHintOnly("传送至上城区"));
		assertFalse(ArcaneCooldownHud.isArcaneInputHintOnly("传送至下城区"));
	}

	@Test
	void removesHintsWithoutRemovingNormalWords() {
		assertEquals("", ArcaneCooldownHud.stripArcaneInputHints("Shift"));
		assertEquals("", ArcaneCooldownHud.stripArcaneInputHints("下"));
		assertEquals("其他消息", ArcaneCooldownHud.stripArcaneInputHints("上 其他消息 Shift"));
		assertEquals("传送至上城区", ArcaneCooldownHud.stripArcaneInputHints("传送至上城区"));
		assertEquals("传送至下城区", ArcaneCooldownHud.stripArcaneInputHints("传送至下城区"));
	}
}

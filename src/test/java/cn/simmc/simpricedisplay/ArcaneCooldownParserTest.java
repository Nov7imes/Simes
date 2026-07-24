package cn.simmc.simpricedisplay;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ArcaneCooldownParserTest {
	@Test
	void parsesThreeCooldownsAndPreservesKeyHint() {
		var result = ArcaneCooldownParser.parse(
				"火球术冷却剩余：0.2s | 治愈术冷却剩余：44.0s | 雷电射线冷却剩余：1.4s Shift");
		assertEquals(3, result.values().size());
		assertEquals("火球术", result.values().get(0).name());
		assertEquals(44.0, result.values().get(1).remaining());
		assertEquals("雷电射线", result.values().get(2).name());
		assertEquals("Shift", result.residual());
	}

	@Test
	void leavesUnrelatedActionBarUntouched() {
		var result = ArcaneCooldownParser.parse("港口：云港");
		assertEquals(0, result.values().size());
		assertEquals("港口：云港", result.residual());
	}

	@Test
	void acceptsServerWhitespaceSeenInScreenshot() {
		var result = ArcaneCooldownParser.parse("腾云术冷却剩余： 3.6s");
		assertEquals(1, result.values().size());
		assertEquals("腾云术", result.values().get(0).name());
		assertEquals(3.6, result.values().get(0).remaining());
	}

	@Test
	void acceptsSafeFormattingVariants() {
		var result = ArcaneCooldownParser.parse(
				"火球术 冷却 剩余: 2.5 S ｜ 治愈术冷却剩余： 10秒 | 雷击冷却剩余：1.2 s Shift");
		assertEquals(3, result.values().size());
		assertEquals("火球术", result.values().get(0).name());
		assertEquals("治愈术", result.values().get(1).name());
		assertEquals("雷击", result.values().get(2).name());
		assertEquals("Shift", result.residual());
	}

	@Test
	void stillRejectsSimilarButNonCooldownText() {
		assertEquals(0, ArcaneCooldownParser.parse("腾云术剩余次数：3").values().size());
		assertEquals(0, ArcaneCooldownParser.parse("冷却完成：腾云术").values().size());
		assertEquals(0, ArcaneCooldownParser.parse("余额：3.6s").values().size());
	}
}

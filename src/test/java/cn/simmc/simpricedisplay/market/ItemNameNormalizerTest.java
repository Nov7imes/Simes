package cn.simmc.simpricedisplay.market;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ItemNameNormalizerTest {
	@Test
	void exactKeyStripsFormattingButPreservesVisibleIdentity() {
		assertEquals("藤颈（嫩）芯★", ItemNameNormalizer.normalize("  §b藤颈（嫩）芯★  "));
	}

	@Test
	void looseKeyNormalizesWidthCaseWhitespaceAndPunctuation() {
		assertEquals("abc 12钻石", ItemNameNormalizer.looseNormalize("ＡＢＣ\u00a0\u00a0１２（钻石）"));
	}

	@Test
	void exactKeyKeepsButLooseKeyRemovesZeroWidthCharacters() {
		assertEquals("工匠\u200B强化盾牌", ItemNameNormalizer.normalize("工匠\u200B强化盾牌"));
		assertEquals("工匠强化盾牌", ItemNameNormalizer.looseNormalize("工匠\u200B强化盾牌"));
	}
}

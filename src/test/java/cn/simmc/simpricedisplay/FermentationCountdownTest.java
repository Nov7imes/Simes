package cn.simmc.simpricedisplay;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FermentationCountdownTest {
	@Test
	void parsesServerChineseDuration() {
		assertEquals(1_059_670L,
				FermentationCountdown.parseMillis("17 分钟 39 秒 670 毫秒"));
		assertEquals(0L, FermentationCountdown.parseMillis("0 秒 0 毫秒"));
		assertEquals(-1L, FermentationCountdown.parseMillis("已完成"));
	}

	@Test
	void projectsFromMonotonicCalibrationTime() {
		assertEquals(8_750L,
				FermentationCountdown.remainingMillis(10_000L, 1_000_000_000L, 2_250_000_000L));
		assertEquals(0L,
				FermentationCountdown.remainingMillis(1_000L, 1_000_000_000L, 3_000_000_000L));
	}

	@Test
	void formatsProjectedTime() {
		assertEquals("17 分 39.7 秒", FermentationCountdown.formatMillis(1_059_670L));
		assertEquals("0.8 秒", FermentationCountdown.formatMillis(800L));
	}

	@Test
	void recognizesServerCompletionWording() {
		assertTrue(FermentationCountdown.isServerComplete("腌制已完成！"));
		assertTrue(FermentationCountdown.isServerComplete("剩余时间：已完成"));
		assertTrue(FermentationCountdown.isServerComplete(" 完 成 "));
		assertFalse(FermentationCountdown.isServerComplete("预计已到，等待服务器确认"));
		assertFalse(FermentationCountdown.isServerComplete("正在腌制"));
	}
}

package cn.simmc.simpricedisplay;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class BalanceLedgerTest {
	private static final LocalDate TODAY = LocalDate.of(2026, 7, 22);

	@Test
	void keepsSameDayBaselineAcrossReconnects() {
		assertEquals(new BigDecimal("1564291.12"),
				BalanceLedger.savedBaseline(TODAY, "2026-07-22", "1564291.12"));
	}

	@Test
	void rejectsPreviousDayAndInvalidValues() {
		assertNull(BalanceLedger.savedBaseline(TODAY, "2026-07-21", "100"));
		assertNull(BalanceLedger.savedBaseline(TODAY, "2026-07-22", "not-money"));
		assertNull(BalanceLedger.savedBaseline(TODAY, "2026-07-22", ""));
	}
}

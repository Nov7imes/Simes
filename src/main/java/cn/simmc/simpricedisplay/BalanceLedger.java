package cn.simmc.simpricedisplay;

import java.math.BigDecimal;
import java.time.LocalDate;

final class BalanceLedger {
	private BalanceLedger() {}

	static BigDecimal savedBaseline(LocalDate today, String savedDate, String savedValue) {
		if (today == null || savedDate == null || savedValue == null
				|| !today.toString().equals(savedDate) || savedValue.isBlank()) return null;
		try {
			return new BigDecimal(savedValue);
		} catch (NumberFormatException ignored) {
			return null;
		}
	}
}

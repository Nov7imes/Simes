package cn.simmc.simpricedisplay;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimesDiagnosticsTest {
	@Test
	void redactRemovesUuidAndIpv4Address() {
		String source = "player=123e4567-e89b-12d3-a456-426614174000 server=192.168.1.25:25565";
		String redacted = SimesDiagnostics.redact(source);
		assertFalse(redacted.contains("123e4567-e89b-12d3-a456-426614174000"));
		assertFalse(redacted.contains("192.168.1.25"));
		assertTrue(redacted.contains("<UUID>"));
		assertTrue(redacted.contains("<IP>"));
	}

	@Test
	void redactKeepsOrdinaryDiagnosticText() {
		String source = "mod=simes version=test action=render";
		assertTrue(SimesDiagnostics.redact(source).contains(source));
	}
}

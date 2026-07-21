package cn.simmc.simpricedisplay.market;

import java.text.Normalizer;
import java.util.Locale;

public final class ItemNameNormalizer {
	private ItemNameNormalizer() {
	}

	/**
	 * Conservative normalization for exact identity. Visible symbols, arrows and private-use glyphs are preserved.
	 */
	public static String normalize(String input) {
		if (input == null || input.isBlank()) {
			return "";
		}

		String normalized = Normalizer.normalize(input, Normalizer.Form.NFC);
		StringBuilder result = new StringBuilder(normalized.length());
		for (int offset = 0; offset < normalized.length(); ) {
			int codePoint = normalized.codePointAt(offset);
			offset += Character.charCount(codePoint);

			if (codePoint == '\u00A7') {
				if (offset < normalized.length()) {
					int formatCode = normalized.codePointAt(offset);
					offset += Character.charCount(formatCode);
				}
				continue;
			}

			if (!Character.isISOControl(codePoint)) {
				result.appendCodePoint(codePoint);
			}
		}
		return result.toString().strip();
	}

	/**
	 * Simall-style forgiving key used only for fuzzy candidates, never for merging distinct market items.
	 */
	public static String looseNormalize(String input) {
		String exact = normalize(input);
		if (exact.isEmpty()) {
			return "";
		}

		String normalized = Normalizer.normalize(exact, Normalizer.Form.NFKC);
		StringBuilder result = new StringBuilder(normalized.length());
		boolean previousWasSpace = false;

		for (int offset = 0; offset < normalized.length(); ) {
			int codePoint = normalized.codePointAt(offset);
			offset += Character.charCount(codePoint);

			if (codePoint == '\u200B' || codePoint == '\u200C' || codePoint == '\u200D' || codePoint == '\uFEFF') {
				continue;
			}

			if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)) {
				if (!previousWasSpace && !result.isEmpty()) {
					result.append(' ');
					previousWasSpace = true;
				}
				continue;
			}

			if (isAllowedBySimall(codePoint)) {
				result.appendCodePoint(codePoint);
				previousWasSpace = false;
			}
		}

		int length = result.length();
		if (length > 0 && result.charAt(length - 1) == ' ') {
			result.setLength(length - 1);
		}
		return result.toString().toLowerCase(Locale.ROOT);
	}

	private static boolean isAllowedBySimall(int codePoint) {
		return codePoint >= '\u4E00' && codePoint <= '\u9FA5'
				|| codePoint >= 'a' && codePoint <= 'z'
				|| codePoint >= 'A' && codePoint <= 'Z'
				|| codePoint >= '0' && codePoint <= '9'
				|| codePoint == '★'
				|| codePoint == '☆';
	}
}

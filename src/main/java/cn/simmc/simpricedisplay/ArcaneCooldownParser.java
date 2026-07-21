package cn.simmc.simpricedisplay;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ArcaneCooldownParser {
	private static final Pattern COOLDOWN = Pattern.compile("(?:^|\\|)\\s*([^|]+?)冷却剩余：([0-9]+(?:\\.[0-9]+)?)s");

	private ArcaneCooldownParser() {}

	static Result parse(String rawText) {
		String raw = rawText == null ? "" : rawText.trim();
		Matcher matcher = COOLDOWN.matcher(raw);
		List<Value> values = new ArrayList<>();
		int end = 0;
		while (matcher.find()) {
			values.add(new Value(matcher.group(1).trim(), Double.parseDouble(matcher.group(2))));
			end = matcher.end();
		}
		String residual = values.isEmpty() ? raw : raw.substring(Math.min(end, raw.length())).trim();
		while (residual.startsWith("|")) residual = residual.substring(1).trim();
		return new Result(List.copyOf(values), residual);
	}

	record Value(String name, double remaining) {}
	record Result(List<Value> values, String residual) {}
}

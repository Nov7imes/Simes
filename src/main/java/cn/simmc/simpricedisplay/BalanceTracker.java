package cn.simmc.simpricedisplay;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BalanceTracker {
	private static final Pattern BALANCE = Pattern.compile("余额\\s*[:：]\\s*\\$?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)");
	private static final long FIRST_QUERY_MS = 3 * 60 * 1000L;
	private static final long QUERY_INTERVAL_MS = 10 * 60 * 1000L;
	private static final long RESPONSE_TIMEOUT_MS = 30 * 1000L;
	private static final DecimalFormat MONEY = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.US));
	private static boolean active;
	private static boolean automaticQueryPending;
	private static long nextQueryAt;
	private static long pendingUntil;
	private static BigDecimal current;
	private static BigDecimal baseline;

	private BalanceTracker() {}

	public static void register() {
		loadSavedValues();
		ClientTickEvents.END_CLIENT_TICK.register(BalanceTracker::tick);
		ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> handleMessage(message, overlay));
	}

	public static void onJoin(boolean targetServer) {
		active = targetServer;
		automaticQueryPending = false;
		nextQueryAt = targetServer ? System.currentTimeMillis() + FIRST_QUERY_MS : 0L;
		if (targetServer) {
			// Each login establishes a fresh earning baseline from its first valid reply.
			// Do not compare a new session against a stale same-day balance.
			current = null;
			baseline = null;
		}
	}

	public static void onDisconnect() {
		active = false;
		automaticQueryPending = false;
		nextQueryAt = 0L;
	}

	private static void tick(MinecraftClient client) {
		if (!active || client.player == null || client.getNetworkHandler() == null) return;
		long now = System.currentTimeMillis();
		if (automaticQueryPending && now > pendingUntil) automaticQueryPending = false;
		if (nextQueryAt != 0L && now >= nextQueryAt) {
			client.getNetworkHandler().sendChatCommand("bal");
			automaticQueryPending = true;
			pendingUntil = now + RESPONSE_TIMEOUT_MS;
			nextQueryAt = now + QUERY_INTERVAL_MS;
		}
	}

	private static boolean handleMessage(Text message, boolean overlay) {
		if (!active || overlay) return true;
		Matcher matcher = BALANCE.matcher(message.getString());
		if (!matcher.find()) return true;
		try {
			updateBalance(new BigDecimal(matcher.group(1).replace(",", "")));
		} catch (NumberFormatException error) {
			SimesClient.LOGGER.warn("Could not parse balance message: {}", message.getString());
			return true;
		}
		boolean hide = automaticQueryPending;
		automaticQueryPending = false;
		return !hide;
	}

	private static void updateBalance(BigDecimal value) {
		SimesConfig config = SimesConfig.get();
		String today = LocalDate.now().toString();
		if (!today.equals(config.balanceDate) || baseline == null) {
			baseline = value;
			config.balanceDate = today;
			config.balanceBaseline = value.toPlainString();
		}
		current = value;
		config.currentBalance = value.toPlainString();
		config.save();
	}

	private static void loadSavedValues() {
		SimesConfig config = SimesConfig.get();
		try {
			if (!config.currentBalance.isBlank()) current = new BigDecimal(config.currentBalance);
			if (LocalDate.now().toString().equals(config.balanceDate) && !config.balanceBaseline.isBlank()) {
				baseline = new BigDecimal(config.balanceBaseline);
			}
		} catch (NumberFormatException error) {
			current = null;
			baseline = null;
		}
	}

	public static boolean hasBalance() { return current != null; }
	public static String currentText() { return current == null ? "--" : format(current); }
	public static String todayChangeText() {
		if (current == null || baseline == null) return "--";
		BigDecimal change = current.subtract(baseline);
		return (change.signum() >= 0 ? "+" : "-") + "$" + format(change.abs());
	}
	public static int todayChangeColor() {
		if (current == null || baseline == null) return 0xFFAAAAAA;
		return current.compareTo(baseline) >= 0 ? 0xFF55FF55 : 0xFFFF5555;
	}
	private static String format(BigDecimal value) { return MONEY.format(value); }
}

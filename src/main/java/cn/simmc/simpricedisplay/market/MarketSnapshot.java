package cn.simmc.simpricedisplay.market;

import cn.simmc.simpricedisplay.market.MarketModels.ItemMarketData;
import cn.simmc.simpricedisplay.market.MarketModels.MarketMatch;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public final class MarketSnapshot {
	private static final MarketSnapshot EMPTY = new MarketSnapshot(Map.of(), Instant.EPOCH, 0);

	private final Map<String, ItemMarketData> itemsByNormalizedName;
	private final Instant dataUpdatedAt;
	private final int shopRecordCount;
	private final List<PortAnchor> portAnchors;
	private final ConcurrentHashMap<String, Optional<MarketMatch>> matchCache = new ConcurrentHashMap<>();

	public MarketSnapshot(
			Map<String, ItemMarketData> itemsByNormalizedName,
			Instant dataUpdatedAt,
			int shopRecordCount
	) {
		this.itemsByNormalizedName = Map.copyOf(itemsByNormalizedName);
		this.dataUpdatedAt = dataUpdatedAt;
		this.shopRecordCount = shopRecordCount;
		this.portAnchors = collectPortAnchors(this.itemsByNormalizedName);
	}

	public static MarketSnapshot empty() {
		return EMPTY;
	}

	public boolean isEmpty() {
		return itemsByNormalizedName.isEmpty();
	}

	public int itemCount() {
		return itemsByNormalizedName.size();
	}

	public int shopRecordCount() {
		return shopRecordCount;
	}

	public Instant dataUpdatedAt() {
		return dataUpdatedAt;
	}

	List<PortAnchor> portAnchors() {
		return portAnchors;
	}

	private static List<PortAnchor> collectPortAnchors(Map<String, ItemMarketData> items) {
		List<PortAnchor> anchors = new ArrayList<>();
		for (ItemMarketData data : items.values()) {
			addAnchor(anchors, data.lowestSell());
			addAnchor(anchors, data.highestBuy());
		}
		return List.copyOf(anchors);
	}

	private static void addAnchor(List<PortAnchor> anchors, MarketModels.Offer offer) {
		if (offer == null || !Double.isFinite(offer.x()) || !Double.isFinite(offer.z())) return;
		String port = offer.port() == null ? "" : offer.port().strip();
		if (port.isEmpty() || "未知".equals(port)) return;
		anchors.add(new PortAnchor(offer.x(), offer.z(), port));
	}

	record PortAnchor(double x, double z, String port) {}

	public Optional<MarketMatch> find(String visibleName) {
		String normalizedName = ItemNameNormalizer.normalize(visibleName);
		if (normalizedName.isEmpty() || isEmpty()) {
			return Optional.empty();
		}
		return matchCache.computeIfAbsent(normalizedName, this::findUncached);
	}

	private Optional<MarketMatch> findUncached(String normalizedName) {
		ItemMarketData exact = itemsByNormalizedName.get(normalizedName);
		if (exact != null) {
			return Optional.of(new MarketMatch(exact, false));
		}

		String looseQuery = ItemNameNormalizer.looseNormalize(normalizedName);
		int queryLength = looseQuery.codePointCount(0, looseQuery.length());
		if (queryLength < 2) {
			return Optional.empty();
		}

		String bestName = null;
		double bestScore = 0.0;
		double secondBestScore = 0.0;

		for (String candidate : itemsByNormalizedName.keySet()) {
			String looseCandidate = ItemNameNormalizer.looseNormalize(candidate);
			if (looseCandidate.isEmpty()) {
				continue;
			}
			double score = similarity(looseQuery, looseCandidate);
			if (score > bestScore) {
				secondBestScore = bestScore;
				bestScore = score;
				bestName = candidate;
			} else if (score > secondBestScore) {
				secondBestScore = score;
			}
		}

		double threshold = queryLength <= 3 ? 0.90 : queryLength <= 5 ? 0.80 : 0.75;
		if (bestName == null || bestScore < threshold || bestScore - secondBestScore < 0.08) {
			return Optional.empty();
		}

		return Optional.of(new MarketMatch(itemsByNormalizedName.get(bestName), true));
	}

	static double similarity(String left, String right) {
		int[] leftPoints = left.codePoints().toArray();
		int[] rightPoints = right.codePoints().toArray();
		int maximumLength = Math.max(leftPoints.length, rightPoints.length);
		if (maximumLength == 0) {
			return 1.0;
		}
		return 1.0 - (double) levenshteinDistance(leftPoints, rightPoints) / maximumLength;
	}

	private static int levenshteinDistance(int[] left, int[] right) {
		if (left.length > right.length) {
			int[] temporary = left;
			left = right;
			right = temporary;
		}

		int[] previous = new int[left.length + 1];
		int[] current = new int[left.length + 1];
		for (int index = 0; index <= left.length; index++) {
			previous[index] = index;
		}

		for (int rightIndex = 1; rightIndex <= right.length; rightIndex++) {
			current[0] = rightIndex;
			for (int leftIndex = 1; leftIndex <= left.length; leftIndex++) {
				int substitutionCost = left[leftIndex - 1] == right[rightIndex - 1] ? 0 : 1;
				current[leftIndex] = Math.min(
						Math.min(current[leftIndex - 1] + 1, previous[leftIndex] + 1),
						previous[leftIndex - 1] + substitutionCost
				);
			}
			int[] temporary = previous;
			previous = current;
			current = temporary;
		}

		return previous[left.length];
	}
}

package cn.simmc.simpricedisplay.market;

import cn.simmc.simpricedisplay.market.MarketDetails.DetailedOffer;
import cn.simmc.simpricedisplay.market.MarketDetails.ItemDetails;
import cn.simmc.simpricedisplay.market.MarketDetails.Side;
import cn.simmc.simpricedisplay.market.MarketModels.ItemMarketData;
import cn.simmc.simpricedisplay.market.MarketModels.Offer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class MarketDetailMerger {
	private MarketDetailMerger() {}

	static ItemDetails merge(ItemMarketData index, Instant indexTime, FullMarketSnapshot.Offers full, Instant fullTime,
			List<MarketSnapshot.PortAnchor> portAnchors) {
		List<DetailedOffer> sells = full == null ? new ArrayList<>() : new ArrayList<>(full.sells());
		List<DetailedOffer> buys = full == null ? new ArrayList<>() : new ArrayList<>(full.buys());
		sells.replaceAll(offer -> resolvePort(offer, portAnchors));
		buys.replaceAll(offer -> resolvePort(offer, portAnchors));
		mergeBest(sells, index.lowestSell(), Side.SELL, indexTime);
		mergeBest(buys, index.highestBuy(), Side.BUY, indexTime);
		sells.sort(Comparator.comparingDouble(DetailedOffer::price));
		buys.sort(Comparator.comparingDouble(DetailedOffer::price).reversed());
		return new ItemDetails(index.displayName(), limit(sells), limit(buys), indexTime, fullTime);
	}

	private static DetailedOffer resolvePort(DetailedOffer offer, List<MarketSnapshot.PortAnchor> anchors) {
		if (offer.port() != null && !offer.port().isBlank() && !"未知".equals(offer.port())) return offer;
		if (!offer.hasCoordinates()) return offer;
		MarketSnapshot.PortAnchor nearest = null;
		double nearestDistance = Double.POSITIVE_INFINITY;
		for (MarketSnapshot.PortAnchor anchor : anchors) {
			double dx = offer.x() - anchor.x();
			double dz = offer.z() - anchor.z();
			double distance = dx * dx + dz * dz;
			if (distance < nearestDistance) {
				nearestDistance = distance;
				nearest = anchor;
			}
		}
		if (nearest == null) return offer;
		return new DetailedOffer(offer.id(), offer.side(), offer.price(), offer.owner(), nearest.port(),
				offer.amount(), offer.x(), offer.y(), offer.z(), offer.updatedAt(), offer.latestIndex());
	}

	private static void mergeBest(List<DetailedOffer> values, Offer offer, Side side, Instant updatedAt) {
		if (offer == null) return;
		DetailedOffer latest = new DetailedOffer("index", side, offer.price(), offer.owner(), offer.port(),
				offer.amount(), offer.x(), Double.NaN, offer.z(), updatedAt, true);
		values.removeIf(old -> sameShop(old, latest));
		values.add(latest);
	}

	private static boolean sameShop(DetailedOffer left, DetailedOffer right) {
		return left.side() == right.side() && left.owner().equalsIgnoreCase(right.owner())
				&& Double.compare(left.x(), right.x()) == 0 && Double.compare(left.z(), right.z()) == 0;
	}

	private static List<DetailedOffer> limit(List<DetailedOffer> values) {
		return List.copyOf(values.subList(0, Math.min(5, values.size())));
	}
}

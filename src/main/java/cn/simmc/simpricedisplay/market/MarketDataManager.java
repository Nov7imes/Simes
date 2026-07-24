package cn.simmc.simpricedisplay.market;

import cn.simmc.simpricedisplay.market.MarketModels.MarketMatch;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;

public final class MarketDataManager implements AutoCloseable {
	private static final Logger LOGGER = LoggerFactory.getLogger("Simes/Data");
	private static final String MOD_VERSION = FabricLoader.getInstance().getModContainer("simes")
			.map(container -> container.getMetadata().getVersion().getFriendlyString())
			.orElse("unknown");
	private static final URI INDEX_URI = URI.create("https://s.eabal.com/BCShop/data/price_index.json");
	private static final URI SHOPS_URI = URI.create("https://s.eabal.com/BCShop/data/shops.json");
	private static final long MAX_INDEX_BYTES = 8L * 1024L * 1024L;
	private static final long MAX_SHOPS_BYTES = 64L * 1024L * 1024L;
	private static final int MIN_EXPECTED_ITEMS = 100;
	private static final long REFRESH_MILLIS = Duration.ofMinutes(10).toMillis();
	private static final long FULL_REFRESH_MILLIS = Duration.ofHours(1).toMillis();

	private final Path cacheDirectory = FabricLoader.getInstance().getConfigDir().resolve("simes");
	private final Path indexCache = cacheDirectory.resolve("price_index.json");
	private final Path metadataFile = cacheDirectory.resolve("http-cache.properties");
	private final Path shopsCache = cacheDirectory.resolve("shops.json");
	private final MarketParser parser = new MarketParser();
	private final FullMarketParser fullParser = new FullMarketParser();
	private final AtomicReference<MarketSnapshot> snapshot = new AtomicReference<>(MarketSnapshot.empty());
	private final AtomicReference<FullMarketSnapshot> fullSnapshot = new AtomicReference<>(FullMarketSnapshot.empty());
	private final ConcurrentHashMap<String, Optional<MarketDetails.ItemDetails>> detailsCache = new ConcurrentHashMap<>();
	private final AtomicLong snapshotRevision = new AtomicLong();
	private final AtomicBoolean activeOnTargetServer = new AtomicBoolean();
	private final AtomicBoolean refreshInProgress = new AtomicBoolean();
	private final AtomicLong lastRefreshAttemptMillis = new AtomicLong();
	private final AtomicLong lastFullRefreshAttemptMillis = new AtomicLong();
	private final ScheduledExecutorService ioExecutor = Executors.newSingleThreadScheduledExecutor(task -> {
		Thread thread = new Thread(task, "simes-market-io");
		thread.setDaemon(true);
		return thread;
	});
	private final HttpClient httpClient = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(15))
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();

	public void initialize() {
		ioExecutor.execute(this::loadCachedSnapshot);
		ioExecutor.scheduleWithFixedDelay(this::refreshIfDue, 30, 30, TimeUnit.SECONDS);
	}

	public void setActiveOnTargetServer(boolean active) {
		boolean wasActive = activeOnTargetServer.getAndSet(active);
		if (active && !wasActive) {
			ioExecutor.schedule(this::refreshIfDue, ThreadLocalRandom.current().nextLong(3, 31), TimeUnit.SECONDS);
		}
	}

	public boolean isActiveOnTargetServer() {
		return activeOnTargetServer.get();
	}

	public Optional<MarketMatch> find(String visibleName) {
		return snapshot.get().find(visibleName);
	}

	public Optional<MarketDetails.ItemDetails> details(String visibleName) {
		String cacheKey = ItemNameNormalizer.normalize(visibleName);
		if (cacheKey.isEmpty()) return Optional.empty();
		return detailsCache.computeIfAbsent(cacheKey, ignored -> detailsUncached(visibleName));
	}

	private Optional<MarketDetails.ItemDetails> detailsUncached(String visibleName) {
		MarketSnapshot current = snapshot.get();
		MarketModels.MarketMatch match = current.find(visibleName).orElse(null);
		if (match == null) return Optional.empty();
		FullMarketSnapshot full = fullSnapshot.get();
		FullMarketSnapshot.Offers offers = full.exact(match.data().displayName());
		return Optional.of(MarketDetailMerger.merge(match.data(), current.dataUpdatedAt(), offers, full.updatedAt(),
				current.portAnchors()));
	}

	public Optional<Double> estimatedBuyPrice(String visibleName) {
		MarketDetails.ItemDetails item = details(visibleName).orElse(null);
		if (item == null) return Optional.empty();
		return MarketValuation.buyMedianRanksTwoToFive(item.buys());
	}

	public Instant fullDataUpdatedAt() { return fullSnapshot.get().updatedAt(); }

	public Instant dataUpdatedAt() {
		return snapshot.get().dataUpdatedAt();
	}

	public int itemCount() {
		return snapshot.get().itemCount();
	}

	public long snapshotRevision() {
		return snapshotRevision.get();
	}

	MarketSnapshot snapshotForTests() {
		return snapshot.get();
	}

	private void loadCachedSnapshot() {
		try {
			Files.createDirectories(cacheDirectory);
		} catch (IOException exception) {
			LOGGER.warn("Could not create the Simes cache directory", exception);
			return;
		}
		if (Files.isRegularFile(indexCache)) {
			try {
				MarketSnapshot cached = parser.parse(indexCache);
				validateMarketScale(cached);
				installSnapshot(cached);
				LOGGER.info("Loaded {} cached Simall market items", cached.itemCount());
			} catch (Exception exception) {
				LOGGER.warn("Could not read the existing Simall price cache; waiting for a fresh update", exception);
			}
		}
		if (Files.isRegularFile(shopsCache)) {
			try {
				FullMarketSnapshot full = fullParser.parse(shopsCache);
				fullSnapshot.set(full);
				detailsCache.clear();
				LOGGER.info("Loaded {} cached Simall shop records", full.recordCount());
			} catch (Exception exception) {
				LOGGER.warn("Could not read the existing Simall shops cache; waiting for a fresh update", exception);
			}
		}
	}

	private void refreshIfDue() {
		if (!activeOnTargetServer.get()) {
			return;
		}
		long now = System.currentTimeMillis();
		long previousAttempt = lastRefreshAttemptMillis.get();
		long previousFullAttempt = lastFullRefreshAttemptMillis.get();
		boolean indexDue = previousAttempt == 0 || now - previousAttempt >= REFRESH_MILLIS;
		boolean fullDue = previousFullAttempt == 0 || now - previousFullAttempt >= FULL_REFRESH_MILLIS;
		if (!indexDue && !fullDue) return;
		if (!refreshInProgress.compareAndSet(false, true)) return;
		if (indexDue) lastRefreshAttemptMillis.set(now);
		if (fullDue) lastFullRefreshAttemptMillis.set(now);

		try {
			Files.createDirectories(cacheDirectory);
			if (indexDue) {
				try { refreshIndex(); }
				catch (InterruptedException exception) { Thread.currentThread().interrupt(); return; }
				catch (Exception exception) { LOGGER.warn("Simall price index update failed; keeping the previous cache", exception); }
			}
			if (fullDue) {
				try { refreshShops(); }
				catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
				catch (Exception exception) { LOGGER.warn("Simall shops update failed; keeping the previous cache", exception); }
			}
		} catch (IOException exception) {
			LOGGER.warn("Could not prepare the Simall cache directory", exception);
		} finally {
			refreshInProgress.set(false);
		}
	}

	private void refreshIndex() throws IOException, InterruptedException {
		Properties metadata = loadMetadata();
		HttpRequest.Builder request = baseRequest(INDEX_URI);
		if (!snapshot.get().isEmpty() && Files.isRegularFile(indexCache)) {
			String etag = metadata.getProperty("index.etag", "").strip();
			String lastModified = metadata.getProperty("index.lastModified", "").strip();
			if (!etag.isEmpty()) {
				request.header("If-None-Match", etag);
			}
			if (!lastModified.isEmpty()) {
				request.header("If-Modified-Since", lastModified);
			}
		}

		HttpResponse<InputStream> response = httpClient.send(
				request.GET().build(), HttpResponse.BodyHandlers.ofInputStream());
		try (InputStream body = response.body()) {
			if (response.statusCode() == 304) {
				LOGGER.debug("Simall price index has not changed");
				return;
			}
			if (response.statusCode() != 200) {
				throw new IOException("Unexpected HTTP " + response.statusCode() + " for " + INDEX_URI);
			}

			Path temporary = temporaryPathFor(indexCache);
			try {
				copyResponse(body, response, temporary, MAX_INDEX_BYTES, "price index");
				MarketSnapshot downloaded = parser.parse(temporary);
				validateMarketScale(downloaded);
				atomicReplace(temporary, indexCache);
				installSnapshot(downloaded);

				updateValidator(metadata, "index.etag", response.headers().firstValue("ETag"));
				updateValidator(metadata, "index.lastModified", response.headers().firstValue("Last-Modified"));
				saveMetadata(metadata);
				LOGGER.info("Updated {} Simall market items", downloaded.itemCount());
			} finally {
				Files.deleteIfExists(temporary);
			}
		}
	}

	private void refreshShops() throws IOException, InterruptedException {
		Properties metadata = loadMetadata();
		HttpRequest.Builder request = baseRequest(SHOPS_URI);
		if (!fullSnapshot.get().isEmpty() && Files.isRegularFile(shopsCache)) {
			String etag = metadata.getProperty("shops.etag", "").strip();
			String modified = metadata.getProperty("shops.lastModified", "").strip();
			if (!etag.isEmpty()) request.header("If-None-Match", etag);
			if (!modified.isEmpty()) request.header("If-Modified-Since", modified);
		}
		HttpResponse<InputStream> response = httpClient.send(request.GET().build(), HttpResponse.BodyHandlers.ofInputStream());
		try (InputStream body = response.body()) {
			if (response.statusCode() == 304) return;
			if (response.statusCode() != 200) throw new IOException("Unexpected HTTP " + response.statusCode() + " for " + SHOPS_URI);
			Path temporary = temporaryPathFor(shopsCache);
			try {
				copyResponse(body, response, temporary, MAX_SHOPS_BYTES, "shops data");
				FullMarketSnapshot downloaded = fullParser.parse(temporary);
				if (downloaded.recordCount() < MIN_EXPECTED_ITEMS) throw new IOException("Simall shops data is implausibly small");
				atomicReplace(temporary, shopsCache);
				fullSnapshot.set(downloaded);
				detailsCache.clear();
				snapshotRevision.incrementAndGet();
				updateValidator(metadata, "shops.etag", response.headers().firstValue("ETag"));
				updateValidator(metadata, "shops.lastModified", response.headers().firstValue("Last-Modified"));
				saveMetadata(metadata);
				LOGGER.info("Updated {} Simall shop records", downloaded.recordCount());
			} finally { Files.deleteIfExists(temporary); }
		}
	}

	private void installSnapshot(MarketSnapshot value) {
		snapshot.set(value);
		detailsCache.clear();
		snapshotRevision.incrementAndGet();
	}

	private HttpRequest.Builder baseRequest(URI uri) {
		return HttpRequest.newBuilder(uri)
				.timeout(Duration.ofSeconds(45))
				.header("Accept", "application/json")
				.header("Accept-Encoding", "gzip")
				.header("User-Agent", "Simes/" + MOD_VERSION + " (Minecraft Fabric; data source: Simall)");
	}

	private void copyResponse(InputStream rawBody, HttpResponse<?> response, Path target,
			long maximumBytes, String label) throws IOException {
		String encoding = response.headers().firstValue("Content-Encoding").orElse("");
		InputStream decoded = encoding.equalsIgnoreCase("gzip")
				? new GZIPInputStream(new BufferedInputStream(rawBody))
				: new BufferedInputStream(rawBody);
		try (InputStream input = decoded;
			 OutputStream output = Files.newOutputStream(target, StandardOpenOption.CREATE,
					 StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
			byte[] buffer = new byte[64 * 1024];
			long total = 0;
			int read;
			while ((read = input.read(buffer)) != -1) {
				total += read;
				if (total > maximumBytes) {
					throw new IOException("Downloaded Simall " + label + " exceeds the safety limit");
				}
				output.write(buffer, 0, read);
			}
			if (total == 0) {
				throw new IOException("Downloaded Simall " + label + " is empty");
			}
		}
	}

	private Properties loadMetadata() {
		Properties metadata = new Properties();
		if (!Files.isRegularFile(metadataFile)) {
			return metadata;
		}
		try (InputStream input = Files.newInputStream(metadataFile)) {
			metadata.load(input);
		} catch (IOException exception) {
			LOGGER.debug("Ignoring unreadable HTTP cache metadata", exception);
		}
		return metadata;
	}

	private void updateValidator(Properties metadata, String key, Optional<String> value) {
		if (value.isPresent() && !value.get().isBlank()) {
			metadata.setProperty(key, value.get());
		} else {
			metadata.remove(key);
		}
	}

	private void validateMarketScale(MarketSnapshot candidate) throws IOException {
		if (candidate.itemCount() < MIN_EXPECTED_ITEMS) {
			throw new IOException("Simall price index is implausibly small: " + candidate.itemCount() + " items");
		}
	}

	private void saveMetadata(Properties metadata) throws IOException {
		Path temporary = temporaryPathFor(metadataFile);
		try {
			try (OutputStream output = Files.newOutputStream(temporary, StandardOpenOption.CREATE,
					StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
				metadata.store(output, "Simes HTTP cache validators");
			}
			atomicReplace(temporary, metadataFile);
		} finally {
			Files.deleteIfExists(temporary);
		}
	}

	private Path temporaryPathFor(Path target) throws IOException {
		return Files.createTempFile(cacheDirectory, target.getFileName() + "-", ".tmp");
	}

	private void atomicReplace(Path source, Path target) throws IOException {
		try {
			Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (AtomicMoveNotSupportedException ignored) {
			Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	@Override
	public void close() {
		activeOnTargetServer.set(false);
		ioExecutor.shutdownNow();
	}
}

package cn.simmc.simpricedisplay;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Tracks boss bars whose ADD packet was deliberately hidden from vanilla. */
final class SuppressedBossBarIds {
	private final Set<UUID> ids = new HashSet<>();

	void suppress(UUID id) { ids.add(id); }
	boolean contains(UUID id) { return ids.contains(id); }
	boolean release(UUID id) { return ids.remove(id); }
	void clear() { ids.clear(); }
}

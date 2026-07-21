package cn.simmc.simpricedisplay;

import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;

public final class ServerGate {
	public static final String TARGET_HOST = "play.simmc.cn";

	private ServerGate() {
	}

	public static boolean isTarget(ServerInfo serverInfo) {
		return serverInfo != null && isTargetAddress(serverInfo.address);
	}

	static boolean isTargetAddress(String address) {
		if (address == null || !ServerAddress.isValid(address)) {
			return false;
		}

		String host = ServerAddress.parse(address).getAddress();
		while (host.endsWith(".")) {
			host = host.substring(0, host.length() - 1);
		}
		return TARGET_HOST.equalsIgnoreCase(host);
	}
}

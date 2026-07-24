package cn.simmc.simpricedisplay;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public final class ValuePanelController {
	private static KeyBinding toggleKey;
	private static long lastToggleAt;

	private ValuePanelController() {}

	public static void register() {
		SimesConfig.get();
		toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.simes.toggle_value_panel", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "category.simes"));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (toggleKey.wasPressed()) {
				toggle();
			}
		});
	}

	public static boolean handleKey(int keyCode, int scanCode) {
		if (toggleKey == null || !toggleKey.matchesKey(keyCode, scanCode)) return false;
		toggle();
		return true;
	}

	public static boolean handleMouse(int button) {
		if (toggleKey == null || !toggleKey.matchesMouse(button)) return false;
		toggle();
		return true;
	}

	private static void toggle() {
		long now = System.nanoTime();
		// A GUI key press can also reach KeyBinding.wasPressed(); debounce that duplicate path.
		if (now - lastToggleAt < 150_000_000L) return;
		lastToggleAt = now;
		SimesConfig config = SimesConfig.get();
		config.valuePanelEnabled = !config.valuePanelEnabled;
		config.save();
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player != null) {
			client.player.sendMessage(Text.literal(config.valuePanelEnabled
					? "§a[Simes] 背包/仓库价值显示已开启"
					: "§c[Simes] 背包/仓库价值显示已关闭"), true);
		}
	}

	public static boolean enabled() {
		return SimesConfig.get().valuePanelEnabled;
	}

	public static net.minecraft.text.Text boundKeyText() {
		return toggleKey == null ? net.minecraft.text.Text.literal("未绑定") : toggleKey.getBoundKeyLocalizedText();
	}

	public static KeyBinding keyBinding() { return toggleKey; }
}

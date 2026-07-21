package cn.simmc.simpricedisplay;

import java.util.Map;

public final class ArcaneColors {
	public record Palette(int primary, int secondary, int highlight) {}

	private static final Palette DEFAULT = new Palette(0xFFFFFF, 0xAAAAAA, 0xFFFFFF);
	private static final Map<String, Palette> COLORS = Map.ofEntries(
			Map.entry("混乱射线", new Palette(0xD92CFF, 0x7CFF4F, 0x32104F)),
			Map.entry("腾云术", new Palette(0xF4F7FF, 0xA9DCFF, 0xFFF1AE)),
			Map.entry("火球术", new Palette(0xFF5A1F, 0xFFB21C, 0xFFF3C4)),
			Map.entry("克敌先机", new Palette(0xFFD447, 0xFF9E2C, 0xFFF8D5)),
			Map.entry("引力术", new Palette(0x6B32D9, 0x281A64, 0x080712)),
			Map.entry("治愈术", new Palette(0x38D878, 0xA8FFB8, 0xF1FFF3)),
			Map.entry("治疗射线", new Palette(0x58F2C2, 0xFFECA0, 0xEFFFFA)),
			Map.entry("冰刃术", new Palette(0x70E5FF, 0x2489D8, 0xECFCFF)),
			Map.entry("寒冰吐息", new Palette(0xBCEFFF, 0xA8BFFF, 0xF5FDFF)),
			Map.entry("跳跃术", new Palette(0xA8F238, 0xF3FF55, 0xF8FFE2)),
			Map.entry("凌步术", new Palette(0x5865F2, 0x9A73FF, 0xE5E9FF)),
			Map.entry("雷击", new Palette(0xFFE94A, 0x76DFFF, 0xFFFFFF)),
			Map.entry("斥力术", new Palette(0x8EAAC4, 0x70D7FF, 0xF2FBFF)),
			Map.entry("激流术", new Palette(0x168CD8, 0x20D6DC, 0xE6FFFF)),
			Map.entry("蜘化术", new Palette(0x54205F, 0xB82E46, 0x72C94A)),
			Map.entry("火焰吐息", new Palette(0xE93224, 0xFF8A22, 0x3D2824)),
			Map.entry("火陨术", new Palette(0x9E1B18, 0xFFB000, 0x24120E)),
			Map.entry("御风术", new Palette(0x55D9C0, 0xA6F2E5, 0xF2FFFD)),
			Map.entry("雷电射线", new Palette(0x715CFF, 0x27E5FF, 0xFFFFFF)),
			Map.entry("后撤步", new Palette(0x557784, 0x2A4F59, 0xDDECEF))
	);

	private ArcaneColors() {}
	public static Palette forName(String name) { return COLORS.getOrDefault(name, DEFAULT); }
}

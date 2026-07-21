package cn.simmc.simpricedisplay;

import cn.ni.automessage.AutoMessageClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public final class HudLayoutScreen extends Screen {
	private enum Target { ARCANE, AUTO_MESSAGE }
	private final Screen parent;
	private Target selected = Target.ARCANE;
	private boolean dragging;
	private double offsetX;
	private double offsetY;
	private ButtonWidget scaleButton;

	public HudLayoutScreen(Screen parent) {
		super(Text.literal("Simes HUD 布局"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int y = height - 28;
		addDrawableChild(ButtonWidget.builder(Text.literal("−10%"), b -> changeScale(-10))
				.dimensions(width / 2 - 154, y, 48, 20).build());
		scaleButton = addDrawableChild(ButtonWidget.builder(scaleText(), b -> changeScale(10))
				.dimensions(width / 2 - 102, y, 98, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("+10%"), b -> changeScale(10))
				.dimensions(width / 2, y, 48, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("重置全部"), b -> resetAll())
				.dimensions(width / 2 + 52, y, 68, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("完成"), b -> close())
				.dimensions(width / 2 + 124, y, 48, 20).build());
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(textRenderer, Text.literal("点击框选择 HUD，拖动改变位置；底部调整所选 HUD 的缩放"), width / 2, 10, 0xFFFFFFFF);
		drawArcane(context);
		drawAutoMessage(context);
	}

	private void drawArcane(DrawContext context) {
		int x = ArcaneCooldownHud.configuredX(width);
		int y = ArcaneCooldownHud.configuredY(height);
		float scale = ArcaneCooldownHud.config().scalePercent / 100.0f;
		ArcaneCooldownHud.renderPreview(context, x, y, scale);
		int w = Math.round(ArcaneCooldownHud.totalWidth() * scale);
		int h = Math.round(3 * 19 * scale);
		context.drawBorder(x - 3, y - h - 3, w + 6, h + 7, selected == Target.ARCANE ? 0xFFFFFF55 : 0xFF777777);
		context.drawTextWithShadow(textRenderer, "奥术 CD " + ArcaneCooldownHud.config().scalePercent + "%", x, Math.max(22, y - h - 14), 0xFFFFFFFF);
	}

	private void drawAutoMessage(DrawContext context) {
		int x = AutoMessageClient.configuredX(width);
		int y = AutoMessageClient.configuredY(height);
		float scale = ArcaneCooldownHud.config().autoMessageScalePercent / 100.0f;
		AutoMessageClient.renderPreview(context, x, y, scale);
		int w = Math.round(AutoMessageClient.previewWidth() * scale);
		int h = Math.round(AutoMessageClient.previewHeight() * scale);
		context.drawBorder(x - 3, y - 3, w + 6, h + 6, selected == Target.AUTO_MESSAGE ? 0xFFFFFF55 : 0xFF777777);
		context.drawTextWithShadow(textRenderer, "自动消息 " + ArcaneCooldownHud.config().autoMessageScalePercent + "%", x, Math.max(22, y - 14), 0xFFFFFFFF);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0 && selectAt(mouseX, mouseY)) {
			dragging = true;
			int x = selected == Target.ARCANE ? ArcaneCooldownHud.configuredX(width) : AutoMessageClient.configuredX(width);
			int y = selected == Target.ARCANE ? ArcaneCooldownHud.configuredY(height) : AutoMessageClient.configuredY(height);
			offsetX = mouseX - x;
			offsetY = mouseY - y;
			scaleButton.setMessage(scaleText());
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	private boolean selectAt(double mx, double my) {
		float autoScale = ArcaneCooldownHud.config().autoMessageScalePercent / 100.0f;
		int ax = AutoMessageClient.configuredX(width), ay = AutoMessageClient.configuredY(height);
		int aw = Math.round(AutoMessageClient.previewWidth() * autoScale), ah = Math.round(AutoMessageClient.previewHeight() * autoScale);
		if (mx >= ax - 4 && mx <= ax + aw + 4 && my >= ay - 4 && my <= ay + ah + 4) { selected = Target.AUTO_MESSAGE; return true; }
		float arcScale = ArcaneCooldownHud.config().scalePercent / 100.0f;
		int x = ArcaneCooldownHud.configuredX(width), y = ArcaneCooldownHud.configuredY(height);
		int w = Math.round(ArcaneCooldownHud.totalWidth() * arcScale), h = Math.round(3 * 19 * arcScale);
		if (mx >= x - 4 && mx <= x + w + 4 && my >= y - h - 4 && my <= y + 4) { selected = Target.ARCANE; return true; }
		return false;
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
		if (!dragging || button != 0) return super.mouseDragged(mouseX, mouseY, button, dx, dy);
		ArcaneHudConfig config = ArcaneCooldownHud.config();
		if (selected == Target.ARCANE) {
			float scale = config.scalePercent / 100.0f;
			int w = Math.round(ArcaneCooldownHud.totalWidth() * scale), h = Math.round(3 * 19 * scale);
			double x = clamp(mouseX - offsetX, 2, width - w - 2);
			double y = clamp(mouseY - offsetY, h + 2, height - 35);
			config.x = x / width; config.y = y / height;
		} else {
			float scale = config.autoMessageScalePercent / 100.0f;
			int w = Math.round(AutoMessageClient.previewWidth() * scale), h = Math.round(AutoMessageClient.previewHeight() * scale);
			double x = clamp(mouseX - offsetX, 2, width - w - 2);
			double y = clamp(mouseY - offsetY, 22, height - h - 35);
			config.autoMessageX = x / width; config.autoMessageY = y / height;
		}
		return true;
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (button == 0 && dragging) { dragging = false; ArcaneCooldownHud.config().save(); return true; }
		return super.mouseReleased(mouseX, mouseY, button);
	}

	private void changeScale(int delta) {
		ArcaneHudConfig config = ArcaneCooldownHud.config();
		if (selected == Target.ARCANE) config.scalePercent = clamp(config.scalePercent + delta, 50, 200);
		else config.autoMessageScalePercent = clamp(config.autoMessageScalePercent + delta, 50, 200);
		config.save();
		scaleButton.setMessage(scaleText());
	}

	private Text scaleText() {
		int value = selected == Target.ARCANE ? ArcaneCooldownHud.config().scalePercent : ArcaneCooldownHud.config().autoMessageScalePercent;
		return Text.literal(value + "%（点击+10）");
	}

	private void resetAll() {
		ArcaneHudConfig config = ArcaneCooldownHud.config();
		config.resetPosition(); config.resetAutoMessagePosition();
		config.scalePercent = 100; config.autoMessageScalePercent = 100;
		config.save(); scaleButton.setMessage(scaleText());
	}

	private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
	private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
	@Override public void close() { ArcaneCooldownHud.config().save(); client.setScreen(parent); }
	@Override public boolean shouldPause() { return false; }
}

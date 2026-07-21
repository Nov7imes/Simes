package cn.simmc.simpricedisplay;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public final class ArcaneHudPositionScreen extends Screen {
	private final Screen parent;
	private boolean dragging;
	private double dragOffsetX;
	private double dragOffsetY;

	public ArcaneHudPositionScreen(Screen parent) {
		super(Text.literal("调整奥术冷却 HUD 位置"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		addDrawableChild(ButtonWidget.builder(Text.literal("重置"), button -> {
			ArcaneCooldownHud.config().resetPosition();
			ArcaneCooldownHud.config().save();
		}).dimensions(width / 2 - 102, height - 28, 98, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("保存"), button -> close())
				.dimensions(width / 2 + 4, height - 28, 98, 20).build());
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(textRenderer, Text.literal("拖动预览区域调整位置"),
				width / 2, 12, 0xFFFFFFFF);
		int x = ArcaneCooldownHud.configuredX(width);
		int y = ArcaneCooldownHud.configuredY(height);
		float scale = ArcaneCooldownHud.config().scalePercent / 100.0f;
		ArcaneCooldownHud.renderPreview(context, x, y, scale);
		int previewWidth = Math.round(ArcaneCooldownHud.totalWidth() * scale);
		int previewHeight = Math.round(3 * 19 * scale);
		context.drawBorder(x - 2, y - previewHeight - 2, previewWidth + 4, previewHeight + 5,
				dragging ? 0xFFFFFF55 : 0xFF888888);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0) {
			int x = ArcaneCooldownHud.configuredX(width);
			int y = ArcaneCooldownHud.configuredY(height);
			float scale = ArcaneCooldownHud.config().scalePercent / 100.0f;
			int w = Math.round(ArcaneCooldownHud.totalWidth() * scale);
			int h = Math.round(3 * 19 * scale);
			if (mouseX >= x - 4 && mouseX <= x + w + 4 && mouseY >= y - h - 4 && mouseY <= y + 4) {
				dragging = true;
				dragOffsetX = mouseX - x;
				dragOffsetY = mouseY - y;
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (dragging && button == 0) {
			float scale = ArcaneCooldownHud.config().scalePercent / 100.0f;
			int w = Math.round(ArcaneCooldownHud.totalWidth() * scale);
			int h = Math.round(3 * 19 * scale);
			double x = Math.max(2, Math.min(width - w - 2, mouseX - dragOffsetX));
			double y = Math.max(h + 2, Math.min(height - 35, mouseY - dragOffsetY));
			ArcaneCooldownHud.config().x = x / width;
			ArcaneCooldownHud.config().y = y / height;
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (button == 0 && dragging) {
			dragging = false;
			ArcaneCooldownHud.config().save();
			return true;
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override public void close() { ArcaneCooldownHud.config().save(); client.setScreen(parent); }
	@Override public boolean shouldPause() { return false; }
}

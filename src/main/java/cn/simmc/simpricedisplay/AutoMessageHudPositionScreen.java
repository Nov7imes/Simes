package cn.simmc.simpricedisplay;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public final class AutoMessageHudPositionScreen extends Screen {
	private final Screen parent;
	private boolean dragging;
	private double dragOffsetX;
	private double dragOffsetY;

	public AutoMessageHudPositionScreen(Screen parent) {
		super(Text.literal("调整自动消息 HUD 位置"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		addDrawableChild(ButtonWidget.builder(Text.literal("重置"), button -> {
			ArcaneCooldownHud.config().resetAutoMessagePosition();
			ArcaneCooldownHud.config().save();
		}).dimensions(width / 2 - 102, height - 28, 98, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("保存"), button -> close())
				.dimensions(width / 2 + 4, height - 28, 98, 20).build());
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(textRenderer, Text.literal("拖动预览栏调整位置"), width / 2, 12, 0xFFFFFFFF);
		int x = AutoMessageModule.configuredX(width);
		int y = AutoMessageModule.configuredY(height);
		float scale = ArcaneCooldownHud.config().autoMessageScalePercent / 100.0f;
		AutoMessageModule.renderPreview(context, x, y, scale);
		context.drawBorder(x - 2, y - 2,
				Math.round(AutoMessageModule.previewWidth() * scale) + 4,
				Math.round(AutoMessageModule.previewHeight() * scale) + 4,
				dragging ? 0xFFFFFF55 : 0xFF888888);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0) {
			int x = AutoMessageModule.configuredX(width);
			int y = AutoMessageModule.configuredY(height);
			float scale = ArcaneCooldownHud.config().autoMessageScalePercent / 100.0f;
			int w = Math.round(AutoMessageModule.previewWidth() * scale);
			int h = Math.round(AutoMessageModule.previewHeight() * scale);
			if (mouseX >= x - 4 && mouseX <= x + w + 4 && mouseY >= y - 4 && mouseY <= y + h + 4) {
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
			float scale = ArcaneCooldownHud.config().autoMessageScalePercent / 100.0f;
			int w = Math.round(AutoMessageModule.previewWidth() * scale);
			int h = Math.round(AutoMessageModule.previewHeight() * scale);
			double x = Math.max(2, Math.min(width - w - 2, mouseX - dragOffsetX));
			double y = Math.max(2, Math.min(height - h - 35, mouseY - dragOffsetY));
			ArcaneCooldownHud.config().autoMessageX = x / width;
			ArcaneCooldownHud.config().autoMessageY = y / height;
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

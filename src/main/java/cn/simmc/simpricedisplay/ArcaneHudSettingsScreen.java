package cn.simmc.simpricedisplay;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public final class ArcaneHudSettingsScreen extends Screen {
	private final Screen parent;

	public ArcaneHudSettingsScreen(Screen parent) {
		super(Text.literal("Simes 奥术冷却 HUD"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int center = width / 2;
		int top = Math.max(45, height / 2 - 75);
		addDrawableChild(ButtonWidget.builder(modeText(), button -> {
			ArcaneHudConfig config = ArcaneCooldownHud.config();
			config.simesMode = !config.simesMode;
			config.save();
			button.setMessage(modeText());
		}).dimensions(center - 100, top, 200, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("HUD 布局与缩放"), button ->
				client.setScreen(new HudLayoutScreen(this)))
				.dimensions(center - 100, top + 34, 200, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), button -> close())
				.dimensions(center - 100, top + 68, 200, 20).build());
	}

	private Text modeText() {
		return Text.literal("冷却显示：" + (ArcaneCooldownHud.config().simesMode ? "Simes 冷却条" : "原版 Action Bar"));
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(textRenderer, Text.literal("Simes HUD 设置"), width / 2, Math.max(10, height / 2 - 145), 0xFFFFFFFF);
		context.drawCenteredTextWithShadow(textRenderer,
				Text.literal("在 HUD 布局中可同时拖动奥术与自动消息 HUD，并分别设置缩放"),
				width / 2, Math.max(22, height / 2 - 131), 0xFFAAAAAA);
	}

	@Override
	public void close() {
		ArcaneCooldownHud.config().save();
		client.setScreen(parent);
	}

	@Override public boolean shouldPause() { return false; }
}

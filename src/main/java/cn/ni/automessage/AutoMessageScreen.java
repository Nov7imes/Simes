package cn.ni.automessage;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public final class AutoMessageScreen extends Screen {
	private TextFieldWidget messageField;
	private TextFieldWidget intervalField;
	private Text notice = Text.empty();
	private int noticeColor = 0xFFFFFFFF;

	public AutoMessageScreen() {
		super(Text.translatable("screen.automessage.title"));
	}

	@Override
	protected void init() {
		int panelWidth = Math.min(360, width - 40);
		int left = (width - panelWidth) / 2;
		int top = Math.max(45, height / 2 - 90);
		int labelWidth = 45;

		messageField = new TextFieldWidget(textRenderer, left + labelWidth, top + 35,
				panelWidth - labelWidth, 20, Text.translatable("screen.automessage.message"));
		messageField.setMaxLength(256);
		messageField.setText(AutoMessageClient.config().message);
		addDrawableChild(messageField);

		intervalField = new TextFieldWidget(textRenderer, left + labelWidth, top + 80,
				110, 20, Text.translatable("screen.automessage.interval"));
		intervalField.setMaxLength(5);
		intervalField.setText(Integer.toString(AutoMessageClient.config().intervalSeconds));
		intervalField.setTextPredicate(value -> value.isEmpty() || value.chars().allMatch(Character::isDigit));
		addDrawableChild(intervalField);

		int buttonsY = top + 120;
		addDrawableChild(ButtonWidget.builder(Text.literal("保存并返回"), button -> {
			if (save()) close();
		}).dimensions(left + (panelWidth - 160) / 2, buttonsY, 160, 20).build());
		setInitialFocus(messageField);
	}

	private boolean save() {
		String message = messageField.getText().trim();
		int interval;
		try {
			interval = Integer.parseInt(intervalField.getText());
		} catch (NumberFormatException exception) {
			interval = 0;
		}
		if (message.isEmpty() || interval < 1 || interval > 86400) {
			notice = Text.translatable("screen.automessage.invalid");
			noticeColor = 0xFFFF5555;
			return false;
		}
		AutoMessageClient.saveSettings(message, interval);
		notice = Text.translatable("screen.automessage.saved");
		noticeColor = 0xFF55FF55;
		return true;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		int panelWidth = Math.min(360, width - 40);
		int left = (width - panelWidth) / 2;
		int top = Math.max(45, height / 2 - 90);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, top, 0xFFFFFFFF);
		context.drawTextWithShadow(textRenderer, Text.translatable("screen.automessage.message"),
				left, top + 41, 0xFFFFFFFF);
		context.drawTextWithShadow(textRenderer, Text.translatable("screen.automessage.interval"),
				left, top + 86, 0xFFFFFFFF);
		Text status = AutoMessageClient.isEnabled()
				? Text.translatable("screen.automessage.status.running", AutoMessageClient.secondsUntilNextSend())
				: Text.translatable("screen.automessage.status.stopped");
		context.drawCenteredTextWithShadow(textRenderer, status, width / 2, top + 106,
				AutoMessageClient.isEnabled() ? 0xFF55FF55 : 0xFFFF5555);
		context.drawCenteredTextWithShadow(textRenderer, notice, width / 2, top + 150, noticeColor);
		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}

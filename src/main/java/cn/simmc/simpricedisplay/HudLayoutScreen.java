package cn.simmc.simpricedisplay;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public final class HudLayoutScreen extends Screen {
	private enum Target { ARCANE, ARCANE_STATUS, GLOBAL_COOLDOWN, MANA, AUTO_MESSAGE, INVENTORY_VALUE, WAREHOUSE_VALUE }
	private final Screen parent;
	private Target selected = Target.WAREHOUSE_VALUE;
	private boolean dragging;
	private double offsetX;
	private double offsetY;
	private ButtonWidget scaleButton;
	private int guideX = -1;
	private int guideY = -1;
	private boolean warehouseReference = true;

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

	private void addTargetButton(String label, Target target, int x, int y, int width) {
		addDrawableChild(ButtonWidget.builder(Text.literal(label), b -> selectTarget(target))
				.dimensions(x, y, width, 20).build());
	}

	private void selectTarget(Target target) {
		selected = target;
		warehouseReference = target == Target.WAREHOUSE_VALUE;
		dragging = false;
		if (scaleButton != null) scaleButton.setMessage(scaleText());
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		drawWarehouseReference(context);
		drawArcane(context);
		drawArcaneStatus(context);
		drawGlobalCooldown(context);
		drawMana(context);
		drawAutoMessage(context);
		drawValuePanels(context);
		if (dragging) drawAlignmentGuides(context);
	}

	private void drawMana(DrawContext context) {
		int x = ManaHud.configuredX(width), y = ManaHud.configuredY(height);
		float scale = ArcaneCooldownHud.config().manaHudScalePercent / 100.0f;
		ManaHud.renderPreview(context, x, y, scale);
		int w = Math.round(ManaHud.totalWidth() * scale), h = Math.round(ManaHud.totalHeight() * scale);
		context.drawBorder(x - 3, y - 3, w + 6, h + 6, selected == Target.MANA ? 0xFFFFFF55 : 0xFF777777);
		context.drawTextWithShadow(textRenderer, "Mana HUD " + ArcaneCooldownHud.config().manaHudScalePercent + "%",
				x, Math.max(22, y - 14), 0xFFFFFFFF);
	}

	private void drawValuePanels(DrawContext context) {
		int warehouseX = ValuePanelRenderer.configuredContainerPreviewX(width);
		int warehouseY = ValuePanelRenderer.configuredContainerPreviewY(height);
		float warehouseScale = ValuePanelRenderer.containerValueScale();
		ValuePanelRenderer.renderContainerPreview(context, warehouseX, warehouseY, warehouseScale);
		int warehouseWidth = Math.round(ValuePanelRenderer.previewWidth() * warehouseScale);
		int warehouseHeight = Math.round(ValuePanelRenderer.containerPreviewHeight() * warehouseScale);
		context.drawBorder(warehouseX - 3, warehouseY - 3, warehouseWidth + 6, warehouseHeight + 6,
				selected == Target.WAREHOUSE_VALUE ? 0xFFFFFF55 : 0xFF777777);
		context.drawTextWithShadow(textRenderer, "仓库价值 "
				+ ArcaneCooldownHud.config().containerValueScalePercent + "%", warehouseX, Math.max(56, warehouseY - 14), 0xFFFFFFFF);
		int x = ValuePanelRenderer.configuredPreviewX(width);
		int y = ValuePanelRenderer.configuredPreviewY(height);
		float scale = ValuePanelRenderer.valueScale();
		ValuePanelRenderer.renderPreview(context, x, y, scale);
		int w = Math.round(ValuePanelRenderer.previewWidth() * scale);
		int h = Math.round(ValuePanelRenderer.previewHeight() * scale);
		context.drawBorder(x - 3, y - 3, w + 6, h + 6,
				selected == Target.INVENTORY_VALUE ? 0xFFFFFF55 : 0xFF777777);
		context.drawTextWithShadow(textRenderer, "背包价值 "
				+ ArcaneCooldownHud.config().valuePanelScalePercent + "%", x, Math.max(22, y - 14), 0xFFFFFFFF);
	}

	private void selectValueTarget(Target target) {
		selectTarget(target);
	}

	private void drawInventoryReference(DrawContext context) {
		int guiW = 176, guiH = 166, left = (width - guiW) / 2, top = (height - guiH) / 2;
		drawMockWindow(context, left, top, guiW, guiH, "背包界面定位参考");
		drawSlotGrid(context, left + 7, top + 83, 9, 3);
		drawSlotGrid(context, left + 7, top + 141, 9, 1);
	}

	private void drawWarehouseReference(DrawContext context) {
		int guiW = 176, guiH = 222, left = (width - guiW) / 2, top = Math.max(56, (height - guiH) / 2);
		drawMockWindow(context, left, top, guiW, guiH, "双箱/木桶界面定位参考");
		drawSlotGrid(context, left + 7, top + 18, 9, 6);
		drawSlotGrid(context, left + 7, top + 140, 9, 3);
		drawSlotGrid(context, left + 7, top + 198, 9, 1);
	}

	private void drawMockWindow(DrawContext context, int x, int y, int w, int h, String title) {
		context.fill(x, y, x + w, y + h, 0xD0C6C6C6);
		context.drawBorder(x, y, w, h, 0xFF404040);
		context.drawTextWithShadow(textRenderer, title, x + 7, y + 6, 0xFF303030);
	}

	private void drawSlotGrid(DrawContext context, int x, int y, int columns, int rows) {
		for (int row = 0; row < rows; row++) for (int col = 0; col < columns; col++) {
			int sx = x + col * 18, sy = y + row * 18;
			context.fill(sx, sy, sx + 18, sy + 18, 0xFF8B8B8B);
			context.drawBorder(sx, sy, 18, 18, 0xFFEEEEEE);
		}
	}

	private void drawAlignmentGuides(DrawContext context) {
		if (guideX >= 0) context.fill(guideX, 54, guideX + 1, height - 34, 0xFFFF55FF);
		if (guideY >= 0) context.fill(0, guideY, width, guideY + 1, 0xFFFF55FF);
	}

	private void drawGlobalCooldown(DrawContext context) {
		int x = ArcaneStatusHud.configuredGlobalX(width);
		int y = ArcaneStatusHud.configuredGlobalY(height);
		float scale = ArcaneCooldownHud.config().globalCooldownScalePercent / 100.0f;
		ArcaneStatusHud.renderGlobalPreview(context, x, y, scale);
		int w = Math.round(ArcaneStatusHud.globalTotalWidth() * scale);
		int h = Math.round(ArcaneStatusHud.globalPreviewHeight() * scale);
		context.drawBorder(x - 3, y - h - 3, w + 6, h + 7,
				selected == Target.GLOBAL_COOLDOWN ? 0xFFFFFF55 : 0xFF777777);
		context.drawTextWithShadow(textRenderer, "公共冷却 "
				+ ArcaneCooldownHud.config().globalCooldownScalePercent + "%", x, Math.max(22, y - h - 14), 0xFFFFFFFF);
	}

	private void drawArcaneStatus(DrawContext context) {
		int x = ArcaneStatusHud.configuredX(width);
		int y = ArcaneStatusHud.configuredY(height);
		float scale = ArcaneCooldownHud.config().arcaneStatusScalePercent / 100.0f;
		ArcaneStatusHud.renderPreview(context, x, y, scale);
		int w = Math.round(ArcaneStatusHud.totalWidth() * scale);
		int h = Math.round(ArcaneStatusHud.previewHeight() * scale);
		context.drawBorder(x - 3, y - h - 3, w + 6, h + 7,
				selected == Target.ARCANE_STATUS ? 0xFFFFFF55 : 0xFF777777);
		context.drawTextWithShadow(textRenderer, "吟唱/持续状态 "
				+ ArcaneCooldownHud.config().arcaneStatusScalePercent + "%", x, Math.max(22, y - h - 14), 0xFFFFFFFF);
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
		int x = AutoMessageModule.configuredX(width);
		int y = AutoMessageModule.configuredY(height);
		float scale = ArcaneCooldownHud.config().autoMessageScalePercent / 100.0f;
		AutoMessageModule.renderPreview(context, x, y, scale);
		int w = Math.round(AutoMessageModule.previewWidth() * scale);
		int h = Math.round(AutoMessageModule.previewHeight() * scale);
		context.drawBorder(x - 3, y - 3, w + 6, h + 6, selected == Target.AUTO_MESSAGE ? 0xFFFFFF55 : 0xFF777777);
		context.drawTextWithShadow(textRenderer, "自动消息 " + ArcaneCooldownHud.config().autoMessageScalePercent + "%", x, Math.max(22, y - 14), 0xFFFFFFFF);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (super.mouseClicked(mouseX, mouseY, button)) return true;
		if (button == 0 && selectAt(mouseX, mouseY)) {
			dragging = true;
			int x = selectedX();
			int y = selectedY();
			offsetX = mouseX - x;
			offsetY = mouseY - y;
			scaleButton.setMessage(scaleText());
			return true;
		}
		return false;
	}

	private boolean selectAt(double mx, double my) {
		float warehouseScale = ValuePanelRenderer.containerValueScale();
		int warehouseX = ValuePanelRenderer.configuredContainerPreviewX(width);
		int warehouseY = ValuePanelRenderer.configuredContainerPreviewY(height);
		int warehouseWidth = Math.round(ValuePanelRenderer.previewWidth() * warehouseScale);
		int warehouseHeight = Math.round(ValuePanelRenderer.containerPreviewHeight() * warehouseScale);
		if (inside(mx, my, warehouseX, warehouseY, warehouseWidth, warehouseHeight)) {
			selectTarget(Target.WAREHOUSE_VALUE);
			return true;
		}
		float valueScale = ValuePanelRenderer.valueScale();
		int valueX = ValuePanelRenderer.configuredPreviewX(width);
		int valueY = ValuePanelRenderer.configuredPreviewY(height);
		int valueWidth = Math.round(ValuePanelRenderer.previewWidth() * valueScale);
		int valueHeight = Math.round(ValuePanelRenderer.previewHeight() * valueScale);
		if (inside(mx, my, valueX, valueY, valueWidth, valueHeight)) {
			selectTarget(Target.INVENTORY_VALUE);
			return true;
		}
		float globalScale = ArcaneCooldownHud.config().globalCooldownScalePercent / 100.0f;
		int globalX = ArcaneStatusHud.configuredGlobalX(width);
		int globalY = ArcaneStatusHud.configuredGlobalY(height);
		int globalWidth = Math.round(ArcaneStatusHud.globalTotalWidth() * globalScale);
		int globalHeight = Math.round(ArcaneStatusHud.globalPreviewHeight() * globalScale);
		if (mx >= globalX - 4 && mx <= globalX + globalWidth + 4
				&& my >= globalY - globalHeight - 4 && my <= globalY + 4) {
			selectTarget(Target.GLOBAL_COOLDOWN);
			return true;
		}
		float statusScale = ArcaneCooldownHud.config().arcaneStatusScalePercent / 100.0f;
		int statusX = ArcaneStatusHud.configuredX(width);
		int statusY = ArcaneStatusHud.configuredY(height);
		int statusWidth = Math.round(ArcaneStatusHud.totalWidth() * statusScale);
		int statusHeight = Math.round(ArcaneStatusHud.previewHeight() * statusScale);
		if (mx >= statusX - 4 && mx <= statusX + statusWidth + 4
				&& my >= statusY - statusHeight - 4 && my <= statusY + 4) {
			selectTarget(Target.ARCANE_STATUS);
			return true;
		}
		float manaScale = ArcaneCooldownHud.config().manaHudScalePercent / 100.0f;
		int manaX = ManaHud.configuredX(width);
		int manaY = ManaHud.configuredY(height);
		int manaWidth = Math.round(ManaHud.totalWidth() * manaScale);
		int manaHeight = Math.round(ManaHud.totalHeight() * manaScale);
		if (inside(mx, my, manaX, manaY, manaWidth, manaHeight)) {
			selectTarget(Target.MANA);
			return true;
		}
		float autoScale = ArcaneCooldownHud.config().autoMessageScalePercent / 100.0f;
		int autoX = AutoMessageModule.configuredX(width);
		int autoY = AutoMessageModule.configuredY(height);
		int autoWidth = Math.round(AutoMessageModule.previewWidth() * autoScale);
		int autoHeight = Math.round(AutoMessageModule.previewHeight() * autoScale);
		if (inside(mx, my, autoX, autoY, autoWidth, autoHeight)) {
			selectTarget(Target.AUTO_MESSAGE);
			return true;
		}
		float arcaneScale = ArcaneCooldownHud.config().scalePercent / 100.0f;
		int arcaneX = ArcaneCooldownHud.configuredX(width);
		int arcaneY = ArcaneCooldownHud.configuredY(height);
		int arcaneWidth = Math.round(ArcaneCooldownHud.totalWidth() * arcaneScale);
		int arcaneHeight = Math.round(3 * 19 * arcaneScale);
		if (mx >= arcaneX - 4 && mx <= arcaneX + arcaneWidth + 4
				&& my >= arcaneY - arcaneHeight - 4 && my <= arcaneY + 4) {
			selectTarget(Target.ARCANE);
			return true;
		}
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
		} else if (selected == Target.ARCANE_STATUS) {
			float scale = config.arcaneStatusScalePercent / 100.0f;
			int w = Math.round(ArcaneStatusHud.totalWidth() * scale);
			int h = Math.round(ArcaneStatusHud.previewHeight() * scale);
			double x = clamp(mouseX - offsetX, 2, width - w - 2);
			double y = clamp(mouseY - offsetY, h + 2, height - 35);
			config.arcaneStatusX = x / width; config.arcaneStatusY = y / height;
		} else if (selected == Target.GLOBAL_COOLDOWN) {
			float scale = config.globalCooldownScalePercent / 100.0f;
			int w = Math.round(ArcaneStatusHud.globalTotalWidth() * scale);
			int h = Math.round(ArcaneStatusHud.globalPreviewHeight() * scale);
			double x = clamp(mouseX - offsetX, 2, width - w - 2);
			double y = clamp(mouseY - offsetY, h + 2, height - 35);
			config.globalCooldownX = x / width; config.globalCooldownY = y / height;
		} else if (selected == Target.AUTO_MESSAGE) {
			float scale = config.autoMessageScalePercent / 100.0f;
			int w = Math.round(AutoMessageModule.previewWidth() * scale), h = Math.round(AutoMessageModule.previewHeight() * scale);
			double x = clamp(mouseX - offsetX, 2, width - w - 2);
			double y = clamp(mouseY - offsetY, 22, height - h - 35);
			config.autoMessageX = x / width; config.autoMessageY = y / height;
		} else if (selected == Target.MANA) {
			float scale = config.manaHudScalePercent / 100.0f;
			int w = Math.round(ManaHud.totalWidth() * scale), h = Math.round(ManaHud.totalHeight() * scale);
			double x = clamp(mouseX - offsetX, 2, width - w - 2);
			double y = clamp(mouseY - offsetY, 22, height - h - 35);
			config.manaHudX = x / width; config.manaHudY = y / height;
		} else if (selected == Target.INVENTORY_VALUE) {
			float scale = config.valuePanelScalePercent / 100.0f;
			int w = Math.round(ValuePanelRenderer.previewWidth() * scale);
			int h = Math.round(ValuePanelRenderer.previewHeight() * scale);
			double[] point = snapValuePanel(mouseX - offsetX, mouseY - offsetY, w, h, warehouseReference);
			double x = point[0], y = point[1];
			config.valuePanelX = x / width; config.valuePanelY = y / height;
		} else if (selected == Target.WAREHOUSE_VALUE) {
			float scale = config.containerValueScalePercent / 100.0f;
			int w = Math.round(ValuePanelRenderer.previewWidth() * scale);
			int h = Math.round(ValuePanelRenderer.containerPreviewHeight() * scale);
			double[] point = snapValuePanel(mouseX - offsetX, mouseY - offsetY, w, h, true);
			config.containerValueX = point[0] / width; config.containerValueY = point[1] / height;
		}
		return true;
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (button == 0 && dragging) { dragging = false; guideX = guideY = -1; ArcaneCooldownHud.config().save(); return true; }
		return super.mouseReleased(mouseX, mouseY, button);
	}

	private void changeScale(int delta) {
		ArcaneHudConfig config = ArcaneCooldownHud.config();
		if (selected == Target.ARCANE) config.scalePercent = clamp(config.scalePercent + delta, 50, 200);
		else if (selected == Target.ARCANE_STATUS) config.arcaneStatusScalePercent = clamp(config.arcaneStatusScalePercent + delta, 50, 200);
		else if (selected == Target.GLOBAL_COOLDOWN) config.globalCooldownScalePercent = clamp(config.globalCooldownScalePercent + delta, 50, 200);
		else if (selected == Target.AUTO_MESSAGE) config.autoMessageScalePercent = clamp(config.autoMessageScalePercent + delta, 50, 200);
		else if (selected == Target.MANA) config.manaHudScalePercent = clamp(config.manaHudScalePercent + delta, 50, 200);
		else if (selected == Target.INVENTORY_VALUE) config.valuePanelScalePercent = clamp(config.valuePanelScalePercent + delta, 50, 200);
		else if (selected == Target.WAREHOUSE_VALUE) config.containerValueScalePercent = clamp(config.containerValueScalePercent + delta, 50, 200);
		config.save();
		scaleButton.setMessage(scaleText());
	}

	private Text scaleText() {
		int value = switch (selected) {
			case ARCANE -> ArcaneCooldownHud.config().scalePercent;
			case ARCANE_STATUS -> ArcaneCooldownHud.config().arcaneStatusScalePercent;
			case GLOBAL_COOLDOWN -> ArcaneCooldownHud.config().globalCooldownScalePercent;
			case MANA -> ArcaneCooldownHud.config().manaHudScalePercent;
			case AUTO_MESSAGE -> ArcaneCooldownHud.config().autoMessageScalePercent;
			case INVENTORY_VALUE -> ArcaneCooldownHud.config().valuePanelScalePercent;
			case WAREHOUSE_VALUE -> ArcaneCooldownHud.config().containerValueScalePercent;
		};
		return Text.literal(value + "%（点击+10）");
	}

	private void resetAll() {
		ArcaneHudConfig config = ArcaneCooldownHud.config();
		config.resetPosition(); config.resetArcaneStatusPosition(); config.resetGlobalCooldownPosition(); config.resetManaHudPosition(); config.resetAutoMessagePosition(); config.resetValuePanelPosition(); config.resetContainerValuePosition();
		config.scalePercent = 100; config.arcaneStatusScalePercent = 100;
		config.globalCooldownScalePercent = 100; config.manaHudScalePercent = 100; config.autoMessageScalePercent = 100; config.valuePanelScalePercent = 100; config.containerValueScalePercent = 100;
		config.save(); scaleButton.setMessage(scaleText());
	}

	private int selectedX() {
		return switch (selected) {
			case ARCANE -> ArcaneCooldownHud.configuredX(width);
			case ARCANE_STATUS -> ArcaneStatusHud.configuredX(width);
			case GLOBAL_COOLDOWN -> ArcaneStatusHud.configuredGlobalX(width);
			case MANA -> ManaHud.configuredX(width);
			case AUTO_MESSAGE -> AutoMessageModule.configuredX(width);
			case INVENTORY_VALUE -> ValuePanelRenderer.configuredPreviewX(width);
			case WAREHOUSE_VALUE -> ValuePanelRenderer.configuredContainerPreviewX(width);
		};
	}
	private int selectedY() {
		return switch (selected) {
			case ARCANE -> ArcaneCooldownHud.configuredY(height);
			case ARCANE_STATUS -> ArcaneStatusHud.configuredY(height);
			case GLOBAL_COOLDOWN -> ArcaneStatusHud.configuredGlobalY(height);
			case MANA -> ManaHud.configuredY(height);
			case AUTO_MESSAGE -> AutoMessageModule.configuredY(height);
			case INVENTORY_VALUE -> ValuePanelRenderer.configuredPreviewY(height);
			case WAREHOUSE_VALUE -> ValuePanelRenderer.configuredContainerPreviewY(height);
		};
	}

	private boolean inside(double mx, double my, int x, int y, int w, int h) {
		return mx >= x - 4 && mx <= x + w + 4 && my >= y - 4 && my <= y + h + 4;
	}

	private double[] snapValuePanel(double rawX, double rawY, int panelWidth, int panelHeight, boolean warehouse) {
		double x = clamp(rawX, 2, width - panelWidth - 2);
		double y = clamp(rawY, 54, height - panelHeight - 35);
		guideX = guideY = -1;
		int guiWidth = 176;
		int guiHeight = warehouse ? 222 : 166;
		int guiLeft = (width - guiWidth) / 2;
		int guiTop = warehouse ? Math.max(56, (height - guiHeight) / 2) : (height - guiHeight) / 2;
		int[] verticals = {width / 2, guiLeft, guiLeft + guiWidth};
		int[] horizontals = {height / 2, guiTop, guiTop + guiHeight};
		for (int line : verticals) {
			if (Math.abs((x + panelWidth / 2.0) - line) <= 5) { x = line - panelWidth / 2.0; guideX = line; break; }
			if (Math.abs(x - line) <= 5) { x = line; guideX = line; break; }
			if (Math.abs((x + panelWidth) - line) <= 5) { x = line - panelWidth; guideX = line; break; }
		}
		for (int line : horizontals) {
			if (Math.abs((y + panelHeight / 2.0) - line) <= 5) { y = line - panelHeight / 2.0; guideY = line; break; }
			if (Math.abs(y - line) <= 5) { y = line; guideY = line; break; }
			if (Math.abs((y + panelHeight) - line) <= 5) { y = line - panelHeight; guideY = line; break; }
		}
		return new double[]{clamp(x, 2, width - panelWidth - 2), clamp(y, 54, height - panelHeight - 35)};
	}

	private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
	private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
	@Override public void close() { ArcaneCooldownHud.config().save(); client.setScreen(parent); }
	@Override public boolean shouldPause() { return false; }
}

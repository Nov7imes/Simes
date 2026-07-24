package cn.simmc.simpricedisplay;

import cn.ni.automessage.AutoMessageClient;
import cn.ni.automessage.AutoMessageScreen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;

public final class ArcaneHudSettingsScreen extends Screen {
	private final Screen parent;
	private Page page = Page.FEATURES;
	private KeyBinding editingBinding;

	public ArcaneHudSettingsScreen(Screen parent) {
		super(Text.literal("Simes 设置"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		clearChildren();
		int center = width / 2;
		int top = 38;
		addDrawableChild(ButtonWidget.builder(tabText("功能开关", page == Page.FEATURES), button -> switchPage(Page.FEATURES))
				.dimensions(center - 204, top, 200, 20).build());
		addDrawableChild(ButtonWidget.builder(tabText("按键设置", page == Page.KEYS), button -> switchPage(Page.KEYS))
				.dimensions(center + 4, top, 200, 20).build());
		if (page == Page.FEATURES) initFeatures(center, top + 30);
		else initKeys(center, top + 30);
		int doneX = page == Page.FEATURES ? center + 4 : center - 100;
		int doneY = page == Page.FEATURES ? top + 174 : Math.min(height - 28, top + 174);
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), button -> close())
				.dimensions(doneX, doneY, 200, 20).build());
	}

	private void initFeatures(int center, int top) {
		int left = center - 204, right = center + 4;
		addToggle(left, top, "市场价格与详情", () -> SimesConfig.get().marketTooltipEnabled,
				() -> SimesConfig.get().toggle("market"));
		addToggle(right, top, "复制商店坐标", () -> SimesConfig.get().coordinateCopyEnabled,
				() -> SimesConfig.get().toggle("coordinates"));
		addToggle(left, top + 24, "背包/仓库价值", () -> SimesConfig.get().valuePanelEnabled,
				() -> SimesConfig.get().toggle("value"));
		addToggle(right, top + 24, "余额与今日收益", () -> SimesConfig.get().balanceTrackingEnabled,
				() -> SimesConfig.get().toggle("balance"));
		addToggle(left, top + 48, "箱子累计估值 /cal", () -> SimesConfig.get().containerCalculationEnabled,
				() -> SimesConfig.get().toggle("containers"));
		addToggle(right, top + 48, "奥术冷却监听", () -> ArcaneCooldownHud.config().arcaneEnabled, () -> {
			ArcaneCooldownHud.config().arcaneEnabled = !ArcaneCooldownHud.config().arcaneEnabled;
			ArcaneCooldownHud.config().save();
		});
		addToggle(left, top + 72, "吟唱与持续状态", () -> ArcaneCooldownHud.config().arcaneStatusEnabled, () -> {
			ArcaneCooldownHud.config().arcaneStatusEnabled = !ArcaneCooldownHud.config().arcaneStatusEnabled;
			ArcaneStatusHud.reset();
			ArcaneCooldownHud.config().save();
		});
		addDrawableChild(ButtonWidget.builder(modeText(), button -> {
			ArcaneHudConfig config = ArcaneCooldownHud.config();
			config.simesMode = !config.simesMode;
			ArcaneStatusHud.reset();
			config.save();
			button.setMessage(modeText());
		}).dimensions(right, top + 72, 200, 20).build());
		addToggle(left, top + 96, "自动消息运行", AutoMessageClient::isEnabled,
				() -> AutoMessageClient.setEnabled(!AutoMessageClient.isEnabled()));
		addToggle(left, top + 120, "法杖魔力 HUD", () -> ArcaneCooldownHud.config().manaHudEnabled, () -> {
			ArcaneCooldownHud.config().manaHudEnabled = !ArcaneCooldownHud.config().manaHudEnabled;
			ArcaneCooldownHud.config().save();
		});
		addDrawableChild(ButtonWidget.builder(Text.literal("编辑自动消息内容与间隔"), button ->
				client.setScreen(new AutoMessageScreen(this)))
				.dimensions(right, top + 96, 200, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("统一 HUD 布局与缩放"), button ->
				client.setScreen(new HudLayoutScreen(this)))
				.dimensions(left, top + 144, 200, 20).build());
		addToggle(right, top + 120, "发酵桶助手", () -> SimesConfig.get().fermentationAssistantEnabled,
				() -> SimesConfig.get().toggle("fermentation"));
		addToggle(right, top + 144, "蒸煮煎锅助手", () -> SimesConfig.get().cookwareAssistantEnabled,
				() -> SimesConfig.get().toggle("cookware"));
	}

	private void initKeys(int center, int top) {
		int left = center - 190;
		addKeyButton(left, top, "打开 Simes 设置", ArcaneCooldownHud.settingsKeyBinding());
		addKeyButton(left, top + 24, "打开/关闭市场详情", MarketDetailsController.keyBinding());
		addKeyButton(left, top + 48, "复制出售坐标", CoordinateCopyController.sellKeyBinding());
		addKeyButton(left, top + 72, "复制收购坐标", CoordinateCopyController.buyKeyBinding());
		addKeyButton(left, top + 96, "背包/仓库价值开关", ValuePanelController.keyBinding());
	}

	private void addKeyButton(int x, int y, String action, KeyBinding binding) {
		if (binding == null) return;
		addDrawableChild(ButtonWidget.builder(keyButtonText(action, binding), button -> {
			editingBinding = binding;
			refreshKeyButtons();
		}).dimensions(x, y, 380, 20).build());
	}

	private Text keyButtonText(String action, KeyBinding binding) {
		String key = binding.isUnbound() ? "§c未绑定" : "§e" + binding.getBoundKeyLocalizedText().getString();
		if (binding == editingBinding) key = "§f> 按下键盘键或鼠标键；Esc 清除 <";
		return Text.literal(action + "：" + key);
	}

	private void refreshKeyButtons() {
		if (page == Page.KEYS) init(client, width, height);
	}

	private void switchPage(Page target) {
		if (page == target) return;
		page = target;
		init(client, width, height);
	}

	private void addToggle(int x, int y, String name, State state, Runnable toggle) {
		addDrawableChild(ButtonWidget.builder(toggleText(name, state.get()), button -> {
			toggle.run();
			button.setMessage(toggleText(name, state.get()));
		}).dimensions(x, y, 200, 20).build());
	}

	private Text modeText() {
		return Text.literal("奥术显示：" + (ArcaneCooldownHud.config().simesMode ? "Simes HUD" : "原版 Action Bar"));
	}

	private static Text toggleText(String name, boolean enabled) {
		return Text.literal(name + "：" + (enabled ? "§a开启" : "§c关闭"));
	}

	private static Text tabText(String name, boolean selected) {
		return Text.literal((selected ? "§e▶ " : "§7") + name);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(textRenderer, Text.literal("Simes 设置"), width / 2, 14, 0xFFFFFFFF);
		context.drawCenteredTextWithShadow(textRenderer,
				Text.literal(page == Page.FEATURES ? "按模块开关功能，并进入对应的详细设置" : "点击一项后直接按新按键；支持键盘和鼠标键，Esc 清除绑定"),
				width / 2, 26, 0xFFAAAAAA);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (editingBinding == null) return super.keyPressed(keyCode, scanCode, modifiers);
		InputUtil.Key key = keyCode == 256 ? InputUtil.UNKNOWN_KEY : InputUtil.fromKeyCode(keyCode, scanCode);
		finishBinding(key);
		return true;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (editingBinding != null) {
			finishBinding(InputUtil.Type.MOUSE.createFromCode(button));
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	private void finishBinding(InputUtil.Key key) {
		editingBinding.setBoundKey(key);
		KeyBinding.updateKeysByCode();
		client.options.write();
		editingBinding = null;
		refreshKeyButtons();
	}

	@Override
	public void close() {
		SimesConfig.get().save();
		ArcaneCooldownHud.config().save();
		client.setScreen(parent);
	}

	@Override public boolean shouldPause() { return false; }
	@FunctionalInterface private interface State { boolean get(); }
	private enum Page { FEATURES, KEYS }
}

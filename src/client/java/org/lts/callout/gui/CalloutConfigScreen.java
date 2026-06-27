package org.lts.callout.gui;

import org.lts.callout.CalloutConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class CalloutConfigScreen extends Screen {
    private static final int FIELD_HEIGHT = 20;
    private static final int GAP = 6;
    private static final int TRIGGER_ROWS = 5;

    private final Screen parent;
    private final CalloutConfig config;
    private final List<TriggerRow> triggerRows = new ArrayList<>();

    private Button enabledButton;
    private Button caseButton;
    private Button ownButton;
    private EditBox nicknameWord;
    private EditBox nicknameSound;
    private EditBox nicknameVolume;
    private EditBox nicknamePitch;

    public CalloutConfigScreen(Screen parent) {
        super(Component.translatable("callout.screen.title"));
        this.parent = parent;
        this.config = CalloutConfig.currentCopy();
    }

    @Override
    protected void init() {
        int contentWidth = Math.min(620, this.width - 40);
        int left = (this.width - contentWidth) / 2;
        int y = 32;

        this.enabledButton = addRenderableWidget(toggleButton(left, y, 195, enabledLabel(), button -> {
            config.enabled = !config.enabled;
            button.setMessage(enabledLabel());
        }));
        this.caseButton = addRenderableWidget(toggleButton(left + 205, y, 205, caseLabel(), button -> {
            config.caseSensitive = !config.caseSensitive;
            button.setMessage(caseLabel());
        }));
        this.ownButton = addRenderableWidget(toggleButton(left + 420, y, 200, ownLabel(), button -> {
            config.pingOwnMessages = !config.pingOwnMessages;
            button.setMessage(ownLabel());
        }));

        y = 80;
        nicknameWord = addField(left, y, 130, config.nickname.word, "callout.hint.nickname_word");
        addRenderableWidget(regexButton(left + 136, y, 64, config.nickname));
        nicknameSound = addField(left + 206, y, 214, config.nickname.sound, "callout.hint.sound");
        nicknameVolume = addField(left + 430, y, 80, Float.toString(config.nickname.volume), "callout.hint.volume");
        nicknamePitch = addField(left + 520, y, 80, Float.toString(config.nickname.pitch), "callout.hint.pitch");

        triggerRows.clear();
        y = 138;
        for (int i = 0; i < TRIGGER_ROWS; i++) {
            CalloutConfig.Trigger trigger = i < config.triggers.size()
                    ? config.triggers.get(i)
                    : new CalloutConfig.Trigger("", "minecraft:block.note_block.pling", 1.0F, 1.0F);
            TriggerRow row = new TriggerRow(
                    trigger,
                    addField(left, y, 130, trigger.word, "callout.hint.word"),
                    addRenderableWidget(regexButton(left + 136, y, 64, trigger)),
                    addField(left + 206, y, 214, trigger.sound, "callout.hint.sound"),
                    addField(left + 430, y, 80, Float.toString(trigger.volume), "callout.hint.volume"),
                    addField(left + 520, y, 80, Float.toString(trigger.pitch), "callout.hint.pitch")
            );
            triggerRows.add(row);
            y += FIELD_HEIGHT + GAP;
        }

        int buttonY = this.height - 30;
        addRenderableWidget(Button.builder(Component.translatable("callout.button.save"), button -> saveAndClose())
                .bounds(this.width / 2 - 155, buttonY, 150, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("callout.button.cancel"), button -> this.minecraft.setScreen(parent))
                .bounds(this.width / 2 + 5, buttonY, 150, 20)
                .build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta) {
        graphics.fill(0, 0, this.width, this.height, 0xF010141C);
        graphics.centeredText(this.font, this.title, this.width / 2, 12, 0xFFFFFFFF);

        int contentWidth = Math.min(620, this.width - 40);
        int left = (this.width - contentWidth) / 2;

        graphics.text(this.font, Component.translatable("callout.section.main_trigger"), left, 56, 0xFF88C0D0);
        drawColumnHeaders(graphics, left, 68);

        graphics.text(this.font, Component.translatable("callout.section.additional_triggers"), left, 114, 0xFF88C0D0);
        drawColumnHeaders(graphics, left, 126);

        super.extractRenderState(graphics, mouseX, mouseY, tickDelta);
    }

    private void drawColumnHeaders(GuiGraphicsExtractor graphics, int left, int y) {
        graphics.text(this.font, Component.translatable("callout.field.word"), left, y, 0xFFD8DEE9);
        graphics.text(this.font, Component.translatable("callout.field.match_mode"), left + 136, y, 0xFFD8DEE9);
        graphics.text(this.font, Component.translatable("callout.field.sound"), left + 206, y, 0xFFD8DEE9);
        graphics.text(this.font, Component.translatable("callout.field.volume"), left + 430, y, 0xFFD8DEE9);
        graphics.text(this.font, Component.translatable("callout.field.pitch"), left + 520, y, 0xFFD8DEE9);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    private EditBox addField(int x, int y, int width, String value, String hintKey) {
        Component hint = Component.translatable(hintKey);
        EditBox field = new EditBox(this.font, x, y, width, FIELD_HEIGHT, hint);
        field.setValue(value == null ? "" : value);
        field.setMaxLength(256);
        field.setHint(hint);
        return addRenderableWidget(field);
    }

    private Button toggleButton(int x, int y, int width, Component label, Button.OnPress onPress) {
        return Button.builder(label, onPress).bounds(x, y, width, FIELD_HEIGHT).build();
    }

    private Button regexButton(int x, int y, int width, CalloutConfig.Trigger trigger) {
        return Button.builder(regexLabel(trigger), button -> {
                    trigger.regex = !trigger.regex;
                    button.setMessage(regexLabel(trigger));
                })
                .bounds(x, y, width, FIELD_HEIGHT)
                .build();
    }

    private Component enabledLabel() {
        return Component.translatable("callout.option.enabled", onOff(config.enabled));
    }

    private Component caseLabel() {
        return Component.translatable("callout.option.case_sensitive", onOff(config.caseSensitive));
    }

    private Component ownLabel() {
        return Component.translatable("callout.option.own_messages", onOff(config.pingOwnMessages));
    }

    private static Component onOff(boolean value) {
        return Component.translatable(value ? "callout.state.on" : "callout.state.off");
    }

    private void saveAndClose() {
        config.nickname.word = nicknameWord.getValue();
        config.nickname.sound = nicknameSound.getValue();
        config.nickname.volume = parseFloat(nicknameVolume.getValue(), 1.0F);
        config.nickname.pitch = parseFloat(nicknamePitch.getValue(), 1.0F);

        config.triggers.clear();
        for (TriggerRow row : triggerRows) {
            String word = row.word.getValue().trim();
            if (!word.isEmpty()) {
                config.triggers.add(new CalloutConfig.Trigger(
                        word,
                        row.sound.getValue(),
                        parseFloat(row.volume.getValue(), 1.0F),
                        parseFloat(row.pitch.getValue(), 1.0F),
                        row.trigger.regex
                ));
            }
        }

        CalloutConfig.save(config);
        this.minecraft.setScreen(parent);
    }

    private static float parseFloat(String value, float fallback) {
        try {
            return Float.parseFloat(value.trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static Component regexLabel(CalloutConfig.Trigger trigger) {
        return Component.translatable(trigger.regex ? "callout.match.regex" : "callout.match.text");
    }

    private record TriggerRow(CalloutConfig.Trigger trigger, EditBox word, Button regex, EditBox sound, EditBox volume, EditBox pitch) {
    }
}

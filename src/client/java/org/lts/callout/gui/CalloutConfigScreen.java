package org.lts.callout.gui;

import org.lts.callout.CalloutConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class CalloutConfigScreen extends Screen {
    private static final int FIELD_HEIGHT = 20;
    private static final int GAP = 6;
    private static final int TRIGGERS_PER_PAGE = 6;

    private final Screen parent;
    private final CalloutConfig config;
    private final List<TriggerRow> triggerRows = new ArrayList<>();
    private int triggerPage;
    private String validationError = "";

    private Button enabledButton;
    private Button caseButton;
    private Button ownButton;
    private Button persistButton;
    private Button clearScopeButton;
    private EditBox nicknameWord;
    private EditBox nicknameSound;
    private EditBox nicknameVolume;
    private EditBox nicknamePitch;
    private EditBox maxPings;
    private EditBox contextBefore;
    private EditBox contextAfter;

    public CalloutConfigScreen(Screen parent) {
        super(Component.translatable("callout.screen.title"));
        this.parent = parent;
        this.config = CalloutConfig.currentCopy();
    }

    private CalloutConfigScreen(Screen parent, CalloutConfig config, int triggerPage) {
        super(Component.translatable("callout.screen.title"));
        this.parent = parent;
        this.config = config;
        this.triggerPage = Math.max(0, triggerPage);
    }

    @Override
    protected void init() {
        int contentWidth = Math.min(700, this.width - 40);
        int left = (this.width - contentWidth) / 2;
        int y = 32;

        this.enabledButton = addRenderableWidget(toggleButton(left, y, 220, enabledLabel(), button -> {
            config.enabled = !config.enabled;
            button.setMessage(enabledLabel());
        }));
        this.caseButton = addRenderableWidget(toggleButton(left + 230, y, 220, caseLabel(), button -> {
            config.caseSensitive = !config.caseSensitive;
            button.setMessage(caseLabel());
            validateAllRegex();
        }));
        this.ownButton = addRenderableWidget(toggleButton(left + 460, y, 240, ownLabel(), button -> {
            config.pingOwnMessages = !config.pingOwnMessages;
            button.setMessage(ownLabel());
        }));

        y = 62;
        maxPings = addField(left, y, 90, Integer.toString(config.maxPingHistory), "callout.hint.max_pings");
        contextBefore = addField(left + 100, y, 80, Integer.toString(config.contextBefore), "callout.hint.context_before");
        contextAfter = addField(left + 190, y, 80, Integer.toString(config.contextAfter), "callout.hint.context_after");
        persistButton = addRenderableWidget(toggleButton(left + 282, y, 190, persistLabel(), button -> {
            config.persistHistory = !config.persistHistory;
            button.setMessage(persistLabel());
        }));
        clearScopeButton = addRenderableWidget(toggleButton(left + 482, y, 218, clearScopeLabel(), button -> {
            config.clearHistoryOnScopeChange = !config.clearHistoryOnScopeChange;
            button.setMessage(clearScopeLabel());
        }));

        y = 118;
        nicknameWord = addField(left, y, 130, config.nickname.word, "callout.hint.nickname_word");
        addRenderableWidget(regexButton(left + 136, y, 64, config.nickname));
        nicknameSound = addField(left + 206, y, 214, config.nickname.sound, "callout.hint.sound");
        nicknameVolume = addField(left + 430, y, 80, Float.toString(config.nickname.volume), "callout.hint.volume");
        nicknamePitch = addField(left + 520, y, 80, Float.toString(config.nickname.pitch), "callout.hint.pitch");
        nicknameWord.setResponder(text -> validateAllRegex());

        triggerRows.clear();
        y = 176;
        int start = triggerPage * TRIGGERS_PER_PAGE;
        for (int i = 0; i < TRIGGERS_PER_PAGE; i++) {
            int triggerIndex = start + i;
            if (triggerIndex >= config.triggers.size()) {
                break;
            }
            CalloutConfig.Trigger trigger = config.triggers.get(triggerIndex);
            TriggerRow row = new TriggerRow(
                    triggerIndex,
                    trigger,
                    addField(left, y, 130, trigger.word, "callout.hint.word"),
                    addRenderableWidget(regexButton(left + 136, y, 64, trigger)),
                    addField(left + 206, y, 214, trigger.sound, "callout.hint.sound"),
                    addField(left + 430, y, 80, Float.toString(trigger.volume), "callout.hint.volume"),
                    addField(left + 520, y, 80, Float.toString(trigger.pitch), "callout.hint.pitch"),
                    addRenderableWidget(Button.builder(Component.translatable("callout.button.remove"), button -> {
                        collectCurrentValues();
                        if (triggerIndex >= 0 && triggerIndex < config.triggers.size()) {
                            config.triggers.remove(triggerIndex);
                        }
                        triggerPage = Math.min(triggerPage, maxTriggerPage());
                        reopen();
                    }).bounds(left + 610, y, 90, FIELD_HEIGHT).build())
            );
            row.word.setResponder(text -> validateAllRegex());
            triggerRows.add(row);
            y += FIELD_HEIGHT + GAP;
        }

        int listY = 148;
        addRenderableWidget(Button.builder(Component.translatable("callout.button.add_trigger"), button -> {
            collectCurrentValues();
            config.triggers.add(new CalloutConfig.Trigger("", "minecraft:block.note_block.pling", 1.0F, 1.0F));
            triggerPage = maxTriggerPage();
            reopen();
        }).bounds(left + 342, listY, 120, FIELD_HEIGHT).build());
        addRenderableWidget(Button.builder(Component.literal("<"), button -> {
            collectCurrentValues();
            triggerPage = Math.max(0, triggerPage - 1);
            reopen();
        }).bounds(left + 472, listY, 36, FIELD_HEIGHT).build()).active = triggerPage > 0;
        addRenderableWidget(Button.builder(Component.literal(">"), button -> {
            collectCurrentValues();
            triggerPage = Math.min(maxTriggerPage(), triggerPage + 1);
            reopen();
        }).bounds(left + 514, listY, 36, FIELD_HEIGHT).build()).active = triggerPage < maxTriggerPage();

        int buttonY = this.height - 30;
        addRenderableWidget(Button.builder(Component.translatable("callout.button.save"), button -> saveAndClose())
                .bounds(this.width / 2 - 155, buttonY, 150, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("callout.button.cancel"), button -> this.minecraft.setScreen(parent))
                .bounds(this.width / 2 + 5, buttonY, 150, 20)
                .build());
        validateAllRegex();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta) {
        graphics.fill(0, 0, this.width, this.height, 0xF010141C);
        graphics.centeredText(this.font, this.title, this.width / 2, 12, 0xFFFFFFFF);

        int contentWidth = Math.min(700, this.width - 40);
        int left = (this.width - contentWidth) / 2;

        graphics.text(this.font, Component.translatable("callout.section.history"), left, 50, 0xFF88C0D0);
        graphics.text(this.font, Component.translatable("callout.section.main_trigger"), left, 94, 0xFF88C0D0);
        drawColumnHeaders(graphics, left, 106);

        graphics.text(this.font, Component.translatable("callout.section.additional_triggers"), left, 152, 0xFF88C0D0);
        graphics.text(this.font, Component.translatable("callout.triggers.page", triggerPage + 1, maxTriggerPage() + 1), left + 556, 154, 0xFFD8DEE9);
        drawColumnHeaders(graphics, left, 164);
        if (!validationError.isBlank()) {
            graphics.centeredText(this.font, Component.literal(validationError), this.width / 2, this.height - 45, 0xFFFF5555);
        }

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
                    validateAllRegex();
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

    private Component persistLabel() {
        return Component.translatable("callout.option.persist_history", onOff(config.persistHistory));
    }

    private Component clearScopeLabel() {
        return Component.translatable("callout.option.clear_on_scope_change", onOff(config.clearHistoryOnScopeChange));
    }

    private static Component onOff(boolean value) {
        return Component.translatable(value ? "callout.state.on" : "callout.state.off");
    }

    private void saveAndClose() {
        collectCurrentValues();
        if (!validateAllRegex()) {
            return;
        }
        config.triggers.removeIf(trigger -> trigger.word == null || trigger.word.isBlank());

        CalloutConfig.save(config);
        this.minecraft.setScreen(parent);
    }

    private void collectCurrentValues() {
        config.nickname.word = nicknameWord.getValue();
        config.nickname.sound = nicknameSound.getValue();
        config.nickname.volume = parseFloat(nicknameVolume.getValue(), 1.0F);
        config.nickname.pitch = parseFloat(nicknamePitch.getValue(), 1.0F);
        config.maxPingHistory = parseInt(maxPings.getValue(), config.maxPingHistory);
        config.contextBefore = parseInt(contextBefore.getValue(), config.contextBefore);
        config.contextAfter = parseInt(contextAfter.getValue(), config.contextAfter);

        for (TriggerRow row : triggerRows) {
            if (row.index >= 0 && row.index < config.triggers.size()) {
                CalloutConfig.Trigger trigger = config.triggers.get(row.index);
                trigger.word = row.word.getValue().trim();
                trigger.sound = row.sound.getValue();
                trigger.volume = parseFloat(row.volume.getValue(), 1.0F);
                trigger.pitch = parseFloat(row.pitch.getValue(), 1.0F);
                trigger.regex = row.trigger.regex;
            }
        }
    }

    private void reopen() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(new CalloutConfigScreen(parent, config, triggerPage));
        }
    }

    private int maxTriggerPage() {
        return Math.max(0, (Math.max(1, config.triggers.size()) - 1) / TRIGGERS_PER_PAGE);
    }

    private boolean validateAllRegex() {
        validationError = "";
        if (nicknameWord != null) {
            config.nickname.word = nicknameWord.getValue();
        }
        for (TriggerRow row : triggerRows) {
            row.trigger.word = row.word.getValue().trim();
        }
        boolean valid = validateRegex(config.nickname, nicknameWord);
        for (TriggerRow row : triggerRows) {
            valid &= validateRegex(row.trigger, row.word);
        }
        if (valid) {
            for (CalloutConfig.Trigger trigger : config.triggers) {
                if (trigger.regex && trigger.word != null && !trigger.word.isBlank() && !isValidRegex(trigger.word)) {
                    validationError = Component.translatable("callout.error.invalid_regex", trigger.word).getString();
                    valid = false;
                    break;
                }
            }
        }
        return valid;
    }

    private boolean validateRegex(CalloutConfig.Trigger trigger, EditBox field) {
        if (trigger == null || field == null || !trigger.regex || field.getValue().isBlank()) {
            if (field != null) {
                field.setTextColor(0xFFE0E0E0);
            }
            return true;
        }
        if (isValidRegex(field.getValue())) {
            field.setTextColor(0xFFE0E0E0);
            return true;
        }
        field.setTextColor(0xFFFF5555);
        validationError = Component.translatable("callout.error.invalid_regex", field.getValue()).getString();
        return false;
    }

    private boolean isValidRegex(String value) {
        int flags = config.caseSensitive ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        try {
            Pattern.compile(value, flags);
            return true;
        } catch (PatternSyntaxException exception) {
            return false;
        }
    }

    private static float parseFloat(String value, float fallback) {
        try {
            return Float.parseFloat(value.trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static Component regexLabel(CalloutConfig.Trigger trigger) {
        return Component.translatable(trigger.regex ? "callout.match.regex" : "callout.match.text");
    }

    private record TriggerRow(int index, CalloutConfig.Trigger trigger, EditBox word, Button regex, EditBox sound, EditBox volume, EditBox pitch, Button remove) {
    }
}

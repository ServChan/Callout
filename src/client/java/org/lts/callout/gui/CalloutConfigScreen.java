package org.lts.callout.gui;

import org.lts.callout.CalloutConfig;
import org.lts.callout.MinecraftScreenAccess;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class CalloutConfigScreen extends Screen {
    private static final int BG_CONTAINER = 0xF00D141F;
    private static final int BG_HEADER = 0xFF182638;
    private static final int BORDER_PRIMARY = 0xFF2E435E;
    private static final int STATUS_ON = 0xFF55FF55;
    private static final int STATUS_OFF = 0xFFFF5555;
    private static final int ACCENT_CYAN = 0xFF00E5FF;

    private static final int FIELD_HEIGHT = 20;
    private static final int GAP = 6;
    private static final int TRIGGERS_PER_PAGE = 6;

    private static final int DESIGN_WIDTH = 700;

    private final Screen parent;
    private final CalloutConfig config;
    private final List<TriggerRow> triggerRows = new ArrayList<>();
    private int triggerPage;
    private String validationError = "";

    private double layoutScale = 1.0;
    private int originX;

    private Button enabledButton;
    private Button caseButton;
    private Button ownButton;
    private Button persistButton;
    private Button clearScopeButton;
    private Button saveButton;
    private Button cancelButton;
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
        int contentWidth = Math.min(DESIGN_WIDTH, this.width - 20);
        this.layoutScale = Math.min(1.0, contentWidth / (double) DESIGN_WIDTH);
        this.originX = (this.width - contentWidth) / 2;
        int y = 34;

        this.enabledButton = addRenderableWidget(toggleButton(gx(0), y, gw(220), enabledLabel(), button -> {
            config.enabled = !config.enabled;
            button.setMessage(enabledLabel());
        }));
        this.caseButton = addRenderableWidget(toggleButton(gx(230), y, gw(220), caseLabel(), button -> {
            config.caseSensitive = !config.caseSensitive;
            button.setMessage(caseLabel());
            validateAllInputs();
        }));
        this.ownButton = addRenderableWidget(toggleButton(gx(460), y, gw(240), ownLabel(), button -> {
            config.pingOwnMessages = !config.pingOwnMessages;
            button.setMessage(ownLabel());
        }));

        y = 64;
        maxPings = addField(gx(0), y, gw(90), Integer.toString(config.maxPingHistory), "callout.hint.max_pings");
        contextBefore = addField(gx(100), y, gw(80), Integer.toString(config.contextBefore), "callout.hint.context_before");
        contextAfter = addField(gx(190), y, gw(80), Integer.toString(config.contextAfter), "callout.hint.context_after");
        maxPings.setResponder(text -> validateAllInputs());
        contextBefore.setResponder(text -> validateAllInputs());
        contextAfter.setResponder(text -> validateAllInputs());
        persistButton = addRenderableWidget(toggleButton(gx(282), y, gw(190), persistLabel(), button -> {
            config.persistHistory = !config.persistHistory;
            button.setMessage(persistLabel());
        }));
        clearScopeButton = addRenderableWidget(toggleButton(gx(482), y, gw(218), clearScopeLabel(), button -> {
            config.clearHistoryOnScopeChange = !config.clearHistoryOnScopeChange;
            button.setMessage(clearScopeLabel());
        }));

        y = 118;
        nicknameWord = addField(gx(0), y, gw(130), config.nickname.word, "callout.hint.nickname_word");
        addRenderableWidget(regexButton(gx(136), y, gw(64), config.nickname));
        nicknameSound = addField(gx(206), y, gw(214), config.nickname.sound, "callout.hint.sound");
        nicknameVolume = addField(gx(430), y, gw(80), Float.toString(config.nickname.volume), "callout.hint.volume");
        nicknamePitch = addField(gx(520), y, gw(80), Float.toString(config.nickname.pitch), "callout.hint.pitch");
        nicknameWord.setResponder(text -> validateAllInputs());
        nicknameVolume.setResponder(text -> validateAllInputs());
        nicknamePitch.setResponder(text -> validateAllInputs());

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
                    addField(gx(0), y, gw(130), trigger.word, "callout.hint.word"),
                    addRenderableWidget(regexButton(gx(136), y, gw(64), trigger)),
                    addField(gx(206), y, gw(214), trigger.sound, "callout.hint.sound"),
                    addField(gx(430), y, gw(80), Float.toString(trigger.volume), "callout.hint.volume"),
                    addField(gx(520), y, gw(80), Float.toString(trigger.pitch), "callout.hint.pitch"),
                    addRenderableWidget(Button.builder(Component.translatable("callout.button.remove"), button -> {
                        collectCurrentValues();
                        if (triggerIndex >= 0 && triggerIndex < config.triggers.size()) {
                            config.triggers.remove(triggerIndex);
                        }
                        triggerPage = Math.min(triggerPage, maxTriggerPage());
                        reopen();
                    }).bounds(gx(610), y, gw(90), FIELD_HEIGHT).build())
            );
            row.word.setResponder(text -> validateAllInputs());
            row.volume.setResponder(text -> validateAllInputs());
            row.pitch.setResponder(text -> validateAllInputs());
            triggerRows.add(row);
            y += FIELD_HEIGHT + GAP;
        }

        int listY = 148;
        addRenderableWidget(Button.builder(Component.translatable("callout.button.add_trigger"), button -> {
            if (!validateAllInputs()) return;
            collectCurrentValues();
            config.triggers.add(new CalloutConfig.Trigger("", "minecraft:block.note_block.pling", 1.0F, 1.0F));
            triggerPage = maxTriggerPage();
            reopen();
        }).bounds(gx(342), listY, gw(120), FIELD_HEIGHT).build());
        addRenderableWidget(Button.builder(Component.literal("<"), button -> {
            if (!validateAllInputs()) return;
            collectCurrentValues();
            triggerPage = Math.max(0, triggerPage - 1);
            reopen();
        }).bounds(gx(472), listY, gw(36), FIELD_HEIGHT).build()).active = triggerPage > 0;
        addRenderableWidget(Button.builder(Component.literal(">"), button -> {
            if (!validateAllInputs()) return;
            collectCurrentValues();
            triggerPage = Math.min(maxTriggerPage(), triggerPage + 1);
            reopen();
        }).bounds(gx(514), listY, gw(36), FIELD_HEIGHT).build()).active = triggerPage < maxTriggerPage();

        int buttonY = this.height - 30;
        saveButton = addRenderableWidget(Button.builder(Component.translatable("callout.button.save"), button -> saveAndClose())
                .bounds(this.width / 2 - 155, buttonY, 150, 20)
                .build());
        cancelButton = addRenderableWidget(Button.builder(Component.translatable("callout.button.cancel"), button -> closeScreen())
                .bounds(this.width / 2 + 5, buttonY, 150, 20)
                .build());
        validateAllInputs();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta) {
        graphics.fill(0, 0, this.width, this.height, BG_CONTAINER);

        graphics.fill(0, 0, this.width, 28, BG_HEADER);
        graphics.fill(0, 27, this.width, 28, BORDER_PRIMARY);
        int titleX = (this.width - this.font.width(this.title)) / 2;
        graphics.text(this.font, this.title, titleX, 9, 0xFFFFFFFF, true);

        int left = gx(0);

        graphics.text(this.font, Component.translatable("callout.section.history"), left, 52, ACCENT_CYAN);
        graphics.text(this.font, Component.translatable("callout.section.main_trigger"), left, 96, ACCENT_CYAN);
        drawColumnHeaders(graphics, 108);

        graphics.text(this.font, Component.translatable("callout.section.additional_triggers"), left, 152, ACCENT_CYAN);
        graphics.text(this.font, Component.translatable("callout.triggers.page", triggerPage + 1, maxTriggerPage() + 1), gx(556), 154, 0xFFD8DEE9);
        drawColumnHeaders(graphics, 164);
        if (!validationError.isBlank()) {
            graphics.centeredText(this.font, Component.literal(validationError), this.width / 2, this.height - 45, 0xFFFF5555);
        }

        super.extractRenderState(graphics, mouseX, mouseY, tickDelta);
    }

    private void drawColumnHeaders(GuiGraphicsExtractor graphics, int y) {
        graphics.text(this.font, Component.translatable("callout.field.word"), gx(0), y, 0xFFD8DEE9);
        graphics.text(this.font, Component.translatable("callout.field.match_mode"), gx(136), y, 0xFFD8DEE9);
        graphics.text(this.font, Component.translatable("callout.field.sound"), gx(206), y, 0xFFD8DEE9);
        graphics.text(this.font, Component.translatable("callout.field.volume"), gx(430), y, 0xFFD8DEE9);
        graphics.text(this.font, Component.translatable("callout.field.pitch"), gx(520), y, 0xFFD8DEE9);
    }

    private int gx(int offset) {
        return originX + (int) Math.round(offset * layoutScale);
    }

    private int gw(int width) {
        return Math.max(14, (int) Math.round(width * layoutScale));
    }

    @Override
    public void onClose() {
        closeScreen();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (saveButton != null && saveButton.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (cancelButton != null && cancelButton.mouseClicked(event, doubleClick)) {
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_ESCAPE) {
            closeScreen();
            return true;
        }
        return super.keyPressed(event);
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
                    validateAllInputs();
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
        return Component.translatable(value ? "callout.state.on" : "callout.state.off")
                .withStyle(style -> style.withColor(value ? STATUS_ON : STATUS_OFF));
    }

    private void saveAndClose() {
        if (!validateAllInputs()) {
            return;
        }
        collectCurrentValues();
        config.triggers.removeIf(trigger -> trigger.word == null || trigger.word.isBlank());

        if (CalloutConfig.save(config)) {
            closeScreen();
        } else {
            validationError = Component.translatable("callout.error.save_failed").getString();
        }
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
            this.minecraft.setScreenAndShow(new CalloutConfigScreen(parent, config, triggerPage));
        }
    }

    private void closeScreen() {
        if (this.minecraft != null) {
            MinecraftScreenAccess.closeScreen(this.minecraft);
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

    private boolean validateAllInputs() {
        boolean valid = validateAllRegex();
        valid &= validateIntField(maxPings, 1, 1000, "callout.error.max_pings");
        valid &= validateIntField(contextBefore, 0, 20, "callout.error.context");
        valid &= validateIntField(contextAfter, 0, 20, "callout.error.context");
        valid &= validateFloatField(nicknameVolume, 0.0F, 4.0F, "callout.error.volume");
        valid &= validateFloatField(nicknamePitch, 0.5F, 2.0F, "callout.error.pitch");
        for (TriggerRow row : triggerRows) {
            valid &= validateFloatField(row.volume, 0.0F, 4.0F, "callout.error.volume");
            valid &= validateFloatField(row.pitch, 0.5F, 2.0F, "callout.error.pitch");
        }
        if (saveButton != null) {
            saveButton.active = valid;
        }
        return valid;
    }

    private boolean validateIntField(EditBox field, int min, int max, String errorKey) {
        if (field == null) return true;
        try {
            int value = Integer.parseInt(field.getValue().trim());
            if (value >= min && value <= max) {
                field.setTextColor(0xFFE0E0E0);
                return true;
            }
        } catch (NumberFormatException ignored) {
        }
        field.setTextColor(0xFFFF5555);
        if (validationError.isBlank()) {
            validationError = Component.translatable(errorKey).getString();
        }
        return false;
    }

    private boolean validateFloatField(EditBox field, float min, float max, String errorKey) {
        if (field == null) return true;
        try {
            float value = Float.parseFloat(field.getValue().trim());
            if (Float.isFinite(value) && value >= min && value <= max) {
                field.setTextColor(0xFFE0E0E0);
                return true;
            }
        } catch (NumberFormatException ignored) {
        }
        field.setTextColor(0xFFFF5555);
        if (validationError.isBlank()) {
            validationError = Component.translatable(errorKey).getString();
        }
        return false;
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

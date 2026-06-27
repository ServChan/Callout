package org.lts.callout.gui;

import org.lts.callout.CalloutHistory;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class CalloutHistoryScreen extends Screen {
    private static final int CARD_HEIGHT = 165;
    private static final int CARD_GAP = 10;

    private final Screen parent;
    private int page;
    private Button previousButton;
    private Button nextButton;

    public CalloutHistoryScreen(Screen parent) {
        super(Component.translatable("callout.history.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int y = this.height - 30;
        previousButton = addRenderableWidget(Button.builder(Component.translatable("callout.history.previous"), button -> {
            page = Math.max(0, page - 1);
            updateButtons();
        }).bounds(this.width / 2 - 205, y, 96, 20).build());
        nextButton = addRenderableWidget(Button.builder(Component.translatable("callout.history.next"), button -> {
            page = Math.min(maxPage(), page + 1);
            updateButtons();
        }).bounds(this.width / 2 - 103, y, 96, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("callout.history.clear"), button -> {
            CalloutHistory.clear();
            page = 0;
            updateButtons();
        }).bounds(this.width / 2 + 7, y, 96, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("callout.button.close"), button -> this.minecraft.setScreen(parent))
                .bounds(this.width / 2 + 109, y, 96, 20).build());
        updateButtons();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta) {
        graphics.fill(0, 0, this.width, this.height, 0xF00B0E14);
        graphics.fill(0, 0, this.width, 36, 0xFF141A24);
        graphics.centeredText(this.font, this.title, this.width / 2, 12, 0xFFFFFFFF);

        List<CalloutHistory.PingEntry> entries = CalloutHistory.entries();
        int total = entries.size();
        int left = 16;
        int contentWidth = Math.max(200, this.width - 32);
        int top = 46;

        Component countComponent = Component.translatable("callout.history.count", total, Math.min(page + 1, maxPage() + 1), maxPage() + 1);
        int countWidth = this.font.width(countComponent) + 12;
        graphics.fill(left, 32, left + countWidth, 44, 0xFF1D2430);
        graphics.text(this.font, countComponent, left + 6, 34, 0xFF8BE9FD);

        if (entries.isEmpty()) {
            graphics.fill(left, top, left + contentWidth, Math.min(this.height - 44, top + 96), 0xFF151C26);
            graphics.outline(left, top, contentWidth, 96, 0xFF2C394A);
            graphics.centeredText(this.font, Component.translatable("callout.history.empty"), this.width / 2, top + 42, 0xFFD8DEE9);
            super.extractRenderState(graphics, mouseX, mouseY, tickDelta);
            return;
        }

        int pageSize = pageSize();
        int start = page * pageSize;
        int end = Math.min(entries.size(), start + pageSize);
        int y = top;
        for (int i = start; i < end; i++) {
            drawEntry(graphics, entries.get(i), left, y, contentWidth, i + 1);
            y += CARD_HEIGHT + CARD_GAP;
        }

        super.extractRenderState(graphics, mouseX, mouseY, tickDelta);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY > 0) {
            if (page > 0) {
                page--;
                updateButtons();
                return true;
            }
        } else if (scrollY < 0) {
            if (page < maxPage()) {
                page++;
                updateButtons();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void drawEntry(GuiGraphicsExtractor graphics, CalloutHistory.PingEntry entry, int left, int top, int width, int index) {
        graphics.fill(left, top, left + width, top + CARD_HEIGHT, 0xFF151C26);
        graphics.outline(left, top, width, CARD_HEIGHT, 0xFF2C394A);
        graphics.fill(left, top, left + 4, top + CARD_HEIGHT, 0xFF4FA3FF);

        int curX = left + 12;
        int headerY = top + 8;

        String idxStr = "#" + index;
        graphics.text(this.font, idxStr, curX, headerY, 0xFF88C0D0);
        curX += this.font.width(idxStr) + 8;

        graphics.text(this.font, "·", curX, headerY, 0xFF6272A4);
        curX += this.font.width("·") + 8;

        graphics.text(this.font, entry.time, curX, headerY, 0xFFF8F8F2);
        curX += this.font.width(entry.time) + 8;

        graphics.text(this.font, "·", curX, headerY, 0xFF6272A4);
        curX += this.font.width("·") + 8;

        String senderStr = entry.sender == null ? "" : entry.sender;
        graphics.text(this.font, senderStr, curX, headerY, 0xFF50FA7B);
        curX += this.font.width(senderStr) + 10;

        String modeText = entry.regex ? Component.translatable("callout.match.regex").getString() : Component.translatable("callout.match.text").getString();
        int modeW = this.font.width(modeText) + 8;
        int modeBg = entry.regex ? 0x40BD93F9 : 0x408BE9FD;
        int modeFg = entry.regex ? 0xFFBD93F9 : 0xFF8BE9FD;
        graphics.fill(curX, headerY - 2, curX + modeW, headerY + 10, modeBg);
        graphics.text(this.font, modeText, curX + 4, headerY, modeFg);
        curX += modeW + 8;

        String triggerText = entry.trigger == null || entry.trigger.isBlank() ? Component.translatable("callout.history.nickname_trigger").getString() : entry.trigger;
        int trigW = this.font.width(triggerText) + 8;
        graphics.fill(curX, headerY - 2, curX + trigW, headerY + 10, 0x304FA3FF);
        graphics.text(this.font, triggerText, curX + 4, headerY, 0xFF4FA3FF);

        graphics.fill(left + 8, top + 22, left + width - 8, top + 23, 0xFF232C3A);

        int lineY = top + 26;
        for (DisplayLine line : displayLines(entry)) {
            if (line.ping) {
                graphics.fill(left + 5, lineY - 1, left + width - 5, lineY + 11, 0x40FFD166);
                graphics.fill(left + 4, lineY - 1, left + 7, lineY + 11, 0xFFFFD166);
            }
            int color = line.ping ? 0xFFFFD166 : 0xFF98A6B5;
            String prefix = line.ping ? "> " : "  ";
            Component text = Component.literal(prefix + "[" + line.line.time() + "] " + line.line.message());
            graphics.text(this.font, trim(text.getString(), width - 26), left + 12, lineY, color);
            lineY += 12;
        }
    }

    private List<DisplayLine> displayLines(CalloutHistory.PingEntry entry) {
        List<DisplayLine> lines = new ArrayList<>();
        for (CalloutHistory.ChatLine line : entry.before) {
            lines.add(new DisplayLine(line, false));
        }
        lines.add(new DisplayLine(entry.pingLine, true));
        for (CalloutHistory.ChatLine line : CalloutHistory.afterLines(entry)) {
            lines.add(new DisplayLine(line, false));
        }
        return lines;
    }

    private String trim(String value, int pixelWidth) {
        return this.font.width(value) <= pixelWidth ? value : this.font.plainSubstrByWidth(value, pixelWidth - this.font.width("...")) + "...";
    }

    private int pageSize() {
        return Math.max(1, (this.height - 92) / (CARD_HEIGHT + CARD_GAP));
    }

    private int maxPage() {
        int size = CalloutHistory.entries().size();
        return Math.max(0, (size - 1) / pageSize());
    }

    private void updateButtons() {
        int maxPage = maxPage();
        page = Math.min(page, maxPage);
        if (previousButton != null) {
            previousButton.active = page > 0;
        }
        if (nextButton != null) {
            nextButton.active = page < maxPage;
        }
    }

    private record DisplayLine(CalloutHistory.ChatLine line, boolean ping) {
    }
}


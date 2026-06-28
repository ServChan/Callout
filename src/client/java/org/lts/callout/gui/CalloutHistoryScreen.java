package org.lts.callout.gui;

import org.lts.callout.CalloutHistory;
import org.lts.callout.CalloutConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class CalloutHistoryScreen extends Screen {
    private static final int CARD_HEIGHT = 165;
    private static final int CARD_GAP = 10;

    private final Screen parent;
    private int page;
    private Button previousButton;
    private Button nextButton;
    private EditBox searchBox;
    private Button sortButton;
    private Button scopeButton;
    private boolean newestFirst = true;
    private int scopeIndex;

    private String toastText;
    private int toastX;
    private int toastY;
    private long toastExpiration;

    public CalloutHistoryScreen(Screen parent) {
        super(Component.translatable("callout.history.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int topBarY = 8;
        int searchX = Math.max(this.width / 2 + 50, this.width - 210);
        int sortX = Math.max(this.width / 2 + 165, this.width - 94);

        searchBox = addRenderableWidget(new EditBox(this.font, searchX, topBarY, 110, 20, Component.translatable("callout.history.search_hint")));
        searchBox.setHint(Component.translatable("callout.history.search_hint"));
        searchBox.setResponder(text -> {
            page = 0;
            updateButtons();
        });

        sortButton = addRenderableWidget(Button.builder(sortLabel(), button -> {
            newestFirst = !newestFirst;
            button.setMessage(sortLabel());
            page = 0;
            updateButtons();
        }).bounds(sortX, topBarY, 88, 20).build());

        scopeButton = addRenderableWidget(Button.builder(scopeLabel(), button -> {
            int count = availableScopes().size() + 1;
            scopeIndex = (scopeIndex + 1) % Math.max(1, count);
            button.setMessage(scopeLabel());
            page = 0;
            updateButtons();
        }).bounds(Math.max(16, searchX - 142), topBarY, 136, 20).build());

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

    private Component sortLabel() {
        return Component.translatable(newestFirst ? "callout.history.sort.newest" : "callout.history.sort.oldest");
    }

    private Component scopeLabel() {
        String scope = selectedScope();
        return Component.translatable("callout.history.scope_filter", scope == null ? Component.translatable("callout.history.scope_all").getString() : scope);
    }

    private List<CalloutHistory.PingEntry> filteredEntries() {
        List<CalloutHistory.PingEntry> all = CalloutHistory.entries();
        String query = searchBox != null ? searchBox.getValue().trim().toLowerCase(Locale.ROOT) : "";
        String selectedScope = selectedScope();
        List<CalloutHistory.PingEntry> result = new ArrayList<>();
        for (CalloutHistory.PingEntry entry : all) {
            if (selectedScope != null && !scopeOf(entry).equals(selectedScope)) {
                continue;
            }
            if (query.isEmpty() || matchesQuery(entry, query)) {
                result.add(entry);
            }
        }
        if (!newestFirst) {
            List<CalloutHistory.PingEntry> reversed = new ArrayList<>(result);
            Collections.reverse(reversed);
            return reversed;
        }
        return result;
    }

    private boolean matchesQuery(CalloutHistory.PingEntry entry, String query) {
        if (scopeOf(entry).toLowerCase(Locale.ROOT).contains(query)) return true;
        if (entry.sender != null && entry.sender.toLowerCase(Locale.ROOT).contains(query)) return true;
        if (entry.trigger != null && entry.trigger.toLowerCase(Locale.ROOT).contains(query)) return true;
        if (entry.pingLine != null && safeText(entry.pingLine.message()).toLowerCase(Locale.ROOT).contains(query)) return true;
        for (CalloutHistory.ChatLine line : safeLines(entry.before)) {
            if (safeText(line.message()).toLowerCase(Locale.ROOT).contains(query)) return true;
        }
        for (CalloutHistory.ChatLine line : CalloutHistory.afterLines(entry)) {
            if (safeText(line.message()).toLowerCase(Locale.ROOT).contains(query)) return true;
        }
        return false;
    }

    private List<String> availableScopes() {
        List<String> scopes = new ArrayList<>();
        for (CalloutHistory.PingEntry entry : CalloutHistory.entries()) {
            String scope = scopeOf(entry);
            if (!scopes.contains(scope)) {
                scopes.add(scope);
            }
        }
        Collections.sort(scopes);
        return scopes;
    }

    private String selectedScope() {
        List<String> scopes = availableScopes();
        if (scopeIndex <= 0 || scopes.isEmpty()) {
            return null;
        }
        if (scopeIndex > scopes.size()) {
            scopeIndex = 0;
            return null;
        }
        return scopes.get(scopeIndex - 1);
    }

    private String scopeOf(CalloutHistory.PingEntry entry) {
        if (entry == null || entry.scope == null || entry.scope.isBlank()) {
            return Component.translatable("callout.history.scope_unknown").getString();
        }
        return entry.scope;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();

        if (button == 0) {
            List<CalloutHistory.PingEntry> entries = filteredEntries();
            int pageSize = pageSize();
            int start = page * pageSize;
            int end = Math.min(entries.size(), start + pageSize);
            int left = 16;
            int contentWidth = Math.max(200, this.width - 32);
            int top = 46;
            int y = top;

            for (int i = start; i < end; i++) {
                CalloutHistory.PingEntry entry = entries.get(i);
                int curX = left + 12;
                int headerY = y + 8;
                String idxStr = "#" + (i + 1);
                curX += this.font.width(idxStr) + 8 + this.font.width("·") + 8 + this.font.width(safeText(entry.time)) + 8 + this.font.width("·") + 8;
                String senderStr = entry.sender == null ? "" : entry.sender;
                int senderWidth = this.font.width(senderStr);

                if (mouseX >= curX && mouseX <= curX + senderWidth && mouseY >= headerY && mouseY <= headerY + 10) {
                    if (entry.sender != null && !entry.sender.isBlank() && !entry.sender.equals(Component.translatable("callout.history.sender.system").getString())) {
                        this.minecraft.setScreen(new ChatScreen("/msg " + entry.sender + " ", false));
                        return true;
                    }
                }

                int lineY = y + 26;
                for (DisplayLine line : displayLines(entry)) {
                    if (mouseX >= left + 12 && mouseX <= left + contentWidth - 12 && mouseY >= lineY && mouseY <= lineY + 12) {
                        String msgText = safeText(line.line.message());
                        if (this.minecraft != null && this.minecraft.keyboardHandler != null) {
                            this.minecraft.keyboardHandler.setClipboard(msgText);
                            this.toastText = Component.translatable("callout.history.copied").getString();
                            this.toastX = (int) mouseX;
                            this.toastY = (int) mouseY - 12;
                            this.toastExpiration = System.currentTimeMillis() + 1500;
                            return true;
                        }
                    }
                    lineY += 12;
                }

                y += CARD_HEIGHT + CARD_GAP;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta) {
        graphics.fill(0, 0, this.width, this.height, 0xF00B0E14);
        graphics.fill(0, 0, this.width, 36, 0xFF141A24);
        graphics.centeredText(this.font, this.title, this.width / 2, 12, 0xFFFFFFFF);

        List<CalloutHistory.PingEntry> entries = filteredEntries();
        int total = entries.size();
        int left = 16;
        int contentWidth = Math.max(200, this.width - 32);
        int top = 46;

        Component countComponent = Component.translatable("callout.history.count", total, CalloutConfig.loadIfChanged().maxPingHistory, Math.min(page + 1, maxPage() + 1), maxPage() + 1);
        int countWidth = this.font.width(countComponent) + 12;
        graphics.fill(left, 32, left + countWidth, 44, 0xFF1D2430);
        graphics.text(this.font, countComponent, left + 6, 34, 0xFF8BE9FD);

        if (entries.isEmpty()) {
            graphics.fill(left, top, left + contentWidth, Math.min(this.height - 44, top + 96), 0xFF151C26);
            graphics.outline(left, top, contentWidth, 96, 0xFF2C394A);
            graphics.centeredText(this.font, Component.translatable("callout.history.empty"), this.width / 2, top + 42, 0xFFD8DEE9);
            super.extractRenderState(graphics, mouseX, mouseY, tickDelta);
            renderToast(graphics);
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
        renderToast(graphics);
    }

    private void renderToast(GuiGraphicsExtractor graphics) {
        if (toastText != null && System.currentTimeMillis() < toastExpiration) {
            int tw = this.font.width(toastText) + 8;
            graphics.fill(toastX - 4, toastY - 2, toastX + tw - 4, toastY + 10, 0xE0000000);
            graphics.outline(toastX - 4, toastY - 2, tw, 12, 0xFF50FA7B);
            graphics.text(this.font, toastText, toastX, toastY, 0xFF50FA7B);
        }
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

        String timeText = safeText(entry.time);
        graphics.text(this.font, timeText, curX, headerY, 0xFFF8F8F2);
        curX += this.font.width(timeText) + 8;

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
        curX += trigW + 8;

        String scopeText = trim(scopeOf(entry), Math.max(40, left + width - curX - 12));
        int scopeW = this.font.width(scopeText) + 8;
        if (curX + scopeW < left + width - 8) {
            graphics.fill(curX, headerY - 2, curX + scopeW, headerY + 10, 0x3028D19C);
            graphics.text(this.font, scopeText, curX + 4, headerY, 0xFF28D19C);
        }

        graphics.fill(left + 8, top + 22, left + width - 8, top + 23, 0xFF232C3A);

        int lineY = top + 26;
        for (DisplayLine line : displayLines(entry)) {
            if (line.ping) {
                graphics.fill(left + 5, lineY - 1, left + width - 5, lineY + 11, 0x40FFD166);
                graphics.fill(left + 4, lineY - 1, left + 7, lineY + 11, 0xFFFFD166);
            }
            int color = line.ping ? 0xFFFFD166 : 0xFF98A6B5;
            String prefix = line.ping ? "> " : "  ";
            Component text = Component.literal(prefix + "[" + safeText(line.line.time()) + "] " + safeText(line.line.message()));
            graphics.text(this.font, trim(text.getString(), width - 26), left + 12, lineY, color);
            lineY += 12;
        }
    }

    private List<DisplayLine> displayLines(CalloutHistory.PingEntry entry) {
        List<DisplayLine> lines = new ArrayList<>();
        for (CalloutHistory.ChatLine line : safeLines(entry.before)) {
            lines.add(new DisplayLine(line, false));
        }
        if (entry.pingLine != null) {
            lines.add(new DisplayLine(entry.pingLine, true));
        }
        for (CalloutHistory.ChatLine line : CalloutHistory.afterLines(entry)) {
            lines.add(new DisplayLine(line, false));
        }
        return lines;
    }

    private String trim(String value, int pixelWidth) {
        String safeValue = safeText(value);
        int ellipsisWidth = this.font.width("...");
        if (pixelWidth <= ellipsisWidth) {
            return "...";
        }
        return this.font.width(safeValue) <= pixelWidth ? safeValue : this.font.plainSubstrByWidth(safeValue, pixelWidth - ellipsisWidth) + "...";
    }

    private static String safeText(String value) {
        return value == null ? "" : value;
    }

    private static List<CalloutHistory.ChatLine> safeLines(List<CalloutHistory.ChatLine> lines) {
        return lines == null ? List.of() : lines;
    }

    private int pageSize() {
        return Math.max(1, (this.height - 92) / (CARD_HEIGHT + CARD_GAP));
    }

    private int maxPage() {
        int size = filteredEntries().size();
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
        if (scopeButton != null) {
            scopeButton.setMessage(scopeLabel());
        }
    }

    private record DisplayLine(CalloutHistory.ChatLine line, boolean ping) {
    }
}

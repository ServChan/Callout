package org.lts.callout;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

public final class CalloutHistory {
    private static final int MAX_CHAT_BUFFER = 256;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path HISTORY_PATH = FabricLoader.getInstance().getConfigDir().resolve("callout_history.json");
    private static final Path HISTORY_BACKUP_PATH = HISTORY_PATH.resolveSibling(HISTORY_PATH.getFileName() + ".bak");

    private static final Deque<ChatLine> chatBuffer = new ArrayDeque<>();
    private static final Deque<PingEntry> pings = new ArrayDeque<>();
    private static final List<PingEntry> awaitingAfter = new ArrayList<>();
    private static final Deque<PendingPing> pendingPings = new ArrayDeque<>();
    private static long nextSequence;
    private static String currentScope = "";
    private static boolean dirty = false;

    private CalloutHistory() {
    }

    public static synchronized void load() {
        if (!CalloutConfig.loadIfChanged().persistHistory) {
            return;
        }
        if (!loadFrom(HISTORY_PATH) && !loadFrom(HISTORY_BACKUP_PATH)) {
            pings.clear();
        }
    }

    private static boolean loadFrom(Path path) {
        if (!Files.isRegularFile(path)) {
            return false;
        }
        try (Reader reader = Files.newBufferedReader(path)) {
            Type type = new TypeToken<List<PingEntry>>(){}.getType();
            List<PingEntry> loaded = GSON.fromJson(reader, type);
            if (loaded != null) {
                pings.clear();
                for (PingEntry entry : loaded) {
                    PingEntry normalized = normalize(entry);
                    if (normalized != null) {
                        pings.addLast(normalized);
                    }
                }
                trimHistory(CalloutConfig.loadIfChanged().maxPingHistory);
                return true;
            }
        } catch (Exception exception) {
            CalloutClient.LOGGER.warn("Failed to load history from {}", path, exception);
        }
        return false;
    }

    public static synchronized void save() {
        dirty = false;
        if (!CalloutConfig.loadIfChanged().persistHistory) {
            return;
        }
        try {
            writeAtomically(HISTORY_PATH, new ArrayList<>(pings));
        } catch (Exception exception) {
            CalloutClient.LOGGER.warn("Failed to save history to {}", HISTORY_PATH, exception);
        }
    }

    public static synchronized void flushIfDirty() {
        if (dirty) {
            save();
        }
    }

    public static synchronized ChatLine observeDisplayed(Component message) {
        CalloutConfig config = CalloutConfig.loadIfChanged();
        String text = sanitizeText(message == null ? "" : message.getString());

        ChatLine line = new ChatLine(
                nextSequence++,
                LocalTime.now().format(TIME_FORMAT),
                text
        );

        chatBuffer.addLast(line);
        while (chatBuffer.size() > maxChatBuffer(config)) {
            chatBuffer.removeFirst();
        }

        attachPendingPings(line);
        updateAwaitingAfter(line);

        return line;
    }

    static String sanitizeText(String text) {
        if (text == null || text.isBlank()) {
            return text == null ? "" : text;
        }
        return text.replaceAll("\\[?[^\\s\\[\\]]{1,32}\\s+head\\]", "").trim();
    }

    private static final long PENDING_PING_TIMEOUT_MS = 4000L;

    public static synchronized void queuePing(String sender, String matchText, CalloutConfig.Trigger trigger) {
        PendingPing pendingPing = new PendingPing(
                sender == null || sender.isBlank() ? Component.translatable("callout.history.sender.system").getString() : sender,
                matchText == null ? "" : matchText,
                trigger.word,
                trigger.regex,
                currentScope,
                System.currentTimeMillis()
        );

        if (!pendingPing.matchText.isBlank() && attachToRecentLine(pendingPing)) {
            return;
        }

        pendingPings.addLast(pendingPing);
        while (pendingPings.size() > 16) {
            pendingPings.removeFirst();
        }
    }

    private static boolean attachToRecentLine(PendingPing pendingPing) {
        List<ChatLine> buffer = new ArrayList<>(chatBuffer);
        if (buffer.isEmpty()) {
            return false;
        }

        int scanFrom = Math.max(0, buffer.size() - 3);
        for (int i = buffer.size() - 1; i >= scanFrom; i--) {
            ChatLine line = buffer.get(i);
            if (lineMatchesPending(line, pendingPing)) {
                recordPing(line, pendingPing);
                return true;
            }
        }
        return false;
    }

    private static void attachPendingPings(ChatLine line) {
        if (pendingPings.isEmpty()) {
            return;
        }

        List<PendingPing> copy = new ArrayList<>(pendingPings);
        for (PendingPing pendingPing : copy) {
            if (lineMatchesPending(line, pendingPing)) {
                recordPing(line, pendingPing);
                pendingPings.remove(pendingPing);
                return;
            }
        }

        long now = System.currentTimeMillis();
        for (PendingPing pendingPing : copy) {
            if (now - pendingPing.createdAt() >= PENDING_PING_TIMEOUT_MS) {
                recordPing(line, pendingPing);
                pendingPings.remove(pendingPing);
                return;
            }
        }
    }

    private static boolean lineMatchesPending(ChatLine line, PendingPing pendingPing) {
        if (pendingPing.matchText == null || pendingPing.matchText.isBlank()) {
            return true;
        }
        String msg = line.message().toLowerCase(Locale.ROOT);
        String match = pendingPing.matchText.toLowerCase(Locale.ROOT);
        return msg.contains(match);
    }

    private static void recordPing(ChatLine pingLine, PendingPing pendingPing) {
        CalloutConfig config = CalloutConfig.loadIfChanged();
        List<ChatLine> buffer = new ArrayList<>(chatBuffer);
        int index = -1;
        for (int i = 0; i < buffer.size(); i++) {
            if (buffer.get(i).sequence() == pingLine.sequence()) {
                index = i;
                break;
            }
        }

        List<ChatLine> before = new ArrayList<>();
        if (index > 0) {
            int from = Math.max(0, index - config.contextBefore);
            for (int i = from; i < index; i++) {
                before.add(buffer.get(i));
            }
        }

        PingEntry entry = new PingEntry(
                pingLine.time,
                pendingPing.sender,
                pendingPing.trigger,
                pendingPing.regex,
                pendingPing.scope,
                before,
                pingLine
        );

        pings.addFirst(entry);
        trimHistory(config.maxPingHistory);
        awaitingAfter.add(entry);
        dirty = true;
    }

    private static void updateAwaitingAfter(ChatLine line) {
        int contextAfter = CalloutConfig.loadIfChanged().contextAfter;
        boolean changed = false;
        for (int i = awaitingAfter.size() - 1; i >= 0; i--) {
            PingEntry entry = awaitingAfter.get(i);
            if (line.sequence() > entry.pingLine.sequence() && entry.after.size() < contextAfter) {
                entry.after.add(line);
                changed = true;
            }
            if (entry.after.size() >= contextAfter) {
                awaitingAfter.remove(i);
            }
        }
        if (changed) {
            dirty = true;
        }
    }

    public static synchronized List<ChatLine> afterLines(PingEntry entry) {
        fillAfterFromBuffer(entry);
        return Collections.unmodifiableList(safeLines(entry.after));
    }

    public static synchronized List<PingEntry> entries() {
        return Collections.unmodifiableList(new ArrayList<>(pings));
    }


    public static synchronized void setCurrentScope(String scope) {
        currentScope = scope == null ? "" : scope;
    }

    public static synchronized String currentScope() {
        return currentScope;
    }

    public static synchronized void resetChatBuffer() {
        chatBuffer.clear();
        awaitingAfter.clear();
        pendingPings.clear();
    }


    private static void writeAtomically(Path path, Object value) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temporary)) {
                GSON.toJson(value, writer);
            }
            if (Files.isRegularFile(path)) {
                Files.copy(path, path.resolveSibling(path.getFileName() + ".bak"),
                        StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            Files.deleteIfExists(temporary);
            throw exception;
        }
    }




    public static synchronized void clear() {
        chatBuffer.clear();
        pings.clear();
        awaitingAfter.clear();
        pendingPings.clear();
        dirty = false;
        try {
            Files.deleteIfExists(HISTORY_PATH);
            Files.deleteIfExists(HISTORY_BACKUP_PATH);
        } catch (IOException exception) {
            CalloutClient.LOGGER.warn("Failed to clear persisted callout history", exception);
        }
    }

    private static void fillAfterFromBuffer(PingEntry entry) {
        int contextAfter = CalloutConfig.loadIfChanged().contextAfter;
        List<ChatLine> after = safeLines(entry.after);
        if (after.size() >= contextAfter) {
            return;
        }

        for (ChatLine line : chatBuffer) {
            if (entry.pingLine == null || line.sequence() <= entry.pingLine.sequence()) {
                continue;
            }
            boolean exists = false;
            for (ChatLine existing : after) {
                if (existing.sequence() == line.sequence()) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                after.add(line);
            }
            if (after.size() >= contextAfter) {
                awaitingAfter.remove(entry);
                return;
            }
        }
    }

    private static void trimHistory(int maxPingHistory) {
        while (pings.size() > maxPingHistory) {
            PingEntry removed = pings.removeLast();
            awaitingAfter.remove(removed);
        }
    }

    private static PingEntry normalize(PingEntry entry) {
        if (entry == null || entry.pingLine == null) {
            return null;
        }
        return new PingEntry(
                entry.time,
                entry.sender,
                entry.trigger,
                entry.regex,
                entry.scope,
                safeLines(entry.before),
                entry.pingLine,
                safeLines(entry.after)
        );
    }

    private static List<ChatLine> safeLines(List<ChatLine> lines) {
        return lines == null ? new ArrayList<>() : lines;
    }

    private static int maxChatBuffer(CalloutConfig config) {
        return Math.max(MAX_CHAT_BUFFER, config.contextBefore + config.contextAfter + config.maxPingHistory);
    }

    public record ChatLine(long sequence, String time, String message) {
    }

    private record PendingPing(String sender, String matchText, String trigger, boolean regex, String scope, long createdAt) {
    }

    public static final class PingEntry {
        public final String time;
        public final String sender;
        public final String trigger;
        public final boolean regex;
        public final String scope;
        public final List<ChatLine> before;
        public final ChatLine pingLine;
        public final List<ChatLine> after;

        public PingEntry(String time, String sender, String trigger, boolean regex, String scope, List<ChatLine> before, ChatLine pingLine) {
            this(time, sender, trigger, regex, scope, before, pingLine, new ArrayList<>());
        }

        public PingEntry(String time, String sender, String trigger, boolean regex, String scope, List<ChatLine> before, ChatLine pingLine, List<ChatLine> after) {
            this.time = time;
            this.sender = sender;
            this.trigger = trigger;
            this.regex = regex;
            this.scope = scope == null ? "" : scope;
            this.before = before == null ? new ArrayList<>() : before;
            this.pingLine = pingLine;
            this.after = after == null ? new ArrayList<>() : after;
        }
    }
}

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
    private static final int MAX_PINGS = 100;
    private static final int CONTEXT_BEFORE = 5;
    private static final int CONTEXT_AFTER = 5;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path HISTORY_PATH = FabricLoader.getInstance().getConfigDir().resolve("callout_history.json");

    private static final Deque<ChatLine> chatBuffer = new ArrayDeque<>();
    private static final Deque<PingEntry> pings = new ArrayDeque<>();
    private static final List<PingEntry> awaitingAfter = new ArrayList<>();
    private static final Deque<PendingPing> pendingPings = new ArrayDeque<>();
    private static long nextSequence;

    private CalloutHistory() {
    }

    public static synchronized void load() {
        if (!Files.exists(HISTORY_PATH)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(HISTORY_PATH)) {
            Type type = new TypeToken<List<PingEntry>>(){}.getType();
            List<PingEntry> loaded = GSON.fromJson(reader, type);
            if (loaded != null) {
                pings.clear();
                pings.addAll(loaded);
            }
        } catch (Exception exception) {
            CalloutClient.LOGGER.warn("Failed to load history from {}", HISTORY_PATH, exception);
        }
    }

    public static synchronized void save() {
        try {
            Files.createDirectories(HISTORY_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(HISTORY_PATH)) {
                GSON.toJson(new ArrayList<>(pings), writer);
            }
        } catch (Exception exception) {
            CalloutClient.LOGGER.warn("Failed to save history to {}", HISTORY_PATH, exception);
        }
    }

    public static synchronized ChatLine observeDisplayed(Component message) {
        String text = sanitize(message == null ? "" : message.getString());
        ChatLine line = new ChatLine(
                nextSequence++,
                LocalTime.now().format(TIME_FORMAT),
                text
        );

        chatBuffer.addLast(line);
        while (chatBuffer.size() > MAX_CHAT_BUFFER) {
            chatBuffer.removeFirst();
        }

        attachPendingPings(line);
        updateAwaitingAfter(line);

        return line;
    }

    private static String sanitize(String text) {
        if (text == null || text.isBlank()) {
            return text == null ? "" : text;
        }
        return text.replaceAll("\\[?[A-Za-z0-9_]{3,16}\\s+head\\]", "")
                   .replaceAll("head\\]", "")
                   .trim();
    }

    public static synchronized void queuePing(String sender, String matchText, CalloutConfig.Trigger trigger) {
        PendingPing pendingPing = new PendingPing(
                sender == null || sender.isBlank() ? Component.translatable("callout.history.sender.system").getString() : sender,
                matchText == null ? "" : matchText,
                trigger.word,
                trigger.regex
        );

        pendingPings.addLast(pendingPing);
        while (pendingPings.size() > 16) {
            pendingPings.removeFirst();
        }
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
    }

    private static boolean lineMatchesPending(ChatLine line, PendingPing pendingPing) {
        if (pendingPing.matchText == null || pendingPing.matchText.isBlank()) {
            return true;
        }
        String msg = line.message().toLowerCase(Locale.ROOT);
        String match = pendingPing.matchText.toLowerCase(Locale.ROOT);
        String trigger = pendingPing.trigger == null ? "" : pendingPing.trigger.toLowerCase(Locale.ROOT);
        return msg.contains(match) || (!trigger.isBlank() && msg.contains(trigger));
    }

    private static void recordPing(ChatLine pingLine, PendingPing pendingPing) {
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
            int from = Math.max(0, index - CONTEXT_BEFORE);
            for (int i = from; i < index; i++) {
                before.add(buffer.get(i));
            }
        }

        PingEntry entry = new PingEntry(
                pingLine.time,
                pendingPing.sender,
                pendingPing.trigger,
                pendingPing.regex,
                before,
                pingLine
        );

        pings.addFirst(entry);
        while (pings.size() > MAX_PINGS) {
            PingEntry removed = pings.removeLast();
            awaitingAfter.remove(removed);
        }
        awaitingAfter.add(entry);
        save();
    }

    private static void updateAwaitingAfter(ChatLine line) {
        boolean changed = false;
        for (int i = awaitingAfter.size() - 1; i >= 0; i--) {
            PingEntry entry = awaitingAfter.get(i);
            if (line.sequence() > entry.pingLine.sequence() && entry.after.size() < CONTEXT_AFTER) {
                entry.after.add(line);
                changed = true;
            }
            if (entry.after.size() >= CONTEXT_AFTER) {
                awaitingAfter.remove(i);
            }
        }
        if (changed) {
            save();
        }
    }

    public static synchronized List<ChatLine> afterLines(PingEntry entry) {
        fillAfterFromBuffer(entry);
        return Collections.unmodifiableList(entry.after);
    }

    public static synchronized List<PingEntry> entries() {
        return Collections.unmodifiableList(new ArrayList<>(pings));
    }

    public static synchronized void resetSessionBuffer() {
        chatBuffer.clear();
        awaitingAfter.clear();
        pendingPings.clear();
    }

    public static synchronized void clear() {
        chatBuffer.clear();
        pings.clear();
        awaitingAfter.clear();
        pendingPings.clear();
        try {
            Files.deleteIfExists(HISTORY_PATH);
        } catch (IOException ignored) {
        }
    }

    private static void fillAfterFromBuffer(PingEntry entry) {
        if (entry.after.size() >= CONTEXT_AFTER) {
            return;
        }

        for (ChatLine line : chatBuffer) {
            if (line.sequence() <= entry.pingLine.sequence()) {
                continue;
            }
            boolean exists = false;
            for (ChatLine existing : entry.after) {
                if (existing.sequence() == line.sequence()) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                entry.after.add(line);
            }
            if (entry.after.size() >= CONTEXT_AFTER) {
                awaitingAfter.remove(entry);
                return;
            }
        }
    }

    public record ChatLine(long sequence, String time, String message) {
    }

    private record PendingPing(String sender, String matchText, String trigger, boolean regex) {
    }

    public static final class PingEntry {
        public final String time;
        public final String sender;
        public final String trigger;
        public final boolean regex;
        public final List<ChatLine> before;
        public final ChatLine pingLine;
        public final List<ChatLine> after;

        public PingEntry(String time, String sender, String trigger, boolean regex, List<ChatLine> before, ChatLine pingLine) {
            this.time = time;
            this.sender = sender;
            this.trigger = trigger;
            this.regex = regex;
            this.before = before == null ? new ArrayList<>() : before;
            this.pingLine = pingLine;
            this.after = new ArrayList<>();
        }
    }
}


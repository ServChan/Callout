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
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeHierarchyAdapter(Component.class, new com.google.gson.JsonSerializer<Component>() {
                @Override
                public com.google.gson.JsonElement serialize(Component src, java.lang.reflect.Type typeOfSrc, com.google.gson.JsonSerializationContext context) {
                    try {
                        return net.minecraft.network.chat.ComponentSerialization.CODEC
                                .encodeStart(com.mojang.serialization.JsonOps.INSTANCE, src)
                                .getOrThrow(IllegalStateException::new);
                    } catch (Exception e) {
                        return com.google.gson.JsonNull.INSTANCE;
                    }
                }
            })
            .registerTypeHierarchyAdapter(Component.class, new com.google.gson.JsonDeserializer<Component>() {
                @Override
                public Component deserialize(com.google.gson.JsonElement json, java.lang.reflect.Type typeOfT, com.google.gson.JsonDeserializationContext context) {
                    try {
                        return net.minecraft.network.chat.ComponentSerialization.CODEC
                                .parse(com.mojang.serialization.JsonOps.INSTANCE, json)
                                .getOrThrow(IllegalStateException::new);
                    } catch (Exception e) {
                        return Component.empty();
                    }
                }
            })
            .create();
    private static final Path HISTORY_PATH = FabricLoader.getInstance().getConfigDir().resolve("callout_history.json");

    private static final Deque<ChatLine> chatBuffer = new ArrayDeque<>();
    private static final Deque<PingEntry> pings = new ArrayDeque<>();
    private static final List<PingEntry> awaitingAfter = new ArrayList<>();
    private static final Deque<PendingPing> pendingPings = new ArrayDeque<>();
    private static long nextSequence;
    private static String currentScope = "";

    private CalloutHistory() {
    }

    public static synchronized void load() {
        if (!CalloutConfig.loadIfChanged().persistHistory) {
            return;
        }
        if (!Files.exists(HISTORY_PATH)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(HISTORY_PATH)) {
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
            }
        } catch (Exception exception) {
            CalloutClient.LOGGER.warn("Failed to load history from {}", HISTORY_PATH, exception);
        }
    }

    public static synchronized void save() {
        if (!CalloutConfig.loadIfChanged().persistHistory) {
            return;
        }
        try {
            Files.createDirectories(HISTORY_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(HISTORY_PATH)) {
                GSON.toJson(new ArrayList<>(pings), writer);
            }
        } catch (Exception exception) {
            CalloutClient.LOGGER.warn("Failed to save history to {}", HISTORY_PATH, exception);
        }
    }

    public static boolean isRestoringChat = false;

    public static synchronized ChatLine observeDisplayed(Component message) {
        if (isRestoringChat) return null;
        
        CalloutConfig config = CalloutConfig.loadIfChanged();
        String text = sanitize(message == null ? "" : message.getString());

        ChatLine line = new ChatLine(
                nextSequence++,
                LocalTime.now().format(TIME_FORMAT),
                text,
                message
        );

        chatBuffer.addLast(line);
        while (chatBuffer.size() > maxChatBuffer(config)) {
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
                trigger.regex,
                currentScope
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
        ChatLine line = buffer.get(buffer.size() - 1);
        if (lineMatchesPending(line, pendingPing)) {
            recordPing(line, pendingPing);
            return true;
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
        save();
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
            save();
        }
    }

    public static synchronized List<ChatLine> afterLines(PingEntry entry) {
        fillAfterFromBuffer(entry);
        return Collections.unmodifiableList(safeLines(entry.after));
    }

    public static synchronized List<PingEntry> entries() {
        return Collections.unmodifiableList(new ArrayList<>(pings));
    }

    public static synchronized List<ChatLine> getChatBuffer() {
        return new ArrayList<>(chatBuffer);
    }

    public static synchronized void setCurrentScope(String scope) {
        currentScope = scope == null ? "" : scope;
    }

    public static synchronized String currentScope() {
        return currentScope;
    }

    public static synchronized void resetSessionBuffer() {
        chatBuffer.clear();
        awaitingAfter.clear();
        pendingPings.clear();
    }

    public static synchronized void saveSessionBuffer(String scope) {
        if (scope == null || scope.isBlank()) return;
        Path path = FabricLoader.getInstance().getConfigDir().resolve("callout_sessions").resolve(scope.replaceAll("[^a-zA-Z0-9.-]", "_") + ".json");
        try {
            Files.createDirectories(path.getParent());
            try (java.io.Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(new ArrayList<>(chatBuffer), writer);
            }
        } catch (Exception e) {
            CalloutClient.LOGGER.warn("Failed to save session buffer to {}", path, e);
        }
    }

    public static synchronized void loadSessionBuffer(String scope) {
        chatBuffer.clear();
        if (scope == null || scope.isBlank()) return;
        Path path = FabricLoader.getInstance().getConfigDir().resolve("callout_sessions").resolve(scope.replaceAll("[^a-zA-Z0-9.-]", "_") + ".json");
        if (!Files.exists(path)) return;
        
        try (java.io.Reader reader = Files.newBufferedReader(path)) {
            java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<List<ChatLine>>(){}.getType();
            List<ChatLine> loaded = GSON.fromJson(reader, type);
            if (loaded != null) {
                chatBuffer.addAll(loaded);
            }
        } catch (Exception e) {
            CalloutClient.LOGGER.warn("Failed to load session buffer from {}", path, e);
        }
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

    public record ChatLine(long sequence, String time, String message, Component component) {
        public ChatLine(long sequence, String time, String message) {
            this(sequence, time, message, null);
        }
    }

    private record PendingPing(String sender, String matchText, String trigger, boolean regex, String scope) {
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

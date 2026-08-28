package org.lts.callout;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;
import org.lts.callout.gui.CalloutHistoryScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class CalloutClient implements ClientModInitializer {
    public static final String MOD_ID = "callout";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
    private static KeyMapping historyKey;
    private boolean wasInWorld = false;
    private String lastScope = "";
    private int tickCount = 0;
    private int historyFlushTicks = 0;
    private boolean disconnected = true;

    private static final Pattern SENDER_CHAT_PATTERN = Pattern.compile("(?:^|.*?[\\s\\[\\]<>👤])([a-zA-Z0-9_]{3,16})\\s*[:»>|-]+\\s*(.*)");
    private static final Pattern SENDER_USERNAME_PATTERN = Pattern.compile("([a-zA-Z0-9_]{3,16})");

    @Override
    public void onInitializeClient() {
        CalloutConfig.load();
        CalloutHistory.load();
        historyKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.callout.ping_history",
                InputConstants.Type.KEYSYM,
                InputConstants.KEY_APOSTROPHE,
                CATEGORY
        ));

        ClientReceiveMessageEvents.CHAT.register((message, playerChatMessage, sender, boundChatType, timeStamp) -> {
            handleMessage(message, playerMessageText(playerChatMessage, message, sender), sender);
        });
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            // Skip the action bar: other mods/servers push coordinates, timers and
            // similar overlay text there, which would produce false pings.
            if (!overlay) {
                handleMessage(message, message.getString(), null);
            }
        });
        ClientSendMessageEvents.CHAT.register(this::onSendChat);
        ClientSendMessageEvents.COMMAND.register(this::onSendCommand);
        ClientTickEvents.END_CLIENT_TICK.register(this::handleClientTick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(minecraft -> {
            if (wasInWorld) {
                CalloutHistory.saveSessionBuffer(lastScope);
                CalloutHistory.save();
            }
        });
    }

    private static final java.util.Deque<SentMessage> RECENT_SENT_MESSAGES = new java.util.ArrayDeque<>();

    private record SentMessage(String text, long timestamp) {}

    private void onSendChat(String message) {
        if (message != null && !message.isBlank()) {
            recordSentMessage(message);
        }
    }

    private void onSendCommand(String command) {
        if (command == null || command.isBlank()) return;
        String trimmed = command.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.startsWith("m ") || lower.startsWith("msg ") || lower.startsWith("tell ") || lower.startsWith("w ") || lower.startsWith("r ")) {
            int firstSpace = trimmed.indexOf(' ');
            if (firstSpace > 0) {
                String sub = trimmed.substring(firstSpace + 1).trim();
                if (lower.startsWith("r ")) {
                    recordSentMessage(sub);
                } else {
                    int targetSpace = sub.indexOf(' ');
                    if (targetSpace > 0) {
                        recordSentMessage(sub.substring(targetSpace + 1).trim());
                    }
                }
            }
        }
    }

    private static void recordSentMessage(String text) {
        if (text == null || text.isBlank()) return;
        long now = System.currentTimeMillis();
        synchronized (RECENT_SENT_MESSAGES) {
            RECENT_SENT_MESSAGES.addLast(new SentMessage(text.trim(), now));
            while (RECENT_SENT_MESSAGES.size() > 20) {
                RECENT_SENT_MESSAGES.removeFirst();
            }
        }
    }

    /**
     * Minimum overlap length before a recently sent message is treated as the source
     * of an incoming line. Without this, short fragments the player just typed
     * ("k", "gg", "lol") would suppress every legitimate ping that happens to
     * contain them as a substring.
     */
    private static final int MIN_ECHO_OVERLAP = 5;

    private static boolean isRecentlySentByPlayer(String messageText) {
        if (messageText == null || messageText.isBlank()) return false;
        long now = System.currentTimeMillis();
        String lowerMsg = messageText.toLowerCase(Locale.ROOT).trim();
        synchronized (RECENT_SENT_MESSAGES) {
            java.util.Iterator<SentMessage> iterator = RECENT_SENT_MESSAGES.iterator();
            while (iterator.hasNext()) {
                SentMessage sent = iterator.next();
                if (now - sent.timestamp > 12000) {
                    iterator.remove();
                    continue;
                }
                String lowerSent = sent.text.toLowerCase(Locale.ROOT).trim();
                if (lowerSent.isBlank()) {
                    continue;
                }
                if (lowerSent.equals(lowerMsg)) {
                    return true;
                }
                if (lowerMsg.contains(lowerSent) && lowerSent.length() >= MIN_ECHO_OVERLAP) {
                    return true;
                }
                // Server reformatted/truncated our line: only accept when the incoming
                // text is itself a substantial chunk of what we sent.
                if (lowerSent.contains(lowerMsg) && lowerMsg.length() >= MIN_ECHO_OVERLAP
                        && lowerSent.length() <= lowerMsg.length() * 3L) {
                    return true;
                }
            }
        }
        return false;
    }

    private void handleClientTick(Minecraft minecraft) {
        boolean isInWorld = minecraft.level != null && minecraft.player != null;

        if (!isInWorld && minecraft.getConnection() == null) {
            disconnected = true;
            WorldScopeTracker.clear();
        }

        if (isInWorld) {
            String scope = currentScope(minecraft);
            CalloutHistory.setCurrentScope(scope);
            CalloutConfig config = CalloutConfig.loadIfChanged();

            if (!wasInWorld) {
                if (!lastScope.isBlank() && !lastScope.equals(scope) && config.clearHistoryOnScopeChange) {
                    CalloutHistory.clear();
                } else if (disconnected) {
                    CalloutHistory.loadSessionBuffer(scope);
                    CalloutHistory.isRestoringChat = true;
                    for (CalloutHistory.ChatLine line : CalloutHistory.getChatBuffer()) {
                        if (line.component() != null) {
                            try {
                                minecraft.player.sendSystemMessage(line.component());
                            } catch (Exception e) {
                                LOGGER.warn("Failed to inject historical chat message", e);
                            }
                        }
                    }
                    CalloutHistory.isRestoringChat = false;
                }
                disconnected = false;
            } else if (!lastScope.isBlank() && !lastScope.equals(scope)) {
                CalloutHistory.saveSessionBuffer(lastScope);
                CalloutHistory.save();

                if (config.clearHistoryOnScopeChange) {
                    CalloutHistory.clear();
                } else {
                    CalloutHistory.resetSessionBuffer();
                    CalloutHistory.loadSessionBuffer(scope);
                }
            }
            lastScope = scope;

            historyFlushTicks++;
            if (historyFlushTicks >= 100) {
                historyFlushTicks = 0;
                CalloutHistory.flushIfDirty();
            }

            tickCount++;
            if (tickCount >= 1200) {
                tickCount = 0;
                CalloutHistory.saveSessionBuffer(lastScope);
                CalloutHistory.save();
            }
        }
        if (wasInWorld && !isInWorld) {
            CalloutHistory.saveSessionBuffer(lastScope);
            CalloutHistory.save();
        }
        wasInWorld = isInWorld;

        while (historyKey.consumeClick()) {
            if (isInWorld && MinecraftScreenAccess.getScreen(minecraft) == null) {
                minecraft.setScreenAndShow(new CalloutHistoryScreen());
            }
        }
    }

    private static String currentScope(Minecraft minecraft) {
        ServerData serverData = minecraft.getCurrentServer();
        if (serverData != null) {
            // A single server address/name can expose multiple distinct worlds behind the
            // same dimension (e.g. minigame lobbies); disambiguate with the stable world
            // seed captured from the login/respawn packets so their history/session data
            // doesn't collide. See WorldScopeTracker and AGENTS.md persistence rules.
            if (serverData.ip != null && !serverData.ip.isBlank()) {
                return serverData.ip + WorldScopeTracker.seedSuffix();
            }
            if (serverData.name != null && !serverData.name.isBlank()) {
                return serverData.name + WorldScopeTracker.seedSuffix();
            }
        }

        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server != null && server.getWorldData() != null) {
            String levelName = server.getWorldData().getLevelName();
            if (levelName != null && !levelName.isBlank()) {
                return "singleplayer:" + levelName;
            }
        }

        if (minecraft.level != null) {
            return minecraft.level.dimension().identifier().toString();
        }
        return "";
    }

    private static String playerMessageText(PlayerChatMessage playerChatMessage, Component fallbackMessage, GameProfile sender) {
        if (playerChatMessage != null) {
            return playerChatMessage.signedContent();
        }
        if (fallbackMessage == null) {
            return "";
        }
        return fallbackMessage.getString();
    }

    private record ParsedMessage(String senderName, String bodyText, boolean isOwnMessage) {}

    private static ParsedMessage parseChatMessage(Component displayMessage, String rawMatchText, GameProfile senderProfile, Minecraft minecraft) {
        String fullText = displayMessage != null ? displayMessage.getString() : (rawMatchText != null ? rawMatchText : "");
        String cleanText = CalloutHistory.sanitizeText(fullText);

        String resolvedSender = senderProfile != null ? senderProfile.name() : null;
        String bodyText = rawMatchText != null && !rawMatchText.isBlank() ? rawMatchText : cleanText;
        boolean isOwn = false;

        String ownName = minecraft.player != null ? minecraft.player.getGameProfile().name() : null;

        // Layer 1: Check GameProfile sender UUID / name
        if (minecraft.player != null && senderProfile != null) {
            if (Objects.equals(minecraft.player.getGameProfile().id(), senderProfile.id())
                    || (ownName != null && ownName.equalsIgnoreCase(senderProfile.name()))) {
                isOwn = true;
            }
        }

        // Separate header and message body
        int sepIndex = findMainChatSeparator(cleanText);
        String header = sepIndex >= 0 ? cleanText.substring(0, sepIndex).trim() : cleanText;
        if (sepIndex >= 0 && (rawMatchText == null || rawMatchText.isBlank() || rawMatchText.equals(fullText))) {
            bodyText = cleanText.substring(sepIndex + 1).trim();
            bodyText = bodyText.replaceAll("^[:»>\\-─→|•›~=]+\\s*", "");
        }

        // Layer 2: Outgoing Private Message Check (e.g. [Вы -> Nick], [Я -> Nick], [Me -> Nick], [To ...])
        String lowerHeader = header.toLowerCase(Locale.ROOT);
        if (lowerHeader.startsWith("[вы ") || lowerHeader.startsWith("[я ") || lowerHeader.startsWith("[me ")
                || lowerHeader.startsWith("[you ") || lowerHeader.startsWith("[self ") || lowerHeader.startsWith("[to ")
                || lowerHeader.startsWith("кому ") || lowerHeader.startsWith("to ")) {
            isOwn = true;
            if (ownName != null) resolvedSender = ownName;
        }

        // Layer 3: Sender resolution and Header Inspection
        if (resolvedSender == null || resolvedSender.isBlank()) {
            if (ownName != null && containsUsernameWord(header, ownName)) {
                isOwn = true;
                resolvedSender = ownName;
            } else {
                String extracted = extractLastUsername(header);
                if (extracted != null) {
                    resolvedSender = extracted;
                    if (ownName != null && extracted.equalsIgnoreCase(ownName)) {
                        isOwn = true;
                    }
                } else {
                    Matcher legacyMatcher = SENDER_CHAT_PATTERN.matcher(cleanText);
                    if (legacyMatcher.find()) {
                        resolvedSender = legacyMatcher.group(1);
                        bodyText = legacyMatcher.group(2);
                        if (ownName != null && resolvedSender.equalsIgnoreCase(ownName)) {
                            isOwn = true;
                        }
                    }
                }
            }
        } else if (ownName != null && resolvedSender.equalsIgnoreCase(ownName)) {
            isOwn = true;
        }

        // Layer 4: Check recent local player outgoing sent messages
        if (!isOwn && isRecentlySentByPlayer(bodyText.isBlank() ? cleanText : bodyText)) {
            isOwn = true;
            if (ownName != null) resolvedSender = ownName;
        }

        return new ParsedMessage(resolvedSender, bodyText, isOwn);
    }

    private static int findMainChatSeparator(String text) {
        if (text == null || text.isBlank()) return -1;

        int searchStart = 0;
        while (searchStart < text.length() && text.charAt(searchStart) == '[') {
            int closeBracket = text.indexOf(']', searchStart);
            if (closeBracket > searchStart) {
                String bracketContent = text.substring(searchStart, closeBracket + 1);
                if (bracketContent.contains("->") || bracketContent.contains("─>") || bracketContent.contains("→")) {
                    return closeBracket;
                }
                searchStart = closeBracket + 1;
                while (searchStart < text.length() && Character.isWhitespace(text.charAt(searchStart))) {
                    searchStart++;
                }
            } else {
                break;
            }
        }

        for (int i = searchStart; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ':' || c == '»' || c == '>' || c == '→' || c == '›') {
                return i;
            }
        }

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ':' || c == '»' || c == '>' || c == '→' || c == '›') {
                return i;
            }
        }

        return -1;
    }

    private static boolean containsUsernameWord(String text, String ownName) {
        if (text == null || ownName == null || text.isBlank() || ownName.isBlank()) return false;
        Matcher matcher = SENDER_USERNAME_PATTERN.matcher(text);
        while (matcher.find()) {
            if (matcher.group(1).equalsIgnoreCase(ownName)) {
                return true;
            }
        }
        return false;
    }

    private static String extractLastUsername(String header) {
        if (header == null || header.isBlank()) return null;
        Matcher matcher = SENDER_USERNAME_PATTERN.matcher(header);
        String last = null;
        while (matcher.find()) {
            String word = matcher.group(1);
            String lower = word.toLowerCase(Locale.ROOT);
            if (lower.equals("head") || lower.equals("global") || lower.equals("local")
                    || lower.equals("admin") || lower.equals("vip") || lower.equals("chat")
                    || lower.equals("server") || lower.equals("staff") || lower.equals("mod")) {
                continue;
            }
            last = word;
        }
        return last;
    }

    private static void handleMessage(Component displayMessage, String matchText, GameProfile sender) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        CalloutConfig config = CalloutConfig.loadIfChanged();
        if (!config.enabled) {
            return;
        }

        ParsedMessage parsed = parseChatMessage(displayMessage, matchText, sender, minecraft);

        for (CalloutConfig.Trigger trigger : config.allTriggers(minecraft.player.getGameProfile().name())) {
            if (matches(trigger, parsed.bodyText, config.caseSensitive)) {
                if (parsed.isOwnMessage && !config.pingOwnMessages) {
                    showSelfTestHintOnce(config, trigger, parsed.bodyText, sender, parsed.senderName);
                    return;
                }
                CalloutHistory.queuePing(parsed.senderName, parsed.bodyText, trigger);
                playSound(trigger);
                return;
            }
        }
    }

    private static boolean matches(CalloutConfig.Trigger trigger, String message, boolean caseSensitive) {
        if (message == null || trigger.word == null || trigger.word.isBlank()) {
            return false;
        }

        if (!trigger.regex) {
            String haystack = caseSensitive ? message : message.toLowerCase(Locale.ROOT);
            String needle = caseSensitive ? trigger.word : trigger.word.toLowerCase(Locale.ROOT);
            return haystack.contains(needle);
        }

        int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        try {
            return compiledPattern(trigger.word, flags).matcher(message).find();
        } catch (PatternSyntaxException exception) {
            LOGGER.warn("Invalid Callout regex: {}", trigger.word, exception);
            return false;
        }
    }

    private static final java.util.concurrent.ConcurrentHashMap<String, Pattern> REGEX_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    private static Pattern compiledPattern(String pattern, int flags) {
        // Chat messages arrive at a high rate; recompiling every regex per message
        // was measurable overhead on busy servers. The key covers the flags so a
        // case-sensitivity toggle produces a distinct entry.
        Pattern cached = REGEX_CACHE.get(flags + " " + pattern);
        if (cached != null) {
            return cached;
        }
        Pattern compiled = Pattern.compile(pattern, flags);
        if (REGEX_CACHE.size() < 256) {
            REGEX_CACHE.put(flags + " " + pattern, compiled);
        }
        return compiled;
    }

    private static void showSelfTestHintOnce(CalloutConfig config, CalloutConfig.Trigger trigger, String matchText, GameProfile sender, String resolvedSender) {
        if (config.selfTestHintShown) {
            return;
        }

        config.selfTestHintShown = true;
        CalloutConfig.save(config);
        CalloutHistory.queuePing(resolvedSender != null ? resolvedSender : (sender != null ? sender.name() : null), matchText, trigger);
        playSound(trigger);

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.sendSystemMessage(selfTestMessage());
        }
    }

    private static Component selfTestMessage() {
        MutableComponent prefix = Component.literal("[Callout] ").withStyle(ChatFormatting.AQUA);
        MutableComponent detected = Component.translatable("callout.message.self_test_detected").withStyle(ChatFormatting.GREEN);
        MutableComponent ignored = Component.translatable("callout.message.self_test_ignored").withStyle(ChatFormatting.YELLOW);
        MutableComponent action = Component.translatable("callout.message.self_test_action").withStyle(ChatFormatting.GRAY);
        return prefix.append(detected).append(Component.literal(" ")).append(ignored).append(Component.literal(" ")).append(action);
    }

    private static void playSound(CalloutConfig.Trigger trigger) {
        Identifier soundId = Identifier.tryParse(trigger.sound);
        if (soundId == null) {
            LOGGER.warn("Invalid Callout sound id: {}", trigger.sound);
            return;
        }

        SoundEvent sound = BuiltInRegistries.SOUND_EVENT.getValue(soundId);
        if (sound == null) {
            LOGGER.warn("Unknown Callout sound id: {}", soundId);
            return;
        }

        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(sound, trigger.pitch, trigger.volume)
        );
    }
}

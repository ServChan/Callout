package org.lts.callout;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
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

    private static final Pattern SENDER_CHAT_PATTERN = Pattern.compile("(?:^|.*?[\\s\\[\\]<>👤])([a-zA-Z0-9_]{3,16})\\s*[:»>|-]+\\s*(.*)");

    @Override
    public void onInitializeClient() {
        CalloutConfig.load();
        CalloutHistory.load();
        historyKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.callout.ping_history",
                InputConstants.Type.KEYSYM,
                InputConstants.KEY_K,
                CATEGORY
        ));

        ClientReceiveMessageEvents.CHAT.register((message, playerChatMessage, sender, boundChatType, timeStamp) -> {
            handleMessage(message, playerMessageText(playerChatMessage, message, sender), sender);
        });
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> handleMessage(message, message.getString(), null));
        ClientTickEvents.END_CLIENT_TICK.register(this::handleClientTick);
    }

    private void handleClientTick(Minecraft minecraft) {
        boolean isInWorld = minecraft.level != null && minecraft.player != null;
        if (isInWorld) {
            String scope = currentScope(minecraft);
            CalloutHistory.setCurrentScope(scope);
            CalloutConfig config = CalloutConfig.loadIfChanged();
            
            if (!wasInWorld) {
                if (!lastScope.isBlank() && !lastScope.equals(scope) && config.clearHistoryOnScopeChange) {
                    CalloutHistory.clear();
                } else {
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
            }
            lastScope = scope;
        }
        if (wasInWorld && !isInWorld) {
            CalloutHistory.saveSessionBuffer(lastScope);
            CalloutHistory.save();
        }
        wasInWorld = isInWorld;

        while (historyKey.consumeClick()) {
            if (isInWorld && isControlDown(minecraft) && !(minecraft.screen instanceof CalloutHistoryScreen)) {
                minecraft.setScreen(new CalloutHistoryScreen(minecraft.screen));
            }
        }
    }

    private static boolean isControlDown(Minecraft minecraft) {
        return InputConstants.isKeyDown(minecraft.getWindow(), InputConstants.KEY_LCONTROL)
                || InputConstants.isKeyDown(minecraft.getWindow(), InputConstants.KEY_RCONTROL);
    }

    private static String currentScope(Minecraft minecraft) {
        ServerData serverData = minecraft.getCurrentServer();
        if (serverData != null) {
            if (serverData.name != null && !serverData.name.isBlank()) {
                return serverData.name;
            }
            if (serverData.ip != null && !serverData.ip.isBlank()) {
                return serverData.ip;
            }
        }

        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server != null && server.getWorldData() != null) {
            String levelName = server.getWorldData().getLevelName();
            if (levelName != null && !levelName.isBlank()) {
                return levelName;
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
        String cleanText = fullText.replaceAll("\\[?[A-Za-z0-9_]{3,16}\\s+head\\]", "").replaceAll("head\\]", "").trim();

        String resolvedSender = senderProfile != null ? senderProfile.name() : null;
        String bodyText = rawMatchText != null && !rawMatchText.isBlank() ? rawMatchText : cleanText;

        if (resolvedSender == null || resolvedSender.isBlank()) {
            Matcher matcher = SENDER_CHAT_PATTERN.matcher(cleanText);
            if (matcher.find()) {
                resolvedSender = matcher.group(1);
                bodyText = matcher.group(2);
            }
        } else {
            String name = resolvedSender.toLowerCase(Locale.ROOT);
            String trimmed = cleanText.trim();
            if (trimmed.startsWith("<")) {
                int closeIdx = trimmed.indexOf('>');
                if (closeIdx > 0 && trimmed.substring(0, closeIdx).toLowerCase(Locale.ROOT).contains(name)) {
                    bodyText = trimmed.substring(closeIdx + 1).trim();
                }
            } else if (trimmed.toLowerCase(Locale.ROOT).contains(name + ":")) {
                int idx = trimmed.toLowerCase(Locale.ROOT).indexOf(name + ":");
                bodyText = trimmed.substring(idx + name.length() + 1).trim();
            }
        }

        boolean isOwn = false;
        if (minecraft.player != null) {
            String ownName = minecraft.player.getGameProfile().name();
            if (senderProfile != null && Objects.equals(minecraft.player.getGameProfile().id(), senderProfile.id())) {
                isOwn = true;
            } else if (resolvedSender != null && resolvedSender.equalsIgnoreCase(ownName)) {
                isOwn = true;
            }
        }

        return new ParsedMessage(resolvedSender, bodyText, isOwn);
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
            return Pattern.compile(trigger.word, flags).matcher(message).find();
        } catch (PatternSyntaxException exception) {
            LOGGER.warn("Invalid Callout regex: {}", trigger.word, exception);
            return false;
        }
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

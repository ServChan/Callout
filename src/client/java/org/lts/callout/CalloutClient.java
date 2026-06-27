package org.lts.callout;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
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
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class CalloutClient implements ClientModInitializer {
    public static final String MOD_ID = "callout";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
    private static KeyMapping historyKey;
    private boolean wasInWorld = false;

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
        if (wasInWorld && !isInWorld) {
            CalloutHistory.save();
            CalloutHistory.resetSessionBuffer();
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

    private static String playerMessageText(PlayerChatMessage playerChatMessage, Component fallbackMessage, GameProfile sender) {
        if (playerChatMessage != null) {
            return playerChatMessage.signedContent();
        }
        if (fallbackMessage == null) {
            return "";
        }
        String text = fallbackMessage.getString();
        if (sender != null && sender.name() != null && !sender.name().isBlank()) {
            String name = sender.name().toLowerCase(Locale.ROOT);
            String trimmed = text.trim();
            if (trimmed.startsWith("<")) {
                int closeIdx = trimmed.indexOf('>');
                if (closeIdx > 0 && trimmed.substring(0, closeIdx).toLowerCase(Locale.ROOT).contains(name)) {
                    return trimmed.substring(closeIdx + 1).trim();
                }
            }
            if (trimmed.startsWith("[")) {
                int closeIdx = trimmed.indexOf(']');
                if (closeIdx > 0 && trimmed.substring(0, closeIdx).toLowerCase(Locale.ROOT).contains(name)) {
                    return trimmed.substring(closeIdx + 1).trim();
                }
            }
            if (trimmed.toLowerCase(Locale.ROOT).startsWith(name + ":")) {
                return trimmed.substring(name.length() + 1).trim();
            }
        }
        return text;
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

        boolean ownMessage = sender != null && Objects.equals(minecraft.player.getGameProfile().id(), sender.id());

        for (CalloutConfig.Trigger trigger : config.allTriggers(minecraft.player.getGameProfile().name())) {
            if (matches(trigger, matchText, config.caseSensitive)) {
                if (ownMessage && !config.pingOwnMessages) {
                    showSelfTestHintOnce(config, trigger, matchText, sender);
                    return;
                }
                CalloutHistory.queuePing(senderName(sender), matchText, trigger);
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

    private static String senderName(GameProfile sender) {
        return sender == null ? null : sender.name();
    }

    private static void showSelfTestHintOnce(CalloutConfig config, CalloutConfig.Trigger trigger, String matchText, GameProfile sender) {
        if (config.selfTestHintShown) {
            return;
        }

        config.selfTestHintShown = true;
        CalloutConfig.save(config);
        CalloutHistory.queuePing(senderName(sender), matchText, trigger);
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

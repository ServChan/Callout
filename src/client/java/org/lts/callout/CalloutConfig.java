package org.lts.callout;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class CalloutConfig {
    public static final int MAX_CHAT_HISTORY = 16384;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("callout.json");

    private static CalloutConfig instance = defaults();
    private static long lastModified = -1L;
    private static long lastCheckTime = 0L;

    public boolean enabled = true;
    public boolean caseSensitive = false;
    public boolean pingOwnMessages = false;
    public boolean selfTestHintShown = false;
    public int maxPingHistory = 100;
    public int contextBefore = 5;
    public int contextAfter = 5;
    public boolean persistHistory = true;
    public boolean clearHistoryOnScopeChange = false;
    public Trigger nickname = new Trigger("", "minecraft:block.note_block.pling", 1.0F, 1.0F);
    public List<Trigger> triggers = new ArrayList<>();

    public static CalloutConfig load() {
        ensureConfigExists();

        try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
            CalloutConfig loaded = GSON.fromJson(reader, CalloutConfig.class);
            instance = sanitize(loaded == null ? defaults() : loaded);
            lastModified = Files.getLastModifiedTime(CONFIG_PATH).toMillis();
        } catch (IOException | RuntimeException exception) {
            CalloutClient.LOGGER.warn("Failed to load {}, using defaults", CONFIG_PATH, exception);
            instance = defaults();
        }

        return instance;
    }

    public static CalloutConfig loadIfChanged() {
        long now = System.currentTimeMillis();
        if (now - lastCheckTime < 1000) {
            return instance;
        }
        lastCheckTime = now;

        try {
            long modified = Files.exists(CONFIG_PATH) ? Files.getLastModifiedTime(CONFIG_PATH).toMillis() : -1L;
            if (modified != lastModified) {
                return load();
            }
        } catch (IOException exception) {
            CalloutClient.LOGGER.warn("Failed to check {}", CONFIG_PATH, exception);
        }

        return instance;
    }

    public static CalloutConfig currentCopy() {
        return instance.copy();
    }

    public static void save(CalloutConfig config) {
        CalloutConfig sanitized = sanitize(config);

        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(sanitized, writer);
            }
            instance = sanitized;
            lastModified = Files.getLastModifiedTime(CONFIG_PATH).toMillis();
        } catch (IOException exception) {
            CalloutClient.LOGGER.warn("Failed to save {}", CONFIG_PATH, exception);
        }
    }

    public List<Trigger> allTriggers(String playerName) {
        List<Trigger> all = new ArrayList<>();

        if (nickname != null) {
            Trigger ownNick = nickname.copy();
            if (ownNick.word == null || ownNick.word.isBlank()) {
                ownNick.word = playerName == null ? "" : playerName;
            }
            all.add(ownNick);
        }

        if (triggers != null) {
            all.addAll(triggers);
        }

        return all;
    }

    private static void ensureConfigExists() {
        if (Files.exists(CONFIG_PATH)) {
            return;
        }

        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(defaults(), writer);
            }
        } catch (IOException exception) {
            CalloutClient.LOGGER.warn("Failed to create {}", CONFIG_PATH, exception);
        }
    }

    private static CalloutConfig defaults() {
        CalloutConfig config = new CalloutConfig();
        config.triggers.add(new Trigger("admin", "minecraft:entity.experience_orb.pickup", 0.8F, 1.0F));
        config.triggers.add(new Trigger("help", "minecraft:block.note_block.bell", 1.0F, 1.0F));
        return config;
    }

    private CalloutConfig copy() {
        CalloutConfig copy = new CalloutConfig();
        copy.enabled = enabled;
        copy.caseSensitive = caseSensitive;
        copy.pingOwnMessages = pingOwnMessages;
        copy.selfTestHintShown = selfTestHintShown;
        copy.maxPingHistory = maxPingHistory;
        copy.contextBefore = contextBefore;
        copy.contextAfter = contextAfter;
        copy.persistHistory = persistHistory;
        copy.clearHistoryOnScopeChange = clearHistoryOnScopeChange;
        copy.nickname = nickname == null ? null : nickname.copy();
        copy.triggers = new ArrayList<>();
        if (triggers != null) {
            for (Trigger trigger : triggers) {
                copy.triggers.add(trigger.copy());
            }
        }
        return copy;
    }

    private static CalloutConfig sanitize(CalloutConfig config) {
        if (config.nickname == null) {
            config.nickname = new Trigger("", "minecraft:block.note_block.pling", 1.0F, 1.0F);
        }
        config.maxPingHistory = clamp(config.maxPingHistory, 1, 1000);
        config.contextBefore = clamp(config.contextBefore, 0, 20);
        config.contextAfter = clamp(config.contextAfter, 0, 20);
        config.nickname.sanitize();

        if (config.triggers == null) {
            config.triggers = new ArrayList<>();
        }
        config.triggers.forEach(Trigger::sanitize);

        return config;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static class Trigger {
        public String word;
        public String sound;
        public float volume;
        public float pitch;
        public boolean regex;

        public Trigger() {
        }

        public Trigger(String word, String sound, float volume, float pitch) {
            this(word, sound, volume, pitch, false);
        }

        public Trigger(String word, String sound, float volume, float pitch, boolean regex) {
            this.word = word;
            this.sound = sound;
            this.volume = volume;
            this.pitch = pitch;
            this.regex = regex;
        }

        public Trigger copy() {
            return new Trigger(word, sound, volume, pitch, regex);
        }

        private void sanitize() {
            if (word == null) {
                word = "";
            }
            if (sound == null || sound.isBlank()) {
                sound = "minecraft:block.note_block.pling";
            }
            volume = clamp(volume, 0.0F, 4.0F);
            pitch = clamp(pitch, 0.5F, 2.0F);
        }

        private static float clamp(float value, float min, float max) {
            if (Float.isNaN(value)) {
                return min;
            }
            return Math.max(min, Math.min(max, value));
        }
    }
}

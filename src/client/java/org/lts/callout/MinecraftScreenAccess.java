package org.lts.callout;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Reads the active screen across the API move from Minecraft to Minecraft.gui in 26.2.
 */
public final class MinecraftScreenAccess {
    private static final Access ACCESS = createAccess();

    private MinecraftScreenAccess() {
    }

    public static Screen getScreen(Minecraft minecraft) {
        return ACCESS.getScreen(minecraft);
    }

    /**
     * Closes the active screen across the API move from Minecraft to Minecraft.gui in 26.2.
     * Passing {@code null} also avoids restoring a stale Mod Menu/inventory parent screen.
     */
    public static void closeScreen(Minecraft minecraft) {
        try {
            try {
                minecraft.gui.getClass().getMethod("setScreen", Screen.class)
                        .invoke(minecraft.gui, new Object[]{null});
            } catch (NoSuchMethodException ignored) {
                minecraft.getClass().getMethod("setScreen", Screen.class)
                        .invoke(minecraft, new Object[]{null});
            }
        } catch (ReflectiveOperationException exception) {
            throw accessFailed(exception, "Could not close the active Minecraft screen");
        }
    }

    private static Access createAccess() {
        try {
            return new LegacyAccess(Minecraft.class.getField("screen"));
        } catch (NoSuchFieldException ignored) {
            try {
                Field guiField = Minecraft.class.getField("gui");
                return new GuiAccess(guiField, guiField.getType().getMethod("screen"));
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Unsupported Minecraft screen API", exception);
            }
        }
    }

    private static RuntimeException accessFailed(ReflectiveOperationException exception) {
        return accessFailed(exception, "Could not read the active Minecraft screen");
    }

    private static RuntimeException accessFailed(ReflectiveOperationException exception, String message) {
        Throwable cause = exception instanceof InvocationTargetException && exception.getCause() != null
                ? exception.getCause()
                : exception;
        return new IllegalStateException(message, cause);
    }

    private interface Access {
        Screen getScreen(Minecraft minecraft);
    }

    private record LegacyAccess(Field screenField) implements Access {
        @Override
        public Screen getScreen(Minecraft minecraft) {
            try {
                return (Screen) screenField.get(minecraft);
            } catch (ReflectiveOperationException exception) {
                throw accessFailed(exception);
            }
        }
    }

    private record GuiAccess(Field guiField, Method screenMethod) implements Access {
        @Override
        public Screen getScreen(Minecraft minecraft) {
            try {
                return (Screen) screenMethod.invoke(guiField.get(minecraft));
            } catch (ReflectiveOperationException exception) {
                throw accessFailed(exception);
            }
        }
    }
}

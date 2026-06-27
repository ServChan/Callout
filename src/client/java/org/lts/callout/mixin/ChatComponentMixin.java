package org.lts.callout.mixin;

import org.lts.callout.CalloutConfig;
import org.lts.callout.CalloutHistory;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public class ChatComponentMixin {
    @ModifyExpressionValue(
            method = {"addMessageToDisplayQueue", "addMessageToQueue", "addRecentChat"},
            at = @At(value = "CONSTANT", args = "intValue=100")
    )
    private int callout$increaseChatHistoryLimit(int original) {
        return CalloutConfig.MAX_CHAT_HISTORY;
    }

    @Inject(method = "addClientSystemMessage", at = @At("HEAD"))
    private void callout$observeClientSystemMessage(Component message, CallbackInfo ci) {
        CalloutHistory.observeDisplayed(message);
    }

    @Inject(method = "addServerSystemMessage", at = @At("HEAD"))
    private void callout$observeServerSystemMessage(Component message, CallbackInfo ci) {
        CalloutHistory.observeDisplayed(message);
    }

    @Inject(method = "addPlayerMessage", at = @At("HEAD"))
    private void callout$observePlayerMessage(Component message, MessageSignature signature, GuiMessageTag tag, CallbackInfo ci) {
        CalloutHistory.observeDisplayed(message);
    }
}

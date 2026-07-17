package org.lts.callout.mixin;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import org.lts.callout.WorldScopeTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Inject(method = "handleLogin", at = @At("HEAD"))
    private void callout$captureLoginWorld(ClientboundLoginPacket packet, CallbackInfo ci) {
        WorldScopeTracker.setSeed(packet.commonPlayerSpawnInfo().seed());
    }

    @Inject(method = "handleRespawn", at = @At("HEAD"))
    private void callout$captureRespawnWorld(ClientboundRespawnPacket packet, CallbackInfo ci) {
        WorldScopeTracker.setSeed(packet.commonPlayerSpawnInfo().seed());
    }
}

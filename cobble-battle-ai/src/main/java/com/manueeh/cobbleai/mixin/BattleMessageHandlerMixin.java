package com.manueeh.cobbleai.mixin;

import com.cobblemon.mod.common.client.net.battle.BattleMessageHandler;
import com.cobblemon.mod.common.net.messages.client.battle.BattleMessagePacket;
import com.manueeh.cobbleai.track.BattleTracker;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Taps the raw battle message components before Cobblemon flattens them into rendered lines,
 * so the tracker can read translation keys and arguments.
 * The descriptor uses the intermediary name of Minecraft (class_310) because Cobblemon's own
 * classes are not remapped by this mod's refmap.
 */
@Mixin(value = BattleMessageHandler.class, remap = false)
public abstract class BattleMessageHandlerMixin {
    @Inject(
        method = "handle(Lcom/cobblemon/mod/common/net/messages/client/battle/BattleMessagePacket;Lnet/minecraft/class_310;)V",
        at = @At("HEAD"),
        remap = false,
        require = 0
    )
    private void cobblebattleai$onMessages(BattleMessagePacket packet, Minecraft client, CallbackInfo ci) {
        try {
            BattleTracker.INSTANCE.onMessages(packet.getMessages());
        } catch (Throwable ignored) {
            // Never break Cobblemon's own handling.
        }
    }
}

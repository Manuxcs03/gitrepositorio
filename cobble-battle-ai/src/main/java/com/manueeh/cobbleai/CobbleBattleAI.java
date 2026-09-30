package com.manueeh.cobbleai;

import com.cobblemon.mod.common.client.gui.battle.BattleGUI;
import com.manueeh.cobbleai.ui.AiHud;
import com.manueeh.cobbleai.ui.CobbleKeys;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

public final class CobbleBattleAI implements ClientModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("CobbleBattleAI");

    @Override
    public void onInitializeClient() {
        AiConfig.get();
        CobbleKeys.register();
        com.manueeh.cobbleai.data.MovepoolIndex.preload();

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (CobbleKeys.MODE.consumeClick()) cycleMode(mc);
            while (CobbleKeys.EXECUTE.consumeClick()) BattleDriver.INSTANCE.requestExecute();
            BattleDriver.INSTANCE.tick(mc);
        });

        HudRenderCallback.EVENT.register((graphics, delta) -> {
            if (Minecraft.getInstance().screen == null) AiHud.render(graphics);
        });

        // Key bindings do not fire while a screen is open, so listen on the battle screen directly.
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof BattleGUI)) return;
            ScreenKeyboardEvents.afterKeyPress(screen).register((s, key, scancode, mods) -> {
                if (CobbleKeys.MODE.matches(key, scancode)) cycleMode(client);
                else if (CobbleKeys.EXECUTE.matches(key, scancode)) BattleDriver.INSTANCE.requestExecute();
            });
            ScreenEvents.afterRender(screen).register((s, graphics, mx, my, delta) -> AiHud.render(graphics));
        });
        LOG.info("Cobble Battle AI ready (mode {})", AiConfig.get().mode);
    }

    private static void cycleMode(Minecraft mc) {
        AiConfig cfg = AiConfig.get();
        cfg.mode = switch (cfg.mode) {
            case OFF -> AiConfig.Mode.SUGGEST;
            case SUGGEST -> AiConfig.Mode.AUTO;
            case AUTO -> AiConfig.Mode.OFF;
        };
        cfg.save();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.translatable("cobblebattleai.mode_changed",
                Component.translatable("cobblebattleai.mode." + cfg.mode.name().toLowerCase(Locale.ROOT))), true);
        }
    }
}

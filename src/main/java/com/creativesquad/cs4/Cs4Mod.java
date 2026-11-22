package com.creativesquad.cs4;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

public class Cs4Mod implements ModInitializer {
    public static final String MOD_ID = "cs4";

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, ctx, env) ->
            dispatcher.register(Commands.literal("relics")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(c -> {
                ServerPlayer p = c.getSource().getPlayerOrException();
                RelicsGui.openRelics(p);
                return 1;
            })));

        UseItemCallback.EVENT.register(Abilities::onUse);
        ServerTickEvents.END_SERVER_TICK.register(Abilities::tick);
        ServerLivingEntityEvents.AFTER_DAMAGE.register(Abilities::afterDamage);
        ServerLivingEntityEvents.ALLOW_DEATH.register(Abilities::allowDeath);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
            Abilities.cleanup(handler.getPlayer().getUUID()));
    }
}

package br.com.creativesquad.cs4;

import br.com.creativesquad.cs4.relic.CompassTracking;
import br.com.creativesquad.cs4.relic.Disguise;
import br.com.creativesquad.cs4.relic.RelicAbilities;
import br.com.creativesquad.cs4.relic.RelicEffects;
import br.com.creativesquad.cs4.relic.RelicRegistry;
import br.com.creativesquad.cs4.relic.RelicsCommand;
import br.com.creativesquad.cs4.relic.TwinHelmet;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Creative Squad 4 (CS4): relíquias lendárias feitas com itens do próprio Minecraft. */
public class Cs4 implements ModInitializer {
    public static final String MOD_ID = "cs4";
    public static final Logger LOGGER = LoggerFactory.getLogger("CS4");

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, env) -> RelicsCommand.register(dispatcher));
        ServerLifecycleEvents.SERVER_STARTED.register(server -> RelicRegistry.load(server.registryAccess()));
        RelicAbilities.init();
        RelicEffects.init();
        TwinHelmet.init();
        CompassTracking.init();
        Disguise.init();
        LOGGER.info("Creative Squad 4 carregado.");
    }
}

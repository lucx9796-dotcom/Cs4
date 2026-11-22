package br.com.creativesquad.cs4.relic;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public final class RelicsCommand {
    private RelicsCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("relics")
                // Só operadores (nível 2). Para liberar a todos, remova a linha abaixo.
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(ctx -> {
                    RelicsMenu.open(ctx.getSource().getPlayerOrException());
                    return 1;
                }));
    }
}

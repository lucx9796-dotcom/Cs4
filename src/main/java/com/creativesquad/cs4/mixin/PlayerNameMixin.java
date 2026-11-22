package com.creativesquad.cs4.mixin;

import com.creativesquad.cs4.Disguises;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Nome no chat, mensagens de morte, etc. */
@Mixin(Player.class)
public abstract class PlayerNameMixin {
    @Inject(method = "getName", at = @At("HEAD"), cancellable = true, require = 0)
    private void cs4$fakeName(CallbackInfoReturnable<Component> cir) {
        Player self = (Player) (Object) this;
        if (self instanceof ServerPlayer) {
            String n = Disguises.fakeName(self.getUUID());
            if (n != null) cir.setReturnValue(Component.literal(n));
        }
    }
}

package com.creativesquad.cs4.mixin;

import com.creativesquad.cs4.Disguises;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** /tell NomeFalso ... passa a achar o jogador disfarcado. */
@Mixin(PlayerList.class)
public abstract class PlayerListMixin {
    @Inject(method = "getPlayerByName", at = @At("RETURN"), cancellable = true, require = 0)
    private void cs4$byFakeName(String name, CallbackInfoReturnable<ServerPlayer> cir) {
        if (cir.getReturnValue() != null) return;
        for (ServerPlayer p : ((PlayerList) (Object) this).getPlayers()) {
            String fake = Disguises.fakeName(p.getUUID());
            if (fake != null && fake.equalsIgnoreCase(name)) { cir.setReturnValue(p); return; }
        }
    }
}

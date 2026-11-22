package com.creativesquad.cs4.mixin;

import com.creativesquad.cs4.Disguises;
import com.mojang.authlib.GameProfile;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Todo pacote de PlayerInfo montado a partir de um ServerPlayer usa o perfil falso (se houver). */
@Mixin(ClientboundPlayerInfoUpdatePacket.Entry.class)
public abstract class PlayerInfoEntryMixin {
    @Redirect(
        method = "<init>(Lnet/minecraft/server/level/ServerPlayer;)V",
        at = @At(value = "INVOKE",
                 target = "Lnet/minecraft/server/level/ServerPlayer;getGameProfile()Lcom/mojang/authlib/GameProfile;"),
        require = 0)
    private static GameProfile cs4$fakeProfile(ServerPlayer player) {
        GameProfile fake = Disguises.fakeProfile(player.getUUID());
        return fake != null ? fake : player.getGameProfile();
    }
}

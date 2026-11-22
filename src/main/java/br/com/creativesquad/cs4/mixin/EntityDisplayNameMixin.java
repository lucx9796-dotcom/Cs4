package br.com.creativesquad.cs4.mixin;

import br.com.creativesquad.cs4.relic.Disguise;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Relógio Lendário: o servidor usa Entity.getDisplayName() no chat, no /tell, nas mensagens de morte etc.
 * Quem está disfarçado aparece com o nome do alvo.
 */
@Mixin(Entity.class)
public abstract class EntityDisplayNameMixin {

    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true, require = 0)
    private void cs4$nomeDisfarcado(CallbackInfoReturnable<Component> cir) {
        if ((Object) this instanceof ServerPlayer p) {
            String fake = Disguise.nameOf(p.getUUID());
            if (fake != null) cir.setReturnValue(Component.literal(fake));
        }
    }
}

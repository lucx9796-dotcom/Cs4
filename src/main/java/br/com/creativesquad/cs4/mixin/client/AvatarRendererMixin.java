package br.com.creativesquad.cs4.mixin.client;

import br.com.creativesquad.cs4.client.ArmorHider;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lampião Lendário: esconde a armadura e os itens das mãos do jogador na visão de terceira pessoa
 * (só elytra, cabeças de mob/jogador e abóbora continuam visíveis). Na primeira pessoa tudo fica normal.
 */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {

    @Inject(method = "extractRenderState", at = @At("TAIL"), require = 0)
    private void cs4$esconderArmadura(Avatar player, AvatarRenderState state, float partialTick, CallbackInfo ci) {
        if (ArmorHider.isCloaked(player)) {
            ArmorHider.hide(state);
        }
    }
}

package com.creativesquad.cs4.mixin;

import com.creativesquad.cs4.Relics;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applies the extra dragon-head glint only to CS4's Dragao Lendario relic. */
@Mixin(ItemStack.class)
public abstract class DragonHeadGlintMixin {

    @Inject(method = "hasFoil", at = @At("RETURN"), cancellable = true)
    private void cs4$legendaryDragonHeadGlints(CallbackInfoReturnable<Boolean> cir) {
        ItemStack stack = (ItemStack) (Object) this;
        if (stack.is(net.minecraft.world.item.Items.DRAGON_HEAD)
                && Relics.idOf(stack).equals("dragon_head")) {
            cir.setReturnValue(true);
        }
    }
}

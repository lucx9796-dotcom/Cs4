package br.com.creativesquad.cs4.mixin;

import br.com.creativesquad.cs4.client.ArmorHider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lampião Lendário: no cliente a armadura de quem o segura "some" para o renderizador (só vale no lado do cliente). */
@Mixin(LivingEntity.class)
public abstract class LivingEntityEquipMixin {

    @Inject(method = "getItemBySlot", at = @At("RETURN"), cancellable = true, require = 0)
    private void cs4$esconderArmadura(EquipmentSlot slot, CallbackInfoReturnable<ItemStack> cir) {
        ItemStack original = cir.getReturnValue();
        ItemStack result = ArmorHider.filter((LivingEntity) (Object) this, slot, original);
        if (result != original) cir.setReturnValue(result);
    }
}

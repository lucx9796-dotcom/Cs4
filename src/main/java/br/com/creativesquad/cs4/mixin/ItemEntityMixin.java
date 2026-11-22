package br.com.creativesquad.cs4.mixin;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Indestrutível: item com a tag unbreakable={} jogado no chão não toma NENHUM dano (explosão, fogo, lava, cacto,
 * raio, dano genérico...), não some por tempo (despawn) e, se cair no void, fica parado no fundo do mundo em vez de
 * ser apagado.
 */
@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {

    @Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
    private void cs4$indestrutivel(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (((ItemEntity) (Object) this).getItem().has(DataComponents.UNBREAKABLE)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void cs4$salvarDoVoid(CallbackInfo ci) {
        ItemEntity self = (ItemEntity) (Object) this;
        if (!self.getItem().has(DataComponents.UNBREAKABLE)) return;
        double floor = self.level().getMinY() - 8.0;
        if (self.getY() < floor) {
            self.setNoGravity(true);
            self.setDeltaMovement(Vec3.ZERO);
            self.setPos(self.getX(), floor, self.getZ());
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void cs4$semDespawn(CallbackInfo ci) {
        ItemEntity self = (ItemEntity) (Object) this;
        if (self.getItem().has(DataComponents.UNBREAKABLE)) {
            self.setUnlimitedLifetime();
        }
    }
}

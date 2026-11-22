package com.creativesquad.cs4.mixin;

import com.creativesquad.cs4.Relics;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Item com a tag unbreakable nunca e destruido: explosao, cacto, fogo, lava, despawn, void... */
@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {

    @Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
    private void cs4$immune(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        ItemEntity self = (ItemEntity) (Object) this;
        if (Relics.isIndestructible(self.getItem())) cir.setReturnValue(false);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void cs4$keepAlive(CallbackInfo ci) {
        ItemEntity self = (ItemEntity) (Object) this;
        if (!Relics.isIndestructible(self.getItem())) return;

        self.setUnlimitedLifetime();   // nunca despawna
        self.setInvulnerable(true);

        // void: sobe o item e o deixa flutuando em vez de ser removido
        if (!self.level().isClientSide() && self.getY() < self.level().getMinY() - 32) {
            self.setPos(self.getX(), self.level().getMinY() + 1, self.getZ());
            self.setDeltaMovement(Vec3.ZERO);
            self.setNoGravity(true);
        }
    }
}

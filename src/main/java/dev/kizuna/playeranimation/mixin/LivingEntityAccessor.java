package dev.kizuna.playeranimation.mixin;

import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** 服务器调度读取原版挥手时长，避免复制 LivingEntity 的 haste 规则。 */
@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
    @Invoker("getHandSwingDuration")
    int kpa$getHandSwingDuration();
}

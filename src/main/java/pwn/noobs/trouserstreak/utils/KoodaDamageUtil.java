package pwn.noobs.trouserstreak.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.RaycastContext;

import java.util.Optional;

public class KoodaDamageUtil {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public static float getExplosionDamage(Vec3d explosionPos, float power) {
        if (mc.world == null || mc.player == null) return 0f;
        if (mc.world.getDifficulty() == Difficulty.PEACEFUL) return 0f;

        double distance = Math.sqrt(mc.player.squaredDistanceTo(explosionPos));
        if (distance > power * 2.0) return 0f;

        double exposure = calculateExposure(explosionPos, mc.player);
        double impact = (1.0 - (distance / (power * 2.0))) * exposure;

        if (impact < 0.0) return 0f;

        float damage = (float) ((impact * impact + impact) / 2.0 * 7.0 * (double) power + 1.0);

        damage = getDifficultyMultiplier(damage);

        return calculateReductions(damage, mc.player);
    }

    private static double calculateExposure(Vec3d source, PlayerEntity entity) {
        RaycastContext context = new RaycastContext(
                source,
                entity.getEyePos(),
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                entity
        );
        return mc.world.raycast(context).getType() == HitResult.Type.MISS ? 1.0 : 0.0;
    }

    private static float getDifficultyMultiplier(float damage) {
        switch (mc.world.getDifficulty()) {
            case PEACEFUL: return 0f;
            case EASY: return Math.min(damage / 2f + 1f, damage);
            case HARD: return damage * 1.5f;
            default: return damage;
        }
    }

    private static float calculateReductions(float damage, PlayerEntity player) {
        if (player.isCreative()) return 0f;

        if (player.hasStatusEffect(StatusEffects.RESISTANCE)) {
            int amplifier = (player.getStatusEffect(StatusEffects.RESISTANCE).getAmplifier() + 1) * 5;
            damage = Math.max(damage * (1.0f - amplifier / 25.0f), 0f);
        }

        if (damage <= 0) return 0f;

        float armor = player.getArmor();
        float toughness = (float) player.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS);

        float f = 2.0f + toughness / 4.0f;
        float g = MathHelper.clamp(armor - damage / f, armor * 0.2f, 20.0f);
        damage = damage * (1.0f - g / 25.0f);

        if (damage > 0) {
            int protectionLevel = 0;

            try {
                EquipmentSlot[] armorSlots = {
                        EquipmentSlot.HEAD,
                        EquipmentSlot.CHEST,
                        EquipmentSlot.LEGS,
                        EquipmentSlot.FEET
                };

                for (EquipmentSlot slot : armorSlots) {
                    ItemStack stack = player.getEquippedStack(slot);
                    if (stack.isEmpty()) continue;

                    int blastProt = getEnchantmentLevel(Enchantments.BLAST_PROTECTION, stack);
                    int prot = getEnchantmentLevel(Enchantments.PROTECTION, stack);

                    if (blastProt > 0) {
                        protectionLevel += blastProt * 2;
                    } else if (prot > 0) {
                        protectionLevel += prot;
                    }
                }
            } catch (Exception ignored) {
                protectionLevel = 10;
            }

            if (protectionLevel > 20) protectionLevel = 20;
            damage *= (1.0f - (protectionLevel / 25.0f));
        }

        return Math.max(damage, 0f);
    }

    private static int getEnchantmentLevel(RegistryEntry<Enchantment> enchant, ItemStack stack) {
        return EnchantmentHelper.getLevel(enchant, stack);
    }

    private static int getEnchantmentLevel(net.minecraft.registry.RegistryKey<Enchantment> key, ItemStack stack) {
        if (mc.world == null) return 0;

        Optional<RegistryEntry.Reference<Enchantment>> entry = mc.world.getRegistryManager()
                .getOrThrow(RegistryKeys.ENCHANTMENT)
                .getOptional(key);

        return entry.map(enchantmentReference -> EnchantmentHelper.getLevel(enchantmentReference, stack)).orElse(0);
    }
}
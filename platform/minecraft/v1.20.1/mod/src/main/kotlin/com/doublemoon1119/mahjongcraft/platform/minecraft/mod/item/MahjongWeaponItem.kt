package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.item

import com.google.common.collect.ImmutableMultimap
import com.google.common.collect.Multimap
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.ai.attributes.Attribute
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.item.Item

/**
 * 拿在主手時可以當武器的物品：攻擊力與攻擊速度以原版武器的方式加在玩家身上，各版本的原版屬性寫法由這個類別處理。
 *
 * @param properties 原版物品設定。
 * @param attackDamageBonus 攻擊力加成；玩家基礎攻擊力為 1。
 * @param attackSpeedBonus 攻擊速度加成；玩家基礎攻擊速度為 4。
 */
abstract class MahjongWeaponItem(properties: Properties, attackDamageBonus: Double, attackSpeedBonus: Double) : Item(properties) {
    /** 主手持有時套用的攻擊力與攻擊速度。 */
    private val mainHandModifiers: Multimap<Attribute, AttributeModifier> = ImmutableMultimap.of(
        Attributes.ATTACK_DAMAGE,
        AttributeModifier(BASE_ATTACK_DAMAGE_UUID, WEAPON_MODIFIER_NAME, attackDamageBonus, AttributeModifier.Operation.ADDITION),
        Attributes.ATTACK_SPEED,
        AttributeModifier(BASE_ATTACK_SPEED_UUID, WEAPON_MODIFIER_NAME, attackSpeedBonus, AttributeModifier.Operation.ADDITION),
    )

    @Suppress("OVERRIDE_DEPRECATION")
    final override fun getDefaultAttributeModifiers(slot: EquipmentSlot): Multimap<Attribute, AttributeModifier> = if (slot == EquipmentSlot.MAINHAND) mainHandModifiers else super.getDefaultAttributeModifiers(slot)

    private companion object {
        /** 屬性修飾的名稱，比照原版武器。 */
        const val WEAPON_MODIFIER_NAME: String = "Weapon modifier"
    }
}

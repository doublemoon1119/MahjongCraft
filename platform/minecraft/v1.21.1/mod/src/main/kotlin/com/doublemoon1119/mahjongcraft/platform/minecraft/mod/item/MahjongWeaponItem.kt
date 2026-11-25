package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.item

import net.minecraft.world.entity.EquipmentSlotGroup
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.item.Item
import net.minecraft.world.item.component.ItemAttributeModifiers

/**
 * 拿在主手時可以當武器的物品：攻擊力與攻擊速度以原版武器的方式加在玩家身上，各版本的原版屬性寫法由這個類別處理。
 *
 * @param properties 原版物品設定。
 * @param attackDamageBonus 攻擊力加成；玩家基礎攻擊力為 1。
 * @param attackSpeedBonus 攻擊速度加成；玩家基礎攻擊速度為 4。
 */
abstract class MahjongWeaponItem(properties: Properties, attackDamageBonus: Double, attackSpeedBonus: Double) : Item(properties.attributes(mainHandModifiers(attackDamageBonus, attackSpeedBonus))) {
    private companion object {
        /**
         * 主手持有時套用的攻擊力與攻擊速度。
         *
         * @param attackDamageBonus 攻擊力加成。
         * @param attackSpeedBonus 攻擊速度加成。
         * @return 物品的屬性修飾。
         */
        fun mainHandModifiers(attackDamageBonus: Double, attackSpeedBonus: Double): ItemAttributeModifiers = ItemAttributeModifiers.builder()
            .add(
                Attributes.ATTACK_DAMAGE,
                AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, attackDamageBonus, AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.MAINHAND,
            )
            .add(
                Attributes.ATTACK_SPEED,
                AttributeModifier(Item.BASE_ATTACK_SPEED_ID, attackSpeedBonus, AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.MAINHAND,
            )
            .build()
    }
}

package com.doublemoon1119.mahjongcraft.platform.fabric.item

import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongRulerEntity
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.registry.ModSounds
import com.doublemoon1119.mahjongcraft.platform.minecraft.ruler.MahjongRulerColor
import com.google.common.collect.ImmutableMultimap
import com.google.common.collect.Multimap
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.ai.attributes.Attribute
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.item.Item
import net.minecraft.world.item.context.UseOnContext

/**
 * 牌尺 item：每種顏色一個物品。蹲下對方塊右鍵時在命中點放置同色的 [MahjongRulerEntity]；拿在主手時可以當武器，
 * 攻擊力與攻擊速度和木劍相同，沒有耐久度。
 *
 * @property color 這個物品的牌尺顏色。
 * @param settings 原版物品設定。
 */
class MahjongRulerItem(
    val color: MahjongRulerColor,
    settings: Properties,
) : Item(settings) {
    /** 主手持有時套用的攻擊力與攻擊速度。 */
    private val mainHandModifiers: Multimap<Attribute, AttributeModifier> = ImmutableMultimap.of(
        Attributes.ATTACK_DAMAGE,
        AttributeModifier(BASE_ATTACK_DAMAGE_UUID, WEAPON_MODIFIER_NAME, ATTACK_DAMAGE_BONUS, AttributeModifier.Operation.ADDITION),
        Attributes.ATTACK_SPEED,
        AttributeModifier(BASE_ATTACK_SPEED_UUID, WEAPON_MODIFIER_NAME, ATTACK_SPEED_BONUS, AttributeModifier.Operation.ADDITION),
    )

    /**
     * 蹲下時在命中點放置面向玩家的牌尺；沒有蹲下時不做任何事。
     *
     * @param context 方塊互動位置、玩家及物品堆疊。
     */
    override fun useOn(context: UseOnContext): InteractionResult {
        val player = context.player ?: return InteractionResult.PASS
        if (!player.isShiftKeyDown) return InteractionResult.PASS
        if (context.level.isClientSide) return InteractionResult.SUCCESS

        val world = context.level as ServerLevel
        val entity = MahjongRulerEntity(world = world).apply {
            color = this@MahjongRulerItem.color
            val hitPos = context.clickLocation
            moveTo(hitPos.x, hitPos.y, hitPos.z, context.rotation + HALF_TURN_DEGREES, 0.0f)
        }
        val intersectsBlock = world.getBlockCollisions(entity, entity.boundingBox).iterator().hasNext()
        if (intersectsBlock || !world.addFreshEntity(entity)) return InteractionResult.FAIL

        if (!player.abilities.instabuild) context.itemInHand.shrink(1)
        entity.playSound(ModSounds.rulerPlace, 1.0f, 1.0f)
        return InteractionResult.CONSUME
    }

    /**
     * 主手持有時提供木劍的攻擊力與攻擊速度。
     *
     * @param slot 物品所在的裝備欄位。
     * @return 該欄位套用的屬性修飾。
     */
    override fun getDefaultAttributeModifiers(slot: EquipmentSlot): Multimap<Attribute, AttributeModifier> = if (slot == EquipmentSlot.MAINHAND) mainHandModifiers else super.getDefaultAttributeModifiers(slot)

    private companion object {
        /** 屬性修飾的名稱，比照原版武器。 */
        const val WEAPON_MODIFIER_NAME: String = "Weapon modifier"

        /** 攻擊力加成；玩家基礎攻擊力 1，合計 4，與木劍相同。 */
        const val ATTACK_DAMAGE_BONUS: Double = 3.0

        /** 攻擊速度加成；玩家基礎攻擊速度 4，合計 1.6，與木劍相同。 */
        const val ATTACK_SPEED_BONUS: Double = -2.4

        /** 半圈的角度，讓牌尺面向放置的玩家。 */
        const val HALF_TURN_DEGREES: Float = 180.0f
    }
}

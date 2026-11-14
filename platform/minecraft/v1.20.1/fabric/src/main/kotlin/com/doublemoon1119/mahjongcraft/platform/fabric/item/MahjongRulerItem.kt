package com.doublemoon1119.mahjongcraft.platform.fabric.item

import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongRulerEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModSounds
import com.doublemoon1119.mahjongcraft.platform.minecraft.ruler.MahjongRulerColor
import com.google.common.collect.ImmutableMultimap
import com.google.common.collect.Multimap
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.attribute.EntityAttribute
import net.minecraft.entity.attribute.EntityAttributeModifier
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.item.Item
import net.minecraft.item.ItemUsageContext
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.ActionResult

/**
 * 牌尺 item：每種顏色一個物品。蹲下對方塊右鍵時在命中點放置同色的 [MahjongRulerEntity]；拿在主手時可以當武器，
 * 攻擊力與攻擊速度和木劍相同，沒有耐久度。
 *
 * @property color 這個物品的牌尺顏色。
 * @param settings 原版物品設定。
 */
class MahjongRulerItem(
    val color: MahjongRulerColor,
    settings: Settings,
) : Item(settings) {
    /** 主手持有時套用的攻擊力與攻擊速度。 */
    private val mainHandModifiers: Multimap<EntityAttribute, EntityAttributeModifier> = ImmutableMultimap.of(
        EntityAttributes.GENERIC_ATTACK_DAMAGE,
        EntityAttributeModifier(ATTACK_DAMAGE_MODIFIER_ID, WEAPON_MODIFIER_NAME, ATTACK_DAMAGE_BONUS, EntityAttributeModifier.Operation.ADDITION),
        EntityAttributes.GENERIC_ATTACK_SPEED,
        EntityAttributeModifier(ATTACK_SPEED_MODIFIER_ID, WEAPON_MODIFIER_NAME, ATTACK_SPEED_BONUS, EntityAttributeModifier.Operation.ADDITION),
    )

    /**
     * 蹲下時在命中點放置面向玩家的牌尺；沒有蹲下時不做任何事。
     *
     * @param context 方塊互動位置、玩家及物品堆疊。
     */
    override fun useOnBlock(context: ItemUsageContext): ActionResult {
        val player = context.player ?: return ActionResult.PASS
        if (!player.isSneaking) return ActionResult.PASS
        if (context.world.isClient) return ActionResult.SUCCESS

        val world = context.world as ServerWorld
        val entity = MahjongRulerEntity(world = world).apply {
            color = this@MahjongRulerItem.color
            val hitPos = context.hitPos
            refreshPositionAndAngles(hitPos.x, hitPos.y, hitPos.z, context.playerYaw + HALF_TURN_DEGREES, 0.0f)
        }
        val intersectsBlock = world.getBlockCollisions(entity, entity.boundingBox).iterator().hasNext()
        if (intersectsBlock || !world.spawnEntity(entity)) return ActionResult.FAIL

        if (!player.abilities.creativeMode) context.stack.decrement(1)
        entity.playSound(ModSounds.rulerPlace, 1.0f, 1.0f)
        return ActionResult.CONSUME
    }

    /**
     * 主手持有時提供木劍的攻擊力與攻擊速度。
     *
     * @param slot 物品所在的裝備欄位。
     * @return 該欄位套用的屬性修飾。
     */
    override fun getAttributeModifiers(slot: EquipmentSlot): Multimap<EntityAttribute, EntityAttributeModifier> = if (slot == EquipmentSlot.MAINHAND) mainHandModifiers else super.getAttributeModifiers(slot)

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

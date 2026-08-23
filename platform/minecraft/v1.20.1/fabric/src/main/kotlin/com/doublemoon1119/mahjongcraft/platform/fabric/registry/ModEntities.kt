package com.doublemoon1119.mahjongcraft.platform.fabric.registry

import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongDiceEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongRoundInfoEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongScoringStickEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTileEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.WinCelebrationEffectEntity
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import net.fabricmc.fabric.api.`object`.builder.v1.entity.FabricEntityTypeBuilder
import net.minecraft.entity.EntityDimensions
import net.minecraft.entity.EntityType
import net.minecraft.entity.SpawnGroup
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.util.Identifier

/** MahjongCraft Fabric entity type 的集中註冊點。 */
object ModEntities {
    /** 麻將骰子 entity type；由 [register] 初始化。 */
    lateinit var mahjongDice: EntityType<MahjongDiceEntity>
        private set

    /** 麻將牌 entity type；由 [register] 初始化。 */
    lateinit var mahjongTile: EntityType<MahjongTileEntity>
        private set

    /** 麻將點棒 entity type；由 [register] 初始化。 */
    lateinit var mahjongScoringStick: EntityType<MahjongScoringStickEntity>
        private set

    /** 桌面中央局況顯示 entity type；由 [register] 初始化。 */
    lateinit var mahjongRoundInfo: EntityType<MahjongRoundInfoEntity>
        private set

    /** 胡牌慶祝視覺效果 entity type；由 [register] 初始化。 */
    lateinit var winCelebrationEffect: EntityType<WinCelebrationEffectEntity>
        private set

    /** 註冊不自然生成的輕量麻將牌 entity。 */
    fun register() {
        mahjongDice = Registry.register(
            Registries.ENTITY_TYPE,
            Identifier(MinecraftModMetadata.MOD_ID, "mahjong_dice"),
            FabricEntityTypeBuilder.create(SpawnGroup.MISC, ::MahjongDiceEntity)
                .dimensions(EntityDimensions.fixed(MahjongDiceEntity.SIZE, MahjongDiceEntity.SIZE))
                .trackRangeBlocks(16)
                .trackedUpdateRate(10)
                .fireImmune()
                .build(),
        )
        mahjongTile = Registry.register(
            Registries.ENTITY_TYPE,
            Identifier(MinecraftModMetadata.MOD_ID, "mahjong_tile"),
            FabricEntityTypeBuilder.create(SpawnGroup.MISC, ::MahjongTileEntity)
                .dimensions(EntityDimensions.fixed(MahjongTileEntity.TILE_WIDTH, MahjongTileEntity.TILE_HEIGHT))
                .trackRangeBlocks(16)
                .trackedUpdateRate(10)
                .fireImmune()
                .build(),
        )
        mahjongScoringStick = Registry.register(
            Registries.ENTITY_TYPE,
            Identifier(MinecraftModMetadata.MOD_ID, "mahjong_scoring_stick"),
            FabricEntityTypeBuilder.create(SpawnGroup.MISC, ::MahjongScoringStickEntity)
                .dimensions(EntityDimensions.fixed(MahjongScoringStickEntity.STICK_WIDTH, MahjongScoringStickEntity.STICK_HEIGHT))
                .trackRangeBlocks(16)
                .trackedUpdateRate(10)
                .fireImmune()
                .build(),
        )
        mahjongRoundInfo = Registry.register(
            Registries.ENTITY_TYPE,
            Identifier(MinecraftModMetadata.MOD_ID, "mahjong_round_info"),
            FabricEntityTypeBuilder.create(SpawnGroup.MISC, ::MahjongRoundInfoEntity)
                .dimensions(EntityDimensions.fixed(ROUND_INFO_SIZE, ROUND_INFO_SIZE))
                .trackRangeBlocks(16)
                .trackedUpdateRate(10)
                .fireImmune()
                .build(),
        )
        winCelebrationEffect = Registry.register(
            Registries.ENTITY_TYPE,
            Identifier(MinecraftModMetadata.MOD_ID, "win_celebration_effect"),
            FabricEntityTypeBuilder.create(SpawnGroup.MISC, ::WinCelebrationEffectEntity)
                .dimensions(EntityDimensions.fixed(WinCelebrationEffectEntity.WIDTH, WinCelebrationEffectEntity.HEIGHT))
                .trackRangeBlocks(16)
                .trackedUpdateRate(1)
                .fireImmune()
                .build(),
        )
    }

    /**
     * 桌面中央局況顯示 entity 的碰撞箱大小，純視覺（不可碰撞）物件，數值本身不重要，但**不能是
     * `(0f, 0f)`**——vanilla 的 `Marker` entity（1.19+ 專門給「純資料、不渲染」用途設計）固定用零體積
     * 碰撞箱，這代表渲染管線的視錐剔除（`EntityRenderer.shouldRender`）對零體積碰撞箱有特殊處理，
     * 會直接判定不在視野內、整個跳過 `render()` 呼叫——這是遊戲內實際驗證過的問題：改成 `(0f, 0f)`
     * 後透過 IDE 斷點確認 `MahjongRoundInfoEntityRenderer.render()` 完全沒被呼叫到，改回非零值後才
     * 正常。
     */
    private const val ROUND_INFO_SIZE: Float = 0.1f
}

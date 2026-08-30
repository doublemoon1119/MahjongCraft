package com.doublemoon1119.mahjongcraft.platform.fabric.registry

import com.doublemoon1119.mahjongcraft.platform.fabric.entity.ExhaustiveDrawSettlementPresentationEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongDiceEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongLobbyInfoEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongPlayerInfoEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongRoundInfoEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongScoringStickEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTileEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MatchSettlementPresentationEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.WinCelebrationEffectEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.WinCelebrationShowcaseEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.WinSettlementPresentationEntity
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

    /** 桌級玩家公開資訊 entity type；由 [register] 初始化。 */
    lateinit var mahjongPlayerInfo: EntityType<MahjongPlayerInfoEntity>
        private set

    /** 等待中遊戲提示 entity type；由 [register] 初始化。 */
    lateinit var mahjongLobbyInfo: EntityType<MahjongLobbyInfoEntity>
        private set

    /** 胡牌慶祝視覺效果 entity type；由 [register] 初始化。 */
    lateinit var winCelebrationEffect: EntityType<WinCelebrationEffectEntity>
        private set

    /** 役種加碼共享舞台 entity type；由 [register] 初始化。 */
    lateinit var winCelebrationShowcase: EntityType<WinCelebrationShowcaseEntity>
        private set

    /** 統一流局結算排行舞台 entity type；由 [register] 初始化。 */
    lateinit var exhaustiveDrawSettlementPresentation: EntityType<ExhaustiveDrawSettlementPresentationEntity>
        private set

    /** 胡牌詳情與最終排行舞台 entity type。 */
    lateinit var winSettlementPresentation: EntityType<WinSettlementPresentationEntity>
        private set

    /** 終局最終排行舞台 entity type。 */
    lateinit var matchSettlementPresentation: EntityType<MatchSettlementPresentationEntity>
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
                .dimensions(EntityDimensions.fixed(MahjongRoundInfoEntity.WIDTH, MahjongRoundInfoEntity.HEIGHT))
                .trackRangeBlocks(16)
                .trackedUpdateRate(10)
                .fireImmune()
                .build(),
        )
        mahjongPlayerInfo = Registry.register(
            Registries.ENTITY_TYPE,
            Identifier(MinecraftModMetadata.MOD_ID, "mahjong_player_info"),
            FabricEntityTypeBuilder.create(SpawnGroup.MISC, ::MahjongPlayerInfoEntity)
                .dimensions(EntityDimensions.fixed(MahjongPlayerInfoEntity.WIDTH, MahjongPlayerInfoEntity.HEIGHT))
                .trackRangeBlocks(32)
                .trackedUpdateRate(10)
                .fireImmune()
                .build(),
        )
        mahjongLobbyInfo = Registry.register(
            Registries.ENTITY_TYPE,
            Identifier(MinecraftModMetadata.MOD_ID, "mahjong_lobby_info"),
            FabricEntityTypeBuilder.create(SpawnGroup.MISC, ::MahjongLobbyInfoEntity)
                .dimensions(EntityDimensions.fixed(MahjongLobbyInfoEntity.WIDTH, MahjongLobbyInfoEntity.HEIGHT))
                .trackRangeBlocks(32)
                .trackedUpdateRate(1)
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
        winCelebrationShowcase = Registry.register(
            Registries.ENTITY_TYPE,
            Identifier(MinecraftModMetadata.MOD_ID, "win_celebration_showcase"),
            FabricEntityTypeBuilder.create(SpawnGroup.MISC, ::WinCelebrationShowcaseEntity)
                .dimensions(EntityDimensions.fixed(WinCelebrationShowcaseEntity.WIDTH, WinCelebrationShowcaseEntity.HEIGHT))
                .trackRangeBlocks(32)
                .trackedUpdateRate(1)
                .fireImmune()
                .build(),
        )
        exhaustiveDrawSettlementPresentation = Registry.register(
            Registries.ENTITY_TYPE,
            Identifier(MinecraftModMetadata.MOD_ID, "exhaustive_draw_settlement_presentation"),
            FabricEntityTypeBuilder.create(SpawnGroup.MISC, ::ExhaustiveDrawSettlementPresentationEntity)
                .dimensions(EntityDimensions.fixed(ExhaustiveDrawSettlementPresentationEntity.WIDTH, ExhaustiveDrawSettlementPresentationEntity.HEIGHT))
                .trackRangeBlocks(32)
                .trackedUpdateRate(1)
                .fireImmune()
                .build(),
        )
        winSettlementPresentation = Registry.register(
            Registries.ENTITY_TYPE,
            Identifier(MinecraftModMetadata.MOD_ID, "win_settlement_presentation"),
            FabricEntityTypeBuilder.create(SpawnGroup.MISC, ::WinSettlementPresentationEntity)
                .dimensions(EntityDimensions.fixed(WinSettlementPresentationEntity.WIDTH, WinSettlementPresentationEntity.HEIGHT))
                .trackRangeBlocks(32)
                .trackedUpdateRate(1)
                .fireImmune()
                .build(),
        )
        matchSettlementPresentation = Registry.register(
            Registries.ENTITY_TYPE,
            Identifier(MinecraftModMetadata.MOD_ID, "match_settlement_presentation"),
            FabricEntityTypeBuilder.create(SpawnGroup.MISC, ::MatchSettlementPresentationEntity)
                .dimensions(EntityDimensions.fixed(MatchSettlementPresentationEntity.WIDTH, MatchSettlementPresentationEntity.HEIGHT))
                .trackRangeBlocks(32)
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
}

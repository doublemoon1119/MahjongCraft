package com.doublemoon1119.mahjongcraft.platform.fabric.registry

import com.doublemoon1119.mahjongcraft.platform.fabric.entity.DiceRollPresentationEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.ExhaustiveDrawSettlementPresentationEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongDiceEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongLobbyInfoEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongPlayerInfoEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongRoundInfoEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongScoringStickEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongSoundTimelineEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongStoolSeatEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongStoolSeatGeometry
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTileEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTileSelectionConfirmEntity
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
    /** 無形且可持久化的單次聲音時間線 entity type；由 [register] 初始化。 */
    lateinit var mahjongSoundTimeline: EntityType<MahjongSoundTimelineEntity>
        private set

    /** 麻將骰子 entity type；由 [register] 初始化。 */
    lateinit var mahjongDice: EntityType<MahjongDiceEntity>
        private set

    /** 擲骰聚合結果面板 entity type；由 [register] 初始化。 */
    lateinit var diceRollPresentation: EntityType<DiceRollPresentationEntity>
        private set

    /** 麻將牌 entity type；由 [register] 初始化。 */
    lateinit var mahjongTile: EntityType<MahjongTileEntity>
        private set

    /** 多選選牌確認面板 entity type；由 [register] 初始化。 */
    lateinit var mahjongTileSelectionConfirm: EntityType<MahjongTileSelectionConfirmEntity>
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

    /** 麻將凳的隱形座位 entity type；由 [register] 初始化。 */
    lateinit var mahjongStoolSeat: EntityType<MahjongStoolSeatEntity>
        private set

    /** 註冊不自然生成的輕量麻將牌 entity。 */
    fun register() {
        mahjongSoundTimeline = Registry.register(
            Registries.ENTITY_TYPE,
            Identifier(MinecraftModMetadata.MOD_ID, "mahjong_sound_timeline"),
            FabricEntityTypeBuilder.create(SpawnGroup.MISC, ::MahjongSoundTimelineEntity)
                .dimensions(EntityDimensions.fixed(MahjongSoundTimelineEntity.SIZE, MahjongSoundTimelineEntity.SIZE))
                .trackRangeBlocks(32)
                .trackedUpdateRate(1)
                .fireImmune()
                .build(),
        )
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
        diceRollPresentation = Registry.register(
            Registries.ENTITY_TYPE,
            Identifier(MinecraftModMetadata.MOD_ID, "dice_roll_presentation"),
            FabricEntityTypeBuilder.create(SpawnGroup.MISC, ::DiceRollPresentationEntity)
                .dimensions(EntityDimensions.fixed(DiceRollPresentationEntity.WIDTH, DiceRollPresentationEntity.HEIGHT))
                .trackRangeBlocks(32)
                .trackedUpdateRate(1)
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
        mahjongTileSelectionConfirm = Registry.register(
            Registries.ENTITY_TYPE,
            Identifier(MinecraftModMetadata.MOD_ID, "mahjong_tile_selection_confirm"),
            FabricEntityTypeBuilder.create(SpawnGroup.MISC, ::MahjongTileSelectionConfirmEntity)
                .dimensions(EntityDimensions.fixed(MahjongTileSelectionConfirmEntity.WIDTH, MahjongTileSelectionConfirmEntity.HEIGHT))
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
        mahjongStoolSeat = Registry.register(
            Registries.ENTITY_TYPE,
            Identifier(MinecraftModMetadata.MOD_ID, "mahjong_stool_seat"),
            FabricEntityTypeBuilder.create(SpawnGroup.MISC, ::MahjongStoolSeatEntity)
                .dimensions(EntityDimensions.fixed(MahjongStoolSeatGeometry.SEAT_SIZE, MahjongStoolSeatGeometry.SEAT_SIZE))
                // 與原版的船相同，讓看得到乘客的玩家也看得到座位
                .trackRangeChunks(10)
                .trackedUpdateRate(10)
                .fireImmune()
                .build(),
        )
    }
}

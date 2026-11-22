package com.doublemoon1119.mahjongcraft.platform.fabric.registry

import com.doublemoon1119.mahjongcraft.platform.fabric.entity.DiceRollPresentationEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.ExhaustiveDrawSettlementPresentationEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongBotEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongDiceEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongLobbyInfoEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongPlayerInfoEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongRoundInfoEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongRulerEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongScoringStickEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongSoundTimelineEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongStoolSeatEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTileEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTileSelectionConfirmEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MatchSettlementPresentationEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.TablePropTileEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.WinCelebrationEffectEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.WinCelebrationShowcaseEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.WinSettlementPresentationEntity
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.entity.MahjongStoolSeatGeometry
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ModRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.RegisteredEntry
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.registry.MahjongEntityTypes
import com.doublemoon1119.mahjongcraft.platform.minecraft.ruler.MahjongRulerColor
import com.doublemoon1119.mahjongcraft.platform.minecraft.ruler.MahjongRulerDimensions
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.BuiltInTablePropDefinitions
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.EntityType

/** MahjongCraft 實體型別的集中登記點；各型別在 [register] 登記完成後才能讀取。 */
object ModEntities {
    /** 登記完成後可讀取 [mahjongSoundTimeline] 的項目。 */
    private lateinit var mahjongSoundTimelineEntry: RegisteredEntry<EntityType<MahjongSoundTimelineEntity>>

    /** 無形且可持久化的單次聲音時間線 entity type。 */
    val mahjongSoundTimeline: EntityType<MahjongSoundTimelineEntity>
        get() = mahjongSoundTimelineEntry.get()

    /** 登記完成後可讀取 [mahjongDice] 的項目。 */
    private lateinit var mahjongDiceEntry: RegisteredEntry<EntityType<MahjongDiceEntity>>

    /** 麻將骰子 entity type。 */
    val mahjongDice: EntityType<MahjongDiceEntity>
        get() = mahjongDiceEntry.get()

    /** 登記完成後可讀取 [diceRollPresentation] 的項目。 */
    private lateinit var diceRollPresentationEntry: RegisteredEntry<EntityType<DiceRollPresentationEntity>>

    /** 擲骰聚合結果面板 entity type。 */
    val diceRollPresentation: EntityType<DiceRollPresentationEntity>
        get() = diceRollPresentationEntry.get()

    /** 登記完成後可讀取 [mahjongTile] 的項目。 */
    private lateinit var mahjongTileEntry: RegisteredEntry<EntityType<MahjongTileEntity>>

    /** 麻將牌 entity type。 */
    val mahjongTile: EntityType<MahjongTileEntity>
        get() = mahjongTileEntry.get()

    /** 登記完成後可讀取 [tablePropTile] 的項目。 */
    private lateinit var tablePropTileEntry: RegisteredEntry<EntityType<TablePropTileEntity>>

    /** 公開桌面展示牌的獨立實體類型。 */
    val tablePropTile: EntityType<TablePropTileEntity>
        get() = tablePropTileEntry.get()

    /** 登記完成後可讀取 [mahjongTileSelectionConfirm] 的項目。 */
    private lateinit var mahjongTileSelectionConfirmEntry: RegisteredEntry<EntityType<MahjongTileSelectionConfirmEntity>>

    /** 多選選牌確認面板 entity type。 */
    val mahjongTileSelectionConfirm: EntityType<MahjongTileSelectionConfirmEntity>
        get() = mahjongTileSelectionConfirmEntry.get()

    /** 登記完成後可讀取 [mahjongScoringStick] 的項目。 */
    private lateinit var mahjongScoringStickEntry: RegisteredEntry<EntityType<MahjongScoringStickEntity>>

    /** 麻將點棒 entity type。 */
    val mahjongScoringStick: EntityType<MahjongScoringStickEntity>
        get() = mahjongScoringStickEntry.get()

    /** 登記完成後可讀取 [mahjongRuler] 的項目。 */
    private lateinit var mahjongRulerEntry: RegisteredEntry<EntityType<MahjongRulerEntity>>

    /** 放在方塊上的牌尺 entity type。 */
    val mahjongRuler: EntityType<MahjongRulerEntity>
        get() = mahjongRulerEntry.get()

    /** 登記完成後可讀取 [mahjongRoundInfo] 的項目。 */
    private lateinit var mahjongRoundInfoEntry: RegisteredEntry<EntityType<MahjongRoundInfoEntity>>

    /** 桌面中央局況顯示 entity type。 */
    val mahjongRoundInfo: EntityType<MahjongRoundInfoEntity>
        get() = mahjongRoundInfoEntry.get()

    /** 登記完成後可讀取 [mahjongPlayerInfo] 的項目。 */
    private lateinit var mahjongPlayerInfoEntry: RegisteredEntry<EntityType<MahjongPlayerInfoEntity>>

    /** 桌級玩家公開資訊 entity type。 */
    val mahjongPlayerInfo: EntityType<MahjongPlayerInfoEntity>
        get() = mahjongPlayerInfoEntry.get()

    /** 登記完成後可讀取 [mahjongLobbyInfo] 的項目。 */
    private lateinit var mahjongLobbyInfoEntry: RegisteredEntry<EntityType<MahjongLobbyInfoEntity>>

    /** 等待中遊戲提示 entity type。 */
    val mahjongLobbyInfo: EntityType<MahjongLobbyInfoEntity>
        get() = mahjongLobbyInfoEntry.get()

    /** 登記完成後可讀取 [winCelebrationEffect] 的項目。 */
    private lateinit var winCelebrationEffectEntry: RegisteredEntry<EntityType<WinCelebrationEffectEntity>>

    /** 胡牌慶祝視覺效果 entity type。 */
    val winCelebrationEffect: EntityType<WinCelebrationEffectEntity>
        get() = winCelebrationEffectEntry.get()

    /** 登記完成後可讀取 [winCelebrationShowcase] 的項目。 */
    private lateinit var winCelebrationShowcaseEntry: RegisteredEntry<EntityType<WinCelebrationShowcaseEntity>>

    /** 役種加碼共享舞台 entity type。 */
    val winCelebrationShowcase: EntityType<WinCelebrationShowcaseEntity>
        get() = winCelebrationShowcaseEntry.get()

    /** 登記完成後可讀取 [exhaustiveDrawSettlementPresentation] 的項目。 */
    private lateinit var exhaustiveDrawSettlementPresentationEntry: RegisteredEntry<EntityType<ExhaustiveDrawSettlementPresentationEntity>>

    /** 統一流局結算排行舞台 entity type。 */
    val exhaustiveDrawSettlementPresentation: EntityType<ExhaustiveDrawSettlementPresentationEntity>
        get() = exhaustiveDrawSettlementPresentationEntry.get()

    /** 登記完成後可讀取 [winSettlementPresentation] 的項目。 */
    private lateinit var winSettlementPresentationEntry: RegisteredEntry<EntityType<WinSettlementPresentationEntity>>

    /** 胡牌詳情與最終排行舞台 entity type。 */
    val winSettlementPresentation: EntityType<WinSettlementPresentationEntity>
        get() = winSettlementPresentationEntry.get()

    /** 登記完成後可讀取 [matchSettlementPresentation] 的項目。 */
    private lateinit var matchSettlementPresentationEntry: RegisteredEntry<EntityType<MatchSettlementPresentationEntity>>

    /** 終局最終排行舞台 entity type。 */
    val matchSettlementPresentation: EntityType<MatchSettlementPresentationEntity>
        get() = matchSettlementPresentationEntry.get()

    /** 登記完成後可讀取 [mahjongStoolSeat] 的項目。 */
    private lateinit var mahjongStoolSeatEntry: RegisteredEntry<EntityType<MahjongStoolSeatEntity>>

    /** 麻將凳的隱形座位 entity type。 */
    val mahjongStoolSeat: EntityType<MahjongStoolSeatEntity>
        get() = mahjongStoolSeatEntry.get()

    /** 登記完成後可讀取 [mahjongBot] 的項目。 */
    private lateinit var mahjongBotEntry: RegisteredEntry<EntityType<MahjongBotEntity>>

    /** 不寫入區塊存檔的麻將機器人；由角色生命週期恢復。 */
    val mahjongBot: EntityType<MahjongBotEntity>
        get() = mahjongBotEntry.get()

    /**
     * 登記骰子、牌張、點棒、牌尺、座位與各種呈現實體的類型；皆不自然生成。
     *
     * @param registrar 寫入登記表的方式。
     */
    fun register(registrar: ModRegistrar) {
        mahjongBotEntry = registrar.register(
            registry = Registries.ENTITY_TYPE,
            id = ResourceLocation(MinecraftModMetadata.MOD_ID, "mahjong_bot"),
        ) {
            MahjongEntityTypes.create(
                factory = ::MahjongBotEntity,
                width = MahjongBotEntity.WIDTH,
                height = MahjongBotEntity.HEIGHT,
                trackingRangeChunks = MahjongEntityTypes.chunksCovering(blocks = 32),
                updateInterval = 1,
                saveable = false,
            )
        }
        mahjongSoundTimelineEntry = registrar.register(
            registry = Registries.ENTITY_TYPE,
            id = ResourceLocation(MinecraftModMetadata.MOD_ID, "mahjong_sound_timeline"),
        ) {
            MahjongEntityTypes.create(
                factory = ::MahjongSoundTimelineEntity,
                width = MahjongSoundTimelineEntity.SIZE,
                height = MahjongSoundTimelineEntity.SIZE,
                trackingRangeChunks = MahjongEntityTypes.chunksCovering(blocks = 32),
                updateInterval = 1,
            )
        }
        mahjongDiceEntry = registrar.register(
            registry = Registries.ENTITY_TYPE,
            id = ResourceLocation(MinecraftModMetadata.MOD_ID, "mahjong_dice"),
        ) {
            MahjongEntityTypes.create(
                factory = ::MahjongDiceEntity,
                width = MahjongDiceEntity.SIZE,
                height = MahjongDiceEntity.SIZE,
                trackingRangeChunks = MahjongEntityTypes.chunksCovering(blocks = 16),
                updateInterval = 10,
            )
        }
        diceRollPresentationEntry = registrar.register(
            registry = Registries.ENTITY_TYPE,
            id = ResourceLocation(MinecraftModMetadata.MOD_ID, "dice_roll_presentation"),
        ) {
            MahjongEntityTypes.create(
                factory = ::DiceRollPresentationEntity,
                width = DiceRollPresentationEntity.WIDTH,
                height = DiceRollPresentationEntity.HEIGHT,
                trackingRangeChunks = MahjongEntityTypes.chunksCovering(blocks = 32),
                updateInterval = 1,
            )
        }
        mahjongTileEntry = registrar.register(
            registry = Registries.ENTITY_TYPE,
            id = ResourceLocation(MinecraftModMetadata.MOD_ID, "mahjong_tile"),
        ) {
            MahjongEntityTypes.create(
                factory = ::MahjongTileEntity,
                width = MahjongTileEntity.TILE_WIDTH,
                height = MahjongTileEntity.TILE_HEIGHT,
                trackingRangeChunks = MahjongEntityTypes.chunksCovering(blocks = 16),
                updateInterval = 10,
            )
        }
        tablePropTileEntry = registrar.register(
            registry = Registries.ENTITY_TYPE,
            id = ResourceLocation(MinecraftModMetadata.MOD_ID, "table_prop_tile"),
        ) {
            MahjongEntityTypes.create(
                factory = ::TablePropTileEntity,
                width = BuiltInTablePropDefinitions.FACE_UP_TILE.dimensions.depth.toFloat(),
                height = BuiltInTablePropDefinitions.FACE_UP_TILE.dimensions.height.toFloat(),
                trackingRangeChunks = MahjongEntityTypes.chunksCovering(blocks = 16),
                updateInterval = 10,
            )
        }
        mahjongTileSelectionConfirmEntry = registrar.register(
            registry = Registries.ENTITY_TYPE,
            id = ResourceLocation(MinecraftModMetadata.MOD_ID, "mahjong_tile_selection_confirm"),
        ) {
            MahjongEntityTypes.create(
                factory = ::MahjongTileSelectionConfirmEntity,
                width = MahjongTileSelectionConfirmEntity.WIDTH,
                height = MahjongTileSelectionConfirmEntity.HEIGHT,
                trackingRangeChunks = MahjongEntityTypes.chunksCovering(blocks = 16),
                updateInterval = 10,
            )
        }
        mahjongScoringStickEntry = registrar.register(
            registry = Registries.ENTITY_TYPE,
            id = ResourceLocation(MinecraftModMetadata.MOD_ID, "mahjong_scoring_stick"),
        ) {
            MahjongEntityTypes.create(
                factory = ::MahjongScoringStickEntity,
                width = MahjongScoringStickEntity.STICK_WIDTH,
                height = MahjongScoringStickEntity.STICK_HEIGHT,
                trackingRangeChunks = MahjongEntityTypes.chunksCovering(blocks = 16),
                updateInterval = 10,
            )
        }
        mahjongRulerEntry = registrar.register(
            registry = Registries.ENTITY_TYPE,
            id = ResourceLocation(MinecraftModMetadata.MOD_ID, MahjongRulerColor.BASE_ITEM_PATH),
        ) {
            MahjongEntityTypes.create(
                factory = ::MahjongRulerEntity,
                width = MahjongRulerDimensions.LENGTH.toFloat(),
                height = MahjongRulerDimensions.BROAD_SIDE.toFloat(),
                trackingRangeChunks = MahjongEntityTypes.chunksCovering(blocks = 16),
                updateInterval = 10,
            )
        }
        mahjongRoundInfoEntry = registrar.register(
            registry = Registries.ENTITY_TYPE,
            id = ResourceLocation(MinecraftModMetadata.MOD_ID, "mahjong_round_info"),
        ) {
            MahjongEntityTypes.create(
                factory = ::MahjongRoundInfoEntity,
                width = MahjongRoundInfoEntity.WIDTH,
                height = MahjongRoundInfoEntity.HEIGHT,
                trackingRangeChunks = MahjongEntityTypes.chunksCovering(blocks = 16),
                updateInterval = 10,
            )
        }
        mahjongPlayerInfoEntry = registrar.register(
            registry = Registries.ENTITY_TYPE,
            id = ResourceLocation(MinecraftModMetadata.MOD_ID, "mahjong_player_info"),
        ) {
            MahjongEntityTypes.create(
                factory = ::MahjongPlayerInfoEntity,
                width = MahjongPlayerInfoEntity.WIDTH,
                height = MahjongPlayerInfoEntity.HEIGHT,
                trackingRangeChunks = MahjongEntityTypes.chunksCovering(blocks = 32),
                updateInterval = 10,
            )
        }
        mahjongLobbyInfoEntry = registrar.register(
            registry = Registries.ENTITY_TYPE,
            id = ResourceLocation(MinecraftModMetadata.MOD_ID, "mahjong_lobby_info"),
        ) {
            MahjongEntityTypes.create(
                factory = ::MahjongLobbyInfoEntity,
                width = MahjongLobbyInfoEntity.WIDTH,
                height = MahjongLobbyInfoEntity.HEIGHT,
                trackingRangeChunks = MahjongEntityTypes.chunksCovering(blocks = 32),
                updateInterval = 1,
            )
        }
        winCelebrationEffectEntry = registrar.register(
            registry = Registries.ENTITY_TYPE,
            id = ResourceLocation(MinecraftModMetadata.MOD_ID, "win_celebration_effect"),
        ) {
            MahjongEntityTypes.create(
                factory = ::WinCelebrationEffectEntity,
                width = WinCelebrationEffectEntity.WIDTH,
                height = WinCelebrationEffectEntity.HEIGHT,
                trackingRangeChunks = MahjongEntityTypes.chunksCovering(blocks = 16),
                updateInterval = 1,
            )
        }
        winCelebrationShowcaseEntry = registrar.register(
            registry = Registries.ENTITY_TYPE,
            id = ResourceLocation(MinecraftModMetadata.MOD_ID, "win_celebration_showcase"),
        ) {
            MahjongEntityTypes.create(
                factory = ::WinCelebrationShowcaseEntity,
                width = WinCelebrationShowcaseEntity.WIDTH,
                height = WinCelebrationShowcaseEntity.HEIGHT,
                trackingRangeChunks = MahjongEntityTypes.chunksCovering(blocks = 32),
                updateInterval = 1,
            )
        }
        exhaustiveDrawSettlementPresentationEntry = registrar.register(
            registry = Registries.ENTITY_TYPE,
            id = ResourceLocation(MinecraftModMetadata.MOD_ID, "exhaustive_draw_settlement_presentation"),
        ) {
            MahjongEntityTypes.create(
                factory = ::ExhaustiveDrawSettlementPresentationEntity,
                width = ExhaustiveDrawSettlementPresentationEntity.WIDTH,
                height = ExhaustiveDrawSettlementPresentationEntity.HEIGHT,
                trackingRangeChunks = MahjongEntityTypes.chunksCovering(blocks = 32),
                updateInterval = 1,
            )
        }
        winSettlementPresentationEntry = registrar.register(
            registry = Registries.ENTITY_TYPE,
            id = ResourceLocation(MinecraftModMetadata.MOD_ID, "win_settlement_presentation"),
        ) {
            MahjongEntityTypes.create(
                factory = ::WinSettlementPresentationEntity,
                width = WinSettlementPresentationEntity.WIDTH,
                height = WinSettlementPresentationEntity.HEIGHT,
                trackingRangeChunks = MahjongEntityTypes.chunksCovering(blocks = 32),
                updateInterval = 1,
            )
        }
        matchSettlementPresentationEntry = registrar.register(
            registry = Registries.ENTITY_TYPE,
            id = ResourceLocation(MinecraftModMetadata.MOD_ID, "match_settlement_presentation"),
        ) {
            MahjongEntityTypes.create(
                factory = ::MatchSettlementPresentationEntity,
                width = MatchSettlementPresentationEntity.WIDTH,
                height = MatchSettlementPresentationEntity.HEIGHT,
                trackingRangeChunks = MahjongEntityTypes.chunksCovering(blocks = 32),
                updateInterval = 1,
            )
        }
        mahjongStoolSeatEntry = registrar.register(
            registry = Registries.ENTITY_TYPE,
            id = ResourceLocation(MinecraftModMetadata.MOD_ID, "mahjong_stool_seat"),
        ) {
            MahjongEntityTypes.create(
                factory = ::MahjongStoolSeatEntity,
                width = MahjongStoolSeatGeometry.SEAT_SIZE,
                height = MahjongStoolSeatGeometry.SEAT_SIZE,
                // 與原版的船相同，讓看得到乘客的玩家也看得到座位
                trackingRangeChunks = 10,
                updateInterval = 10,
            )
        }
    }
}

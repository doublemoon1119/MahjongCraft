package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.flow.common.game.service.GameEventPublisher
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GamePresentationPublisher
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.flow.server.game.service.HandSortPreferenceStore
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DeclareAndDiscardUseCase
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry

/**
 * 執行擴充命令時可使用的對局流程服務。
 *
 * 每個執行環境（例如正式遊戲或隔離的無頭對局）各自提供一份，handler 只會改動自己環境的對局。
 *
 * @property gameRepository 權威對局數據倉庫。
 * @property moduleRegistry 依對局設定取得規則模組。
 * @property snapshotSynchronizer 對局快照同步服務。
 * @property handSortPreferenceStore 查詢玩家是否啟用自動整理手牌。
 * @property postActionExhaustiveDrawResolverRegistry 動作完成後主動觸發途中流局的判定 registry。
 * @property eventPublisher 對局通知服務。
 * @property presentationPublisher 對局呈現通知服務。
 * @property declareAndDiscardUseCase 第三方宣告與附帶捨牌的共用權威流程。
 */
class ExtensionGameCommandContext(
    val gameRepository: GameRepository,
    val moduleRegistry: MahjongModuleRegistry,
    val snapshotSynchronizer: GameSnapshotSynchronizer,
    val handSortPreferenceStore: HandSortPreferenceStore,
    val postActionExhaustiveDrawResolverRegistry: PostActionExhaustiveDrawResolverRegistry,
    val eventPublisher: GameEventPublisher,
    val presentationPublisher: GamePresentationPublisher,
    val declareAndDiscardUseCase: DeclareAndDiscardUseCase,
)

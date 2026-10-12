package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropPresenter
import kotlin.uuid.Uuid

/**
 * 將權威手牌／摸牌位／副露呈現於 Minecraft 世界的版本 adapter 邊界。
 *
 * 每次呼叫提供立牌、摸牌與副露的完整狀態，使實作能一併計算彼此及角落物件之間所需的空間。
 *
 * 三者裡每一張牌的 UUID 都跟牌牆結構座標裡的同一張牌完全相同——這副牌本來就是從牌牆摸出來分給
 * 玩家、或由玩家鳴牌鳴來的，不是另外複製出一批新牌。實作因此不建立新 entity，而是找到
 * [MahjongTileWallPresenter] 已經生成好的既有 entity，直接改標記、改姿態、移動位置，比照
 * [MahjongTileWallPresenter] 的 best-effort 慣例。
 *
 * 規則桌面物件的 entity 由 [TablePropPresenter] 管理；這裡只讀取 [MahjongPlayerAreaPresentation.cornerWidth]
 * 以計算讓位偏移。
 *
 * [MahjongTileTableLayout.meldPlacement]／[MahjongTileTableLayout.handPlacement] 只負責單一格位／
 * 單張牌座標，把多組副露、各組張數、各組內鳴取牌位置、角落佔用寬度換算成這些函式需要的參數是實作的
 * 責任——見 [MahjongTileTableLayout.meldAreaWidth]／[MahjongTileTableLayout.handCornerYieldShift] 的 KDoc。
 */
interface MahjongPlayerAreaPresenter {
    /**
     * 在指定桌面呈現這位玩家的手牌／摸牌位／副露；[MahjongPlayerAreaPresentation.standingTileIds] 與
     * [MahjongPlayerAreaPresentation.melds] 皆為空、[MahjongPlayerAreaPresentation.drawnTileId] 為
     * `null` 時等同只清除舊牌。
     *
     * @param presentation 已由伺服器決定的玩家區域呈現資料。
     * @return 玩家區域呈現結果。
     */
    fun present(presentation: MahjongPlayerAreaPresentation): MahjongPlayerAreaPresentationResult

    /**
     * 呈現開局發牌動畫：依 [MahjongInitialDealPresentation.dealBatchSizes] 分批，每批同時對所有座位
     * 執行「牌從牌山原位小幅起飛→頂點瞬間重新排列到手牌列上空、面朝下→落下到最終手牌位置」的動畫，
     * 播完後牌維持面朝下姿態靜置在最終手牌位置——翻牌動畫是後續獨立的一步，不在這個方法的職責內。
     *
     * 跟 [present] 一樣不建立新 entity，領走 [MahjongTileWallPresenter] 已生成好的既有牌牆 entity。
     *
     * @param presentation 開局發牌動畫的完整呈現資料。
     * @return 玩家區域呈現結果。
     */
    fun presentInitialDeal(presentation: MahjongInitialDealPresentation): MahjongPlayerAreaPresentationResult

    /**
     * 呈現胡牌慶祝演出第一階段的「強制理牌 → （自摸牌單獨倒下 →）等待 → 立牌一起倒下」序列——不含
     * 降臨特效本身（那是獨立的粒子排程器職責，見 `FabricWinCelebrationEffectScheduler`），但回傳算好的
     * 「立牌全部倒下完成」絕對 game time，供呼叫端接續排定特效開始時機、並延長胡的那張牌的動畫佇列
     * （`TablePresentationBusyTracker` 忙碌窗口），理由見 [MahjongWinCelebrationResult] KDoc。
     *
     * 跟 [present] 一樣不建立新 entity，只移動既有 entity；姿態轉換一律透過既有動畫步驟表達，不直接
     * 賦值姿態欄位。
     *
     * @param presentation 胡牌慶祝演出所需的呈現資料。
     * @return 動畫完成時間與可接續安排特效的結果。
     */
    fun presentWinCelebration(presentation: MahjongWinCelebrationPresentation): MahjongWinCelebrationResult

    /**
     * 清除指定桌子目前的正式手牌／摸牌位／副露用牌。
     *
     * @param tableId 麻將桌 UUID。
     * @param tableLocation 麻將桌目前位置。
     * @return 實際移除的 entity 數量。
     */
    fun clear(tableId: Uuid, tableLocation: TableLocation): Int
}

package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropPresenter
import kotlin.uuid.Uuid

/**
 * 將權威手牌／摸牌位／副露呈現於 Minecraft 世界的版本 adapter 邊界。
 *
 * 三者原本各自獨立成 `MahjongHandTilesPresenter`／`MahjongMeldPresenter`，但手牌／摸牌位要能對副露
 * （以及角落物件）讓開空間，前提是同一次計算必須同時知道「這一側總共被佔用多少寬度」——手牌被觸發呈現
 * 的當下，如果不知道目前副露佔了多寬，就沒辦法算出正確的讓開偏移；反之亦然。這幾個東西必須包裝成
 * 同一個方法呼叫，才有辦法完整地透過計算算出退讓的偏移，因此合併成這一個 presenter，每次呼叫都帶齊
 * 立牌、摸牌、副露三者目前的完整狀態。
 *
 * 三者裡每一張牌的 UUID 都跟牌牆結構座標裡的同一張牌完全相同——這副牌本來就是從牌牆摸出來分給
 * 玩家、或由玩家鳴牌鳴來的，不是另外複製出一批新牌。實作因此不建立新 entity，而是找到
 * [MahjongTileWallPresenter] 已經生成好的既有 entity，直接改標記、改姿態、移動位置，比照
 * [MahjongTileWallPresenter] 的 best-effort 慣例。
 *
 * 角落裡的規則桌面物件（佔用寬度見 [MahjongPlayerAreaPresentation.cornerWidth]）本身的 entity 生成／清除
 * **不**歸這個 presenter 管——那些物件只在特定時間點更新，生命週期完全不同步於手牌／副露（每次打牌/
 * 摸牌/鳴牌都會觸發），交給獨立的 [TablePropPresenter]；這裡只把寬度當成算讓開偏移用的數字。
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
     */
    fun present(presentation: MahjongPlayerAreaPresentation): MahjongPlayerAreaPresentationResult

    /**
     * 呈現開局發牌動畫：依 [MahjongInitialDealPresentation.dealBatchSizes] 分批，每批同時對所有座位
     * 執行「牌從牌山原位小幅起飛→頂點瞬間重新排列到手牌列上空、面朝下→落下到最終手牌位置」的動畫，
     * 播完後牌維持面朝下姿態靜置在最終手牌位置——翻牌動畫是後續獨立的一步，不在這個方法的職責內。
     *
     * 跟 [present] 一樣不建立新 entity，領走 [MahjongTileWallPresenter] 已生成好的既有牌牆 entity。
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
     */
    fun presentWinCelebration(presentation: MahjongWinCelebrationPresentation): MahjongWinCelebrationResult

    /** 清除指定桌子目前的正式手牌／摸牌位／副露用牌；回傳實際移除數量。 */
    fun clear(tableId: Uuid, tableLocation: TableLocation): Int
}

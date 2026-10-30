package com.doublemoon1119.mahjongcraft.logic.tile

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.base.TileFaceView
import com.doublemoon1119.mahjongcraft.logic.base.TileValue

/**
 * 將規則特有牌種解讀成共用麻將運算可使用的正規牌面。
 *
 * 例如規則可以讓自己的擴充牌種在吃、碰、槓、向聽與牌型比較時等同某張基本牌。共用流程只能透過
 * [canonicalize] 比較牌面，不得直接判斷特定 extension ID。逐張特性（例如日麻赤寶牌）不屬於牌面，
 * 由 [valueOf] 解讀。
 */
fun interface TileInterpretationPolicy {
    /** 將 [tile] 轉為目前規則用於一般牌面比較的正規表示。 */
    fun canonicalize(tile: Tile): Tile

    /**
     * 由已獲准的牌面資料建立分析用牌值，不還原未公開的權威組件。
     *
     * 預設只保留基本牌面；規則若公開會影響計算的特性，須在此解讀自己提供的投影。
     *
     * @param face 已依讀取者權限裁切的牌面資料。
     * @return 只含已知計算特性的牌值，不帶局內身分。
     */
    fun valueOf(face: TileFaceView): TileValue = TileValue(face.tile)

    /**
     * 從公開牌組組成取出影響計算的特性，略去授權、姿態等無關組件。
     *
     * 回傳值須與同一個牌值的 [TileFaceView] 經 [valueOf] 解讀後一致，才能正確統計張數。
     *
     * @param value 公開規則定義的牌組組成，不是未公開的局內牌張。
     * @return 用於計算與張數分組的牌值。
     */
    fun valueOf(value: TileValue): TileValue = TileValue(value.tile)

    /**
     * 判斷公開牌牆牌是否已確定不能從一般摸牌取得。
     *
     * 牌面可見不代表已離開牌山。只有公開資訊足以判斷時才回傳 true；
     * 未知用途不應從剩餘候選張數中扣除。
     *
     * @param face 已獲准知道的牌面與公開特徵。
     * @return 已公開表明不能摸取時為 true。
     */
    fun isUnavailableForDraw(face: TileFaceView): Boolean = false
}

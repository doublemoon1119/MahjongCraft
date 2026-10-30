package com.doublemoon1119.mahjongcraft.logic.table

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.component.BuiltInComponentOwners
import com.doublemoon1119.mahjongcraft.logic.component.ComponentSet

/**
 * 牌河介面。
 *
 * 負責管理並追蹤玩家打出至場上的廢牌紀錄。透過泛型 [T] 支援不同規則下的自定義紀錄格式。
 *
 * 本介面的實作應為不可變值物件：[discard]、[takeLast] 皆不修改原實例，
 * 而是回傳反映變更後狀態的新 [DiscardPile] 實例。
 *
 * @param T 捨牌紀錄的型別，必須繼承自 [DiscardPile.DiscardEntry]。
 */
interface DiscardPile<T : DiscardPile.DiscardEntry> {
    /**
     * 獲取目前牌河中所有的紀錄。
     *
     * @return 包含所有 [T] 紀錄的唯讀列表。
     */
    val entries: List<T>

    /**
     * 向牌河添加一項捨牌紀錄。
     *
     * @param entry 具體的捨牌紀錄實體。
     * @return 加入該紀錄後的新 [DiscardPile] 實例。
     */
    fun discard(entry: T): DiscardPile<T>

    /**
     * 標記最後一項紀錄已被取走。
     *
     * 當其他玩家進行鳴牌動作（如吃、碰、槓）時呼叫，將最後一張捨牌的 [DiscardEntry.isTaken] 狀態設為 true。
     *
     * @return 標記後的新 [DiscardPile] 實例；若牌河為空則回傳原實例。
     */
    fun takeLast(): DiscardPile<T>

    /**
     * 依據一張牌建立本規則預設（不含任何規則特有狀態，如立直）的捨牌紀錄，並加入牌河。
     *
     * 提供給不知道具體規則 [T] 為何的呼叫端使用，讓其能透過統一介面捨牌，
     * 不需要知道每種規則各自的 [DiscardEntry] 子類別建構方式。規則特有的狀態（如立直宣告）
     * 仍應由該規則專屬的 use case 直接呼叫 [discard] 並傳入完整建構的紀錄。
     *
     * @param tile 欲捨棄的牌。
     * @return 加入該紀錄後的新 [DiscardPile] 實例。
     */
    fun discardTile(tile: IdentifiedTile): DiscardPile<T>

    /**
     * 替換尚未被取走的同張牌，只容許組件改變；舊的已取走紀錄保持原值。
     *
     * @param tile 要保存的新牌張版本。
     * @return 保留紀錄型別及其他欄位的新牌河。
     */
    fun replaceTile(tile: IdentifiedTile): DiscardPile<T>

    /**
     * 替換一筆紀錄的組件，保留牌張、取走狀態與紀錄型別。
     *
     * @param index 紀錄在 [entries] 中的位置。
     * @param components 這筆紀錄的完整新組件。
     * @return 只有該筆紀錄改變的新牌河。
     */
    fun replaceEntryComponents(index: Int, components: ComponentSet): DiscardPile<T>

    /**
     * 代表牌河中的單一紀錄實體基礎類別。
     *
     * @property tile 被打出的具有唯一標識的麻將牌。
     * @property isTaken 標記該張牌是否已被其他玩家鳴走。
     * @property components 這次捨牌的不可變紀錄組件，不跟著被取走的牌張搬移。
     */
    open class DiscardEntry(
        val tile: IdentifiedTile,
        val isTaken: Boolean = false,
        val components: ComponentSet = ComponentSet.EMPTY,
    ) {
        init {
            components.requireTarget(BuiltInComponentOwners.DISCARD_ENTRY)
        }

        /**
         * 這張捨牌帶有的公開標記 ID，例如日麻的立直宣告牌；沒有標記時為空集合。
         *
         * 標記為所有玩家可見的資訊，牌譜依此保存並在回放中顯示。ID 帶命名空間，顯示名稱由平台另行登記。
         */
        open val publicMarkerIds: Set<String> get() = components.publicMarkerIds

        /**
         * 回傳一份 [isTaken] 為 true 的複本。
         *
         * 子類別若擴充了額外欄位，應覆寫此方法以保留這些欄位。
         */
        open fun withTaken(): DiscardEntry = DiscardEntry(tile, isTaken = true, components = components)

        /**
         * 保留紀錄資料，替換同張牌的組件版本；擴充紀錄必須覆寫以保留自己的欄位。
         *
         * @param replacement 與原牌 UUID、牌面一致的新版本。
         * @return 不改變取走狀態的新紀錄。
         */
        open fun withTile(replacement: IdentifiedTile): DiscardEntry {
            check(this::class == DiscardEntry::class) { "Custom discard entries must preserve their fields in withTile" }
            require(replacement.id == tile.id && replacement.tile == tile.tile) { "Replacement must preserve tile identity and face" }
            return DiscardEntry(replacement, isTaken, components)
        }

        /**
         * 保留牌張與取走狀態，替換這筆紀錄的組件；擴充紀錄必須覆寫以保留自己的欄位。
         *
         * @param replacement 這筆紀錄的完整新組件。
         * @return 只有組件不同的新紀錄。
         */
        open fun withComponents(replacement: ComponentSet): DiscardEntry {
            check(this::class == DiscardEntry::class) { "Custom discard entries must preserve their fields in withComponents" }
            return DiscardEntry(tile, isTaken, replacement)
        }
    }
}

package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftResourceIds

/**
 * 每個座位上可以擺放桌面物件的固定位置。
 *
 * 通用 layout 只知道這些位置在哪，不知道上面會放什麼；要放什麼、放幾個由規則的描述決定。
 */
enum class TableSeatAnchor {
    /** 這個座位牌河靠桌子中心那條邊的中點。 */
    DISCARD_INNER_EDGE,

    /** 這個座位的副露角落。 */
    MELD_CORNER,
}

/**
 * 相對於座位錨點的偏移，以「坐在桌子南側的玩家」為基準的本地座標（單位：方塊）。
 *
 * 實際座標由通用 layout 依座位所在的桌子側面與桌子朝向旋轉換算，描述端不需要處理旋轉。
 *
 * @property x 沿著這個座位牌河與手牌排列的方向；正向朝副露角落。
 * @property y 往上。
 * @property z 垂直於排列方向；正向朝玩家自己，負向朝桌子中心。
 */
data class TableSeatOffset(
    val x: Double = 0.0,
    val y: Double = 0.0,
    val z: Double = 0.0,
)

/**
 * 一個桌面物件的擺放描述。
 *
 * 兩次描述之間，種類、變體、座位、錨點、偏移與轉角都相同的物件視為同一個：已經在桌上的會保留，不會重新
 * 生成或重播出現動畫。
 *
 * @property kind 物件種類的命名空間識別碼，由平台端的物件種類登錄處決定實際生成的 entity。
 * @property variant 種類內的變體，例如點棒的面額；意義由該種類自行解讀。
 * @property seatIndex 所屬座位，即 `TableState.players` 的 index。
 * @property anchor 擺放的基準位置。
 * @property offset 相對於 [anchor] 的偏移。
 * @property yawOffset 在座位原本朝向之上額外轉動的角度（度）。
 */
data class TablePropPlacement(
    val kind: String,
    val variant: String,
    val seatIndex: Int,
    val anchor: TableSeatAnchor,
    val offset: TableSeatOffset = TableSeatOffset(),
    val yawOffset: Float = 0f,
) {
    init {
        MinecraftResourceIds.requireValid(kind) { "Table prop kind must be namespaced: $kind" }
        require(seatIndex >= 0) { "Table prop seat index must not be negative: $seatIndex" }
    }
}

/**
 * 描述一種規則此刻在桌上要擺哪些物件。
 *
 * 只描述、不生成：實際的 entity 由平台的通用執行器依描述建立、保留或移除。每次呼叫都回傳完整清單，
 * 沒列出的物件會被移除。
 */
fun interface TablePropDescriber {
    /**
     * 依目前的權威桌況描述桌上應有的物件。
     *
     * @param tableState 目前的權威桌況。
     * @return 桌上應有的全部物件；沒有任何物件時為空清單。
     */
    fun describe(tableState: TableState): List<TablePropPlacement>
}

/** 依規則模組識別碼登記 [TablePropDescriber]；沒有登記的規則，桌上不會擺任何由規則描述的物件。 */
interface TablePropDescriberRegistry {
    /** 目前已登記的規則模組識別碼快照。 */
    val registrationKeys: Set<String>

    /** registry 是否已凍結。 */
    val isFrozen: Boolean

    /** 登記一個規則模組的桌面物件描述。 */
    fun register(ruleModuleId: String, describer: TablePropDescriber)

    /** 查詢指定規則模組的描述；未登記時回傳 null。 */
    fun find(ruleModuleId: String): TablePropDescriber?

    /** 凍結 registry，禁止後續登記。 */
    fun freeze()
}

/** [TablePropDescriberRegistry] 的記憶體實作。 */
class TablePropDescriberRegistryImpl : TablePropDescriberRegistry {
    /** 依規則模組識別碼索引的描述。 */
    private val describers = mutableMapOf<String, TablePropDescriber>()

    override val registrationKeys: Set<String> get() = describers.keys.toSet()

    override var isFrozen: Boolean = false
        private set

    override fun register(ruleModuleId: String, describer: TablePropDescriber) {
        check(!isFrozen) { "Table prop describer registry is frozen" }
        require(ruleModuleId.isNotBlank()) { "Rule module ID must not be blank" }
        require(describers.putIfAbsent(ruleModuleId, describer) == null) { "Duplicate table prop describer: $ruleModuleId" }
    }

    override fun find(ruleModuleId: String): TablePropDescriber? = describers[ruleModuleId]

    override fun freeze() {
        isFrozen = true
    }
}

/** 內建的桌面物件種類識別碼。 */
object BuiltInTablePropKinds {
    /** 點棒；變體為面額的點數字串，例如 `"1000"`。 */
    const val SCORING_STICK: String = "mahjongcraft:scoring_stick"
}

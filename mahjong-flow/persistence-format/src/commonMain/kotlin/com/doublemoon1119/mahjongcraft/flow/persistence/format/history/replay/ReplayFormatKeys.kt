package com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay

/** 精簡 Replay 文件與內部資料樹共用的欄位名稱。 */
internal object ReplayFormatKeys {
    /** 文件格式版本欄位。 */
    const val FORMAT_VERSION = "formatVersion"

    /** 字典化資料封套欄位。 */
    const val PAYLOAD = "payload"

    /** 字典化資料樹中的欄位名稱字典。 */
    const val KEY_DICTIONARY = "k"

    /** 字典化資料樹中的字串值字典。 */
    const val STRING_DICTIONARY = "s"

    /** 字典化資料樹中的實際內容。 */
    const val DATA = "d"

    /** 內部文件版本欄位。 */
    const val VERSION = "version"

    /** 對局共用資料。 */
    const val HEADER = "header"

    /** 對局識別碼。 */
    const val MATCH = "match"

    /** 牌桌識別碼。 */
    const val TABLE = "table"

    /** 對局玩家清單。 */
    const val PLAYERS = "players"

    /** 對局規則設定。 */
    const val RULE = "rule"

    /** 流程設定。 */
    const val FLOW = "flow"

    /** 對局起始時間。 */
    const val TIME = "time"

    /** 玩家 AI 策略。 */
    const val PLAYER_AI = "ai"

    /** 各局資料。 */
    const val ROUNDS = "rounds"

    /** 局內交易資料。 */
    const val TRANSACTIONS = "transactions"

    /** 局序號。 */
    const val ROUND_NUMBER = "n"

    /** 交易相對時間。 */
    const val TIME_DELTA = "dt"

    /** 交易中的開局標記。 */
    const val ROUND_OPENING = "h"

    /** 交易中新宣告的牌種。 */
    const val NEW_TILES = "new"

    /** 交易中的語意事實。 */
    const val FACTS = "e"

    /** 交易中各事實的行為者座位。 */
    const val ACTORS = "a"

    /** 交易後的桌況差異。 */
    const val PATCH = "p"

    /** 局開始時的桌況投影。 */
    const val INITIAL = "initial"

    /** 局內牌種索引表。 */
    const val TILES = "tiles"

    /** 跨局牌種字典。 */
    const val TYPES = "types"

    /** 語意事實種類字典。 */
    const val FACT_TYPES = "factTypes"

    /** 動作種類字典。 */
    const val ACTION_TYPES = "actionTypes"

    /** 差異路徑字典。 */
    const val PATCH_PATHS = "patchPaths"
}

/** 桌況差異樹使用的操作名稱。 */
internal object ReplayPatchKeys {
    /** 物件欄位差異。 */
    const val OBJECT = "_o"

    /** 依位置更新的列表差異。 */
    const val LIST = "_l"

    /** 值替換。 */
    const val VALUE = "_v"

    /** 欄位刪除。 */
    const val DELETE = "_x"

    /** 列表片段替換。 */
    const val SPLICE = "_a"
}

/** 從權威歷史與桌況 DTO 讀取時使用的既有欄位名稱。 */
internal object ReplaySourceKeys {
    /** 事件中的語意事實。 */
    const val FACT = "fact"

    /** 開局桌況。 */
    const val STATE = "state"

    /** 開局流程設定。 */
    const val FLOW_CONFIG = "flowConfig"

    /** 語意事實或動作的種類。 */
    const val TYPE = "type"

    /** 已接受的遊戲動作。 */
    const val ACTION = "action"

    /** 已接受動作的結果。 */
    const val RESULT = "result"

    /** 動作結果直接涉及的牌。 */
    const val AFFECTED_TILE_IDS = "affectedTileIds"

    /** 動作結果新公開的牌。 */
    const val NEWLY_REVEALED_TILE_IDS = "newlyRevealedTileIds"

    /** 精簡事實中的新公開牌。 */
    const val REVEALED_TILES = "revealedTiles"

    /** 玩家或牌的識別碼。 */
    const val ID = "id"

    /** 擴充資料的類型識別碼。 */
    const val TYPE_KEY = "typeKey"

    /** 牌的序列化資料。 */
    const val TILE = "tile"

    /** 桌況規則設定。 */
    const val CONFIG = "config"

    /** 桌況的實體牌牆布局。 */
    const val PHYSICAL_WALL_LAYOUT = "physicalWallLayout"

    /** 玩家初始座位。 */
    const val INITIAL_SEAT_INDEX = "initialSeatIndex"

    /** 玩家 AI 策略識別碼。 */
    const val AI_STRATEGY_KEY = "aiStrategyKey"

    /** 玩家獨立動作紀錄。 */
    const val ACTION_HISTORY = "actionHistory"

    /** 動作直接涉及的牌。 */
    const val DIRECT_TILES = "directTiles"

    /** 已接受動作的序列化種類識別碼。 */
    const val ACTION_ACCEPTED = "action_accepted"

    /** 內建規則資料的類型前綴。 */
    const val BUILTIN_TYPE_PREFIX = "builtin:"
}

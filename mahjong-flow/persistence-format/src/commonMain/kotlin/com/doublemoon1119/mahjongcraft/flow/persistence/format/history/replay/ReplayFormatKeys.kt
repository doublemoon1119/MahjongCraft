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

    /** 場地識別碼。 */
    const val VENUE = "venue"

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

    /** 胡牌詳情中的手牌描述。 */
    const val WINNING_HAND = "hand"

    /** 胡牌詳情中的有序立牌參照。 */
    const val WINNING_STANDING_TILES = "standingTileIds"

    /** 胡牌詳情中的獨立和牌參照。 */
    const val WINNING_TILE = "winningTileId"

    /** 玩家或牌的識別碼。 */
    const val ID = "id"

    /** 結算明細中的玩家識別碼。 */
    const val PLAYER_ID = "playerId"

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

    /** 玩家目前座位。 */
    const val CURRENT_PLAYER_INDEX = "currentPlayerIndex"

    /** 莊家玩家座位。 */
    const val DEALER_PLAYER_ID = "dealerPlayerId"

    /** 局內風圈與局序位置。 */
    const val ROUND_POSITION = "roundPosition"

    /** 場風。 */
    const val PREVALENT_WIND = "prevalentWind"

    /** 活牌區。 */
    const val TILE_WALL = "tileWall"

    /** 牌牆中的牌。 */
    const val WALL_TILES = "tiles"

    /** 開局保留牌區。 */
    const val INITIAL_DEAD_WALL = "initialDeadWall"

    /** 本場數。 */
    const val COMBO_COUNT = "comboCount"

    /** 已完成玩家座位。 */
    const val FINISHED_PLAYER_IDS = "finishedPlayerIds"

    /** 規則的動態桌況。 */
    const val DYNAMIC_RULE_STATE = "dynamicRuleState"

    /** 目前等待中的一般反應。 */
    const val PENDING_REACTION = "pendingReaction"

    /** 目前等待中的槓牌反應。 */
    const val PENDING_KAN_REACTION = "pendingKanReaction"

    /** 動作使用的牌。 */
    const val TILE_ID = "tileId"

    /** 動作搭配的牌。 */
    const val WITH_TILE_IDS = "withTileIds"

    /** 擴充動作的值。 */
    const val VALUE = "value"

    /** 反應裁定後套用的動作。 */
    const val RESOLVED_ACTION = "resolvedAction"

    /** 反應裁定的行為者座位。 */
    const val ACTOR_PLAYER_ID = "actorPlayerId"

    /** 開局準備步驟識別碼。 */
    const val STEP_ID = "stepId"

    /** 開局準備步驟索引。 */
    const val STEP_INDEX = "stepIndex"

    /** 下一個開局準備步驟識別碼。 */
    const val NEXT_STEP_ID = "nextStepId"

    /** 局完成摘要。 */
    const val SUMMARY = "summary"

    /** 完成或效果原因識別碼。 */
    const val REASON_ID = "reasonId"

    /** 結算結果識別碼。 */
    const val OUTCOME_ID = "outcomeId"

    /** 局完成結果。 */
    const val ROUND_COMPLETION = "roundCompletion"

    /** 最終玩家分數。 */
    const val FINAL_SCORES_BY_PLAYER_ID = "finalScoresByPlayerId"

    /** 結算後玩家分數。 */
    const val SETTLED_SCORES_BY_PLAYER_ID = "settledScoresByPlayerId"

    /** 結算受益玩家。 */
    const val BENEFICIARY_PLAYER_IDS = "beneficiaryPlayerIds"

    /** 結算責任玩家。 */
    const val RESPONSIBLE_PLAYER_IDS = "responsiblePlayerIds"

    /** 結算分類。 */
    const val CLASSIFICATION = "classification"

    /** 局流程轉移指示。 */
    const val TRANSITION_DIRECTIVE = "transitionDirective"

    /** 玩家手牌封套。 */
    const val HAND = "hand"

    /** 手牌中的牌。 */
    const val HAND_TILES = "tiles"

    /** 手牌中最後摸入的牌。 */
    const val LAST_DRAWN = "lastDrawn"

    /** 玩家副露清單。 */
    const val MELDS = "melds"

    /** 玩家分數。 */
    const val SCORE = "score"

    /** 玩家座風。 */
    const val SEAT_WIND = "seatWind"

    /** 玩家規則狀態。 */
    const val PLAYER_RULE_STATE = "playerRuleState"

    /** 玩家牌河資料。 */
    const val DISCARD_PILE = "discardPile"

    /** 副露中的牌。 */
    const val MELD_TILES = "tiles"

    /** 副露來源牌。 */
    const val SOURCE_TILE = "sourceTile"

    /** 副露來源方向。 */
    const val SOURCE_DIRECTION = "sourceDirection"

    /** 結算明細清單。 */
    const val DETAIL_FIELDS = "detailFields"

    /** 胡牌明細封套。 */
    const val WIN_DETAILS = "winDetails"

    /** 明細有單位數值清單。 */
    const val QUANTITIES = "quantities"

    /** 條目附帶的有單位數值。 */
    const val QUANTITY = "quantity"

    /** 有單位數值的單位 ID。 */
    const val UNIT_ID = "unitId"

    /** 有單位數值的數值。 */
    const val AMOUNT = "amount"

    /** 明細牌參照陣列。 */
    const val TILE_IDS = "tileIds"

    /** 明細條目清單。 */
    const val ENTRIES = "entries"

    /** 動作中的擴充動作資料。 */
    const val EXTENSION = "extension"

    /** 內建規則資料的類型前綴。 */
    const val BUILTIN_TYPE_PREFIX = "builtin:"
}

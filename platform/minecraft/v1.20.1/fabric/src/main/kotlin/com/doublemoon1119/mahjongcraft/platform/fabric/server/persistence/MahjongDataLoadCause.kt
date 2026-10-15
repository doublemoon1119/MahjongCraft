package com.doublemoon1119.mahjongcraft.platform.fabric.server.persistence

import com.doublemoon1119.mahjongcraft.flow.persistence.format.core.UnregisteredPersistenceTypeException
import com.doublemoon1119.mahjongcraft.flow.persistence.format.migration.UnsupportedPersistenceSchemaVersionException

/** 讀取失敗的原因分類；只有能從例外確定的情況才給出明確原因，其餘列為無法判斷。 */
sealed interface MahjongDataLoadCause {
    /**
     * 存檔中的型別沒有已登記的轉換器，例如提供它的擴充已被移除。
     *
     * @property typeKey 存檔中找不到轉換器的型別識別碼。
     */
    data class MissingExtension(val typeKey: String) : MahjongDataLoadCause {
        /** 型別識別碼的命名空間，通常是提供該型別的擴充 ID；沒有命名空間時為整個識別碼。 */
        val namespace: String get() = typeKey.substringBefore(':')
    }

    /**
     * 資料由較新版本的 MahjongCraft 保存，目前版本不支援。
     *
     * @property savedVersion 資料宣告的格式版本。
     * @property supportedVersion 目前程式支援的格式版本。
     */
    data class NewerFormat(val savedVersion: Int, val supportedVersion: Int) : MahjongDataLoadCause

    /**
     * 無法從例外判斷原因，可能是不同版本的資料、檔案損壞或缺少擴充。
     *
     * @property summary 不含存檔內容的錯誤摘要。
     */
    data class Unreadable(val summary: String) : MahjongDataLoadCause
}

/**
 * 沿著例外的 cause 鏈判斷讀取失敗的原因。
 *
 * @param failure 讀取或解碼時拋出的例外。
 * @return 能確定時為具體原因，否則為帶錯誤摘要的 [MahjongDataLoadCause.Unreadable]。
 */
internal fun classifyDataLoadFailure(failure: Throwable): MahjongDataLoadCause {
    generateSequence(failure) { it.cause.takeIf { cause -> cause !== it } }
        .take(MAX_CAUSE_DEPTH)
        .forEach { cause ->
            when (cause) {
                is UnregisteredPersistenceTypeException -> return MahjongDataLoadCause.MissingExtension(cause.typeKey)
                is UnsupportedPersistenceSchemaVersionException -> return MahjongDataLoadCause.NewerFormat(cause.version, cause.currentVersion)
            }
        }
    return MahjongDataLoadCause.Unreadable(failureSummary(failure))
}

/**
 * 建立不含存檔內容的單行錯誤摘要。
 *
 * @param failure 要摘要的例外。
 * @return 例外類別名稱與截斷後的訊息。
 */
internal fun failureSummary(failure: Throwable): String {
    val name = failure::class.simpleName ?: "Exception"
    val message = failure.message?.lineSequence()?.firstOrNull()?.take(MAX_SUMMARY_MESSAGE_LENGTH)
    return if (message.isNullOrBlank()) name else "$name: $message"
}

/** 尋找可辨識原因時最多走訪的 cause 層數，避免異常的循環鏈。 */
private const val MAX_CAUSE_DEPTH = 16

/** 錯誤摘要保留的最大訊息長度。 */
private const val MAX_SUMMARY_MESSAGE_LENGTH = 200

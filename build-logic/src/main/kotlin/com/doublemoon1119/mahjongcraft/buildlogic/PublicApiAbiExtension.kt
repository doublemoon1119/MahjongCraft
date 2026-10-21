package com.doublemoon1119.mahjongcraft.buildlogic

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import javax.inject.Inject

/**
 * 指定此模組要承諾二進位相容的公開型別名稱。
 *
 * 名稱使用 Kotlin ABI filter 的完整名稱或 glob pattern。空清單會在 ABI 任務執行時報錯，
 * 避免模組套用了外掛卻沒有實際檢查任何公開介面。
 *
 * @param objects Gradle 建立清單屬性的工廠。
 */
abstract class PublicApiAbiExtension @Inject constructor(
    objects: ObjectFactory,
) {
    /** ABI filter 要納入的完整名稱或 glob pattern。 */
    val includedNames: ListProperty<String> = objects.listProperty(String::class.java).convention(emptyList())
}

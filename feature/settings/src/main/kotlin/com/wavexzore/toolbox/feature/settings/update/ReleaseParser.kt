package com.wavexzore.toolbox.feature.settings.update

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** GitHub release 解析结果（进程内模型；T-304 / 设计 04 §6.1）。 */
data class ReleaseInfo(
    val tag: String,
    val notes: String,
    val publishedAt: String?,
    val htmlUrl: String,
    val asset: AssetRef?,
)

/** 发布资产引用；`digest` 形如 `sha256:<64hex>`，null=未提供摘要。 */
data class AssetRef(
    val name: String,
    val url: String,
    val size: Long,
    val digest: String?,
)

/**
 * `/releases/latest` 响应解析（T-304；设计 04 §5.4）。
 *
 * - 防御性：非法 JSON / tag 缺失或非 semver → null（调用方归 Error(Parse)）；
 * - 资产：精确名 `wave-toolbox-v<tag>.apk` 优先 → 首个 `.apk` → 无则为 null（可更新但不可下载）；
 * - **digest 归一口**：API digest 合法优先 → 正文 `SHA-256: <hex>` 兜底 → 均无 null；
 * - notes 截断（码点安全）。
 */
object ReleaseParser {

    private val DIGEST_RE = Regex("sha256:[0-9a-f]{64}")
    private val BODY_SHA_RE = Regex("(?im)^\\s*sha-256\\s*[:：]\\s*([0-9a-fA-F]{64})\\s*$")

    /** 解析成功返回 [ReleaseInfo]（asset 可空）；失败返回 null。 */
    fun parse(body: String): ReleaseInfo? {
        val root = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val tag = root["tag_name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (tag.isEmpty() || SemverCompare.parse(tag) == null) return null

        val rawNotes = root["body"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val publishedAt = root["published_at"]?.jsonPrimitive?.contentOrNull
        val htmlUrl = root["html_url"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: UpdateConfig.RELEASES_PAGE

        val assets = (root["assets"] as? JsonArray).orEmpty().mapNotNull(::parseAsset)
        val picked = assets.firstOrNull { it.name.equals("${UpdateConfig.ASSET_NAME_PREFIX}$tag.apk", ignoreCase = true) }
            ?: assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }

        val asset = picked?.copy(digest = normalizeDigest(picked.digest, rawNotes))
        return ReleaseInfo(
            tag = tag,
            notes = truncateNotes(rawNotes),
            publishedAt = publishedAt,
            htmlUrl = htmlUrl,
            asset = asset,
        )
    }

    private fun parseAsset(el: kotlinx.serialization.json.JsonElement): AssetRef? {
        val obj = el as? JsonObject ?: return null
        val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return null
        val url = obj["browser_download_url"]?.jsonPrimitive?.contentOrNull ?: return null
        val size = obj["size"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
        val digest = obj["digest"]?.jsonPrimitive?.contentOrNull
        return AssetRef(name = name, url = url, size = size, digest = digest)
    }

    /** digest 归一：API 合法值 → 正文兜底 → null（下游只认该字段）。 */
    private fun normalizeDigest(apiDigest: String?, body: String): String? {
        apiDigest?.trim()?.lowercase()?.takeIf { DIGEST_RE.matches(it) }?.let { return it }
        return BODY_SHA_RE.find(body)?.groupValues?.get(1)?.lowercase()?.let { "sha256:$it" }
    }

    /** 截断到 [UpdateConfig.NOTES_MAX_CHARS]，不劈代理对，尾部补省略号。 */
    private fun truncateNotes(notes: String): String {
        if (notes.length <= UpdateConfig.NOTES_MAX_CHARS) return notes
        var end = UpdateConfig.NOTES_MAX_CHARS
        while (end > 0 && Character.isHighSurrogate(notes[end - 1])) end--
        return notes.substring(0, end).trimEnd() + "…"
    }
}

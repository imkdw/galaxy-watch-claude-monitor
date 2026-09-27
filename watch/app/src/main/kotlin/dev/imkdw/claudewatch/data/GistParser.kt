package dev.imkdw.claudewatch.data

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

class GistFormatException(message: String) : Exception(message)

object GistParser {
    private val usageFileName = Regex("^usage-(.+)\\.json$")

    fun parse(body: String): Map<String, UsageFile> {
        val root = try {
            UsageJson.parseToJsonElement(body) as? JsonObject
        } catch (_: SerializationException) {
            null
        } ?: throw GistFormatException("Gist 응답이 JSON 객체가 아님")
        val files = root["files"] as? JsonObject ?: throw GistFormatException("Gist 응답에 files가 없음")

        val result = linkedMapOf<String, UsageFile>()
        for ((name, entry) in files) {
            val label = usageFileName.matchEntire(name)?.groupValues?.get(1) ?: continue
            val content = ((entry as? JsonObject)?.get("content") as? JsonPrimitive)?.contentOrNull ?: continue
            parseFile(content)?.let { result[label] = it }
        }
        return result
    }

    fun parseFile(content: String): UsageFile? = try {
        UsageJson.decodeFromString(UsageFile.serializer(), content)
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}

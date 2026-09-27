package dev.imkdw.claudewatch.data

import com.google.common.truth.Truth.assertThat
import dev.imkdw.claudewatch.testing.Fixtures
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant

class GistParserTest {

    private fun gist(vararg files: Pair<String, String>): String = buildJsonObject {
        put("id", "g")
        putJsonObject("files") {
            for ((name, content) in files) {
                putJsonObject(name) {
                    put("filename", name)
                    put("content", content)
                }
            }
        }
    }.toString()

    @Test
    fun `P1 usage 파일만 계정으로 읽고 무관한 파일은 뺀다`() {
        val accounts = GistParser.parse(Fixtures.text("gist-response.json"))
        assertThat(accounts.keys).containsExactly("personal", "work")
        assertThat(accounts.getValue("work").session?.pct).isEqualTo(10)
    }

    @Test
    fun `P2 계약 - usage-personal json의 모든 필드가 맞다`() {
        val file = GistParser.parseFile(Fixtures.text("usage-personal.json"))
        assertThat(file).isEqualTo(
            UsageFile(
                account = "personal",
                session = Window(22, "2026-09-27T14:20:00Z"),
                weekly = Window(44, "2026-09-29T11:00:00Z"),
                weeklyByModel = listOf(ModelPct("Fable", 0)),
                changedAt = "2026-09-27T12:05:00Z",
                status = "ok",
                source = "oauth-usage-v1",
            ),
        )
    }

    @Test
    fun `P3 알 수 없는 필드가 있어도 파싱된다`() {
        val original = Json.parseToJsonElement(Fixtures.text("usage-personal.json")) as JsonObject
        val extended = JsonObject(original + ("samples" to JsonPrimitive("new")) + ("extra" to JsonPrimitive(1)))
        assertThat(GistParser.parseFile(extended.toString())?.account).isEqualTo("personal")
    }

    @Test
    fun `P4 깨진 파일 하나는 건너뛰고 나머지를 돌려준다`() {
        val body = gist(
            "usage-personal.json" to Fixtures.text("usage-personal.json"),
            "usage-broken.json" to "{not json",
            "usage-empty.json" to "{}",
        )
        assertThat(GistParser.parse(body).keys).containsExactly("personal")
    }

    @Test
    fun `P5 session이 null이면 null`() {
        val content = """{"account":"a","session":null,"weekly":null,"changedAt":"2026-09-27T12:05:00Z","status":"auth_error"}"""
        val file = GistParser.parseFile(content)!!
        assertThat(file.session).isNull()
        assertThat(file.weeklyByModel).isEmpty()
        assertThat(file.status).isEqualTo("auth_error")
    }

    @Test
    fun `P6 원본 resets_at 형식도 Instant로 바뀐다`() {
        assertThat(parseInstant("2026-09-27T14:20:00.292126+00:00")).isEqualTo(Instant.parse("2026-09-27T14:20:00.292126Z"))
        assertThat(parseInstant("2026-09-27T23:20:00+09:00")).isEqualTo(Instant.parse("2026-09-27T14:20:00Z"))
        assertThat(parseInstant("2026-09-27T14:20:00Z")).isEqualTo(Instant.parse("2026-09-27T14:20:00Z"))
        assertThat(parseInstant("nope")).isNull()
    }

    @Test
    fun `파일 이름의 라벨이 키가 된다`() {
        val body = gist("usage-side-project.json" to Fixtures.text("usage-personal.json"))
        assertThat(GistParser.parse(body).keys).containsExactly("side-project")
    }

    @Test
    fun `Gist 응답 자체가 깨지면 예외`() {
        assertThrows(GistFormatException::class.java) { GistParser.parse("<html>") }
        assertThrows(GistFormatException::class.java) { GistParser.parse("""{"id":"x"}""") }
    }
}

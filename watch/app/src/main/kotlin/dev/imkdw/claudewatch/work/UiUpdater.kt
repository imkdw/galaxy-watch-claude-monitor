package dev.imkdw.claudewatch.work

import android.content.Context

/** 조회나 계정 변경 뒤 타일과 컴플리케이션에 다시 그려 달라고 요청한다 (R5) */
fun interface UiUpdater {
    fun requestUpdate()
}

class SystemUiUpdater(private val context: Context) : UiUpdater {
    override fun requestUpdate() {
    }
}

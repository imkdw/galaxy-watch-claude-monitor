package dev.imkdw.claudewatch

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/** 계획 3.4: Robolectric이 minSdk 36을 돌릴 수 있는지 먼저 확인 */
@RunWith(AndroidJUnit4::class)
class RobolectricSdkTest {
    @Test
    fun runsOnSdk36() {
        assertThat(Build.VERSION.SDK_INT).isEqualTo(36)
    }
}

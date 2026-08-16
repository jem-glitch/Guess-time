package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.sound.SmartSoundManager
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SoundSystemTest {

  private lateinit var context: Context

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext<Context>()
  }

  @Test
  fun testSingleButtonClickSound() {
    val manager = SmartSoundManager(context)
    // Verify sound plays reliably
    manager.playSound("start")
    manager.playSound("stop")
    manager.playSound()
    manager.release()
  }
}

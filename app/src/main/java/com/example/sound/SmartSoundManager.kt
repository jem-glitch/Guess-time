package com.example.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Loud, Crisp, Satisfying & Premium Mechanical Button Click Synthesizer.
 * Engineered for maximum audibility and acoustic warmth without harsh high-frequency distortion.
 */
class SmartSoundManager(private val context: Context) {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  var webViewRef: WebView? = null

  private val sampleRate = 44100

  // Pre-rendered premium physical button click samples (Extremely loud, punchy, and clear)
  private val buttonClickSamples: ShortArray by lazy {
    synthesizeClearPunchyClick()
  }

  /**
   * Synthesizes an exceptionally clear, loud, satisfying button press sound:
   * 1. Warm transient attack (1800Hz -> 520Hz) - highly audible, non-piercing
   * 2. Heavy physical tactile bottom-out punch (320Hz -> 110Hz body drop)
   * 3. Acoustic chamber resonance (580Hz) providing body and real-world fullness
   * 4. Soft-clipping saturation (tanh) giving max loudness and presence across phone speakers
   */
  private fun synthesizeClearPunchyClick(): ShortArray {
    val durationSec = 0.085 // 85ms full acoustic presence
    val numSamples = (sampleRate * durationSec).toInt()
    val samples = ShortArray(numSamples)

    for (i in 0 until numSamples) {
      val t = i.toDouble() / sampleRate
      val progress = t / durationSec

      // 1. Crisp initial tactile click transient (Clean, pleasant, highly audible)
      val clickFreq = 1850.0 * exp(-progress * 28.0) + 520.0
      val clickEnvelope = exp(-progress * 22.0)
      val click = sin(2.0 * PI * clickFreq * t) * clickEnvelope * 0.75

      // 2. Strong mechanical body impact (The punchy "clack" feeling)
      val bodyFreq = 340.0 * exp(-progress * 18.0) + 115.0
      val bodyEnvelope = exp(-progress * 14.0)
      val body = sin(2.0 * PI * bodyFreq * t) * bodyEnvelope * 0.95

      // 3. Acoustic housing air resonance (Solid chamber sound)
      val resFreq = 580.0
      val resEnvelope = exp(-progress * 16.0)
      val res = sin(2.0 * PI * resFreq * t) * resEnvelope * 0.45

      // 4. Attack ramp for zero clicks at onset
      val attack = if (t < 0.0012) t / 0.0012 else 1.0

      // Combine acoustic layers
      val rawMix = (click + body + res) * attack

      // Soft-clip saturation: maximizes speaker acoustic energy and loudness cleanly
      val saturated = tanh(rawMix * 1.6)

      // Master output amplitude
      val amp = (saturated * 32600.0).toInt().coerceIn(-32767, 32767)
      samples[i] = amp.toShort()
    }
    return samples
  }

  @JavascriptInterface
  fun playSound(type: String? = null) {
    scope.launch(Dispatchers.Default) {
      try {
        playPcmDirect(buttonClickSamples)
      } catch (e: Exception) {
        Log.e("SmartSoundManager", "Error playing realistic button sound", e)
      }
    }
  }

  private suspend fun playPcmDirect(samples: ShortArray) {
    var track: AudioTrack? = null
    try {
      val bufferSize = samples.size * 2
      val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

      val audioFormat = AudioFormat.Builder()
        .setSampleRate(sampleRate)
        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
        .build()

      track = AudioTrack(
        audioAttributes,
        audioFormat,
        bufferSize,
        AudioTrack.MODE_STATIC,
        AudioManager.AUDIO_SESSION_ID_GENERATE
      )

      track.write(samples, 0, samples.size)
      track.setVolume(1.0f)
      track.play()

      val durationMs = (samples.size.toDouble() / sampleRate * 1000).toLong() + 10
      delay(durationMs)
    } catch (e: Exception) {
      Log.e("SmartSoundManager", "AudioTrack play error", e)
    } finally {
      try {
        track?.stop()
        track?.release()
      } catch (_: Exception) {}
    }
  }

  fun release() {
    // Cleanup if needed
  }
}

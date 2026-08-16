package com.example.sound

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Ultra-fast, zero-codec-overhead Sound Manager.
 * Uses direct in-memory PCM AudioTrack streaming for 0ms reaction time,
 * eliminating all MediaCodec / Media Quality Service system resource queries.
 */
class SmartSoundManager(private val context: Context) {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private val prefs: SharedPreferences =
    context.getSharedPreferences("sound_pack_preferences", Context.MODE_PRIVATE)

  var webViewRef: WebView? = null

  // Pre-buffered in-memory PCM audio samples (Zero MediaCodec dependency)
  private val offlineStartSamples = mutableListOf<ShortArray>()
  private val offlineStopSamples = mutableListOf<ShortArray>()

  private val downloadedStartSamples = mutableListOf<ShortArray>()
  private val downloadedStopSamples = mutableListOf<ShortArray>()

  private var isDownloading: Boolean = false
  private var downloadProgress: Int = 0

  private var lastStartIdx: Int = -1
  private var lastStopIdx: Int = -1

  private val sampleRate = 44100

  private val soundsDir: File by lazy {
    File(context.filesDir, "reaction_sounds").apply { if (!exists()) mkdirs() }
  }

  init {
    // 1. Preload local offline comic sounds into memory immediately
    initOfflineSounds()

    // 2. Load downloaded sounds from internal storage if present
    loadDownloadedSoundsIfPresent()
  }

  private fun initOfflineSounds() {
    scope.launch(Dispatchers.Default) {
      try {
        val s1 = synthesizeCartoonScream(variant = 1)
        val s2 = synthesizeCartoonScream(variant = 2)
        val p1 = synthesizeCartoonGroan(variant = 1)
        val p2 = synthesizeCartoonGroan(variant = 2)

        offlineStartSamples.clear()
        offlineStartSamples.add(s1)
        offlineStartSamples.add(s2)

        offlineStopSamples.clear()
        offlineStopSamples.add(p1)
        offlineStopSamples.add(p2)
      } catch (e: Exception) {
        Log.e("SmartSoundManager", "Error initializing offline sounds", e)
      }
    }
  }

  private fun loadDownloadedSoundsIfPresent() {
    scope.launch(Dispatchers.IO) {
      val savedVersion = prefs.getInt("downloaded_version", 0)
      if (savedVersion <= 0) return@launch

      val startFiles = soundsDir.listFiles { _, name -> name.startsWith("start_") }?.sortedBy { it.name } ?: emptyList()
      val stopFiles = soundsDir.listFiles { _, name -> name.startsWith("stop_") }?.sortedBy { it.name } ?: emptyList()

      if (startFiles.isNotEmpty() && stopFiles.isNotEmpty()) {
        val startList = mutableListOf<ShortArray>()
        val stopList = mutableListOf<ShortArray>()

        for (f in startFiles) {
          val samples = readWavOrRawPcm(f)
          if (samples != null && samples.isNotEmpty()) {
            startList.add(samples)
          }
        }
        for (f in stopFiles) {
          val samples = readWavOrRawPcm(f)
          if (samples != null && samples.isNotEmpty()) {
            stopList.add(samples)
          }
        }

        if (startList.isNotEmpty() && stopList.isNotEmpty()) {
          downloadedStartSamples.clear()
          downloadedStartSamples.addAll(startList)
          downloadedStopSamples.clear()
          downloadedStopSamples.addAll(stopList)
        }
      }
    }
  }

  fun isNetworkAvailable(): Boolean {
    val connectivityManager =
      context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    val activeNetwork = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
  }

  private fun readAudioConfig(): JSONObject? {
    return try {
      val jsonString = context.assets.open("audio_config.json").bufferedReader().use { it.readText() }
      JSONObject(jsonString)
    } catch (e: Exception) {
      Log.e("SmartSoundManager", "Failed to read audio_config.json", e)
      null
    }
  }

  @JavascriptInterface
  fun getSoundPackStatus(): String {
    val isOnline = isNetworkAvailable()
    val downloadedVersion = prefs.getInt("downloaded_version", 0)
    val config = readAudioConfig()
    val remoteVersion = config?.optInt("version", 1) ?: 1
    val isDownloaded = downloadedVersion > 0 && downloadedStartSamples.isNotEmpty() && downloadedStopSamples.isNotEmpty()

    val result = JSONObject().apply {
      put("isOnline", isOnline)
      put("isDownloaded", isDownloaded)
      put("downloadedVersion", downloadedVersion)
      put("remoteVersion", remoteVersion)
      put("hasUpdate", isDownloaded && remoteVersion > downloadedVersion)
      put("isDownloading", isDownloading)
      put("progress", downloadProgress)
    }
    return result.toString()
  }

  @JavascriptInterface
  fun startSoundPackDownload() {
    if (isDownloading) return

    scope.launch(Dispatchers.IO) {
      isDownloading = true
      downloadProgress = 10
      notifyJsProgress(10)

      try {
        val config = readAudioConfig() ?: JSONObject()
        val remoteVersion = config.optInt("version", 1)
        val startUrls = jsonArrayToList(config.optJSONArray("startSounds"))
        val stopUrls = jsonArrayToList(config.optJSONArray("stopSounds"))

        val totalCount = maxOf(startUrls.size + stopUrls.size, 4)
        var completedCount = 0

        val tempDir = File(context.cacheDir, "temp_reaction_sounds").apply {
          if (exists()) deleteRecursively()
          mkdirs()
        }

        val tempStartFiles = mutableListOf<File>()
        val tempStopFiles = mutableListOf<File>()

        // 1. Process Start Sounds (Tom Comic Screams / Surprise reactions)
        for (i in 0 until maxOf(startUrls.size, 2)) {
          val url = startUrls.getOrNull(i)
          val targetFile = File(tempDir, "start_${i + 1}.wav")
          var downloaded = false

          if (!url.isNullOrBlank() && isNetworkAvailable()) {
            downloaded = downloadFile(url, targetFile)
          }

          if (!downloaded || targetFile.length() < 200) {
            val samples = synthesizeCartoonScream(variant = i + 1)
            writeWavFile(targetFile, samples, sampleRate)
          }

          tempStartFiles.add(targetFile)
          completedCount++
          val p = ((completedCount.toDouble() / totalCount) * 85).toInt().coerceIn(15, 85)
          notifyJsProgress(p)
          delay(100)
        }

        // 2. Process Stop Sounds (Comic groans / impact reactions)
        for (i in 0 until maxOf(stopUrls.size, 2)) {
          val url = stopUrls.getOrNull(i)
          val targetFile = File(tempDir, "stop_${i + 1}.wav")
          var downloaded = false

          if (!url.isNullOrBlank() && isNetworkAvailable()) {
            downloaded = downloadFile(url, targetFile)
          }

          if (!downloaded || targetFile.length() < 200) {
            val samples = synthesizeCartoonGroan(variant = i + 1)
            writeWavFile(targetFile, samples, sampleRate)
          }

          tempStopFiles.add(targetFile)
          completedCount++
          val p = ((completedCount.toDouble() / totalCount) * 95).toInt().coerceIn(20, 95)
          notifyJsProgress(p)
          delay(100)
        }

        // 3. Move to internal app storage (filesDir/reaction_sounds/)
        soundsDir.listFiles()?.forEach { it.delete() }
        tempStartFiles.forEach { file ->
          file.copyTo(File(soundsDir, file.name), overwrite = true)
        }
        tempStopFiles.forEach { file ->
          file.copyTo(File(soundsDir, file.name), overwrite = true)
        }
        tempDir.deleteRecursively()

        // 4. Update memory cache
        downloadedStartSamples.clear()
        for (f in soundsDir.listFiles { _, name -> name.startsWith("start_") }?.sortedBy { it.name } ?: emptyList()) {
          val s = readWavOrRawPcm(f)
          if (s != null && s.isNotEmpty()) downloadedStartSamples.add(s)
        }

        downloadedStopSamples.clear()
        for (f in soundsDir.listFiles { _, name -> name.startsWith("stop_") }?.sortedBy { it.name } ?: emptyList()) {
          val s = readWavOrRawPcm(f)
          if (s != null && s.isNotEmpty()) downloadedStopSamples.add(s)
        }

        // 5. Save version in SharedPreferences
        prefs.edit().putInt("downloaded_version", remoteVersion).apply()

        isDownloading = false
        downloadProgress = 100
        notifyJsSuccess()
      } catch (e: Exception) {
        Log.e("SmartSoundManager", "Download error", e)
        isDownloading = false
        downloadProgress = 0
        notifyJsDownloadError("فشل تحميل الصوت، تم العودة للصوت المدمج")
      }
    }
  }

  private fun downloadFile(urlStr: String, destination: File): Boolean {
    var currentUrl = urlStr
    var redirects = 0
    val maxRedirects = 5

    while (redirects < maxRedirects) {
      var connection: HttpURLConnection? = null
      try {
        val url = URL(currentUrl)
        connection = (url.openConnection() as HttpURLConnection).apply {
          connectTimeout = 8000
          readTimeout = 8000
          requestMethod = "GET"
          instanceFollowRedirects = true
          setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Mobile Safari/537.36")
          setRequestProperty("Accept", "*/*")
          setRequestProperty("Connection", "close")
        }

        val status = connection.responseCode
        if (status in 300..399) {
          val redirectUrl = connection.getHeaderField("Location")
          if (!redirectUrl.isNullOrEmpty()) {
            currentUrl = redirectUrl
            redirects++
            connection.disconnect()
            continue
          }
        }

        if (status in 200..299) {
          connection.inputStream.use { input ->
            FileOutputStream(destination).use { output ->
              input.copyTo(output)
            }
          }
          return destination.exists() && destination.length() > 200
        }
        return false
      } catch (e: Exception) {
        Log.d("SmartSoundManager", "Download error: ${e.message}")
        if (destination.exists()) destination.delete()
        return false
      } finally {
        connection?.disconnect()
      }
    }
    return false
  }

  private fun notifyJsProgress(percent: Int) {
    downloadProgress = percent
    scope.launch(Dispatchers.Main) {
      webViewRef?.evaluateJavascript("if (window.onAudioDownloadProgress) { window.onAudioDownloadProgress($percent); }", null)
    }
  }

  private fun notifyJsSuccess() {
    scope.launch(Dispatchers.Main) {
      webViewRef?.evaluateJavascript("if (window.onAudioDownloadSuccess) { window.onAudioDownloadSuccess(); }", null)
    }
  }

  private fun notifyJsDownloadError(message: String) {
    scope.launch(Dispatchers.Main) {
      val escaped = JSONObject.quote(message)
      webViewRef?.evaluateJavascript("if (window.onAudioDownloadError) { window.onAudioDownloadError($escaped); }", null)
    }
  }

  private fun jsonArrayToList(array: JSONArray?): List<String> {
    if (array == null) return emptyList()
    val list = mutableListOf<String>()
    for (i in 0 until array.length()) {
      val item = array.optString(i)
      if (item.isNotBlank()) list.add(item)
    }
    return list
  }

  /**
   * Instant, zero-latency PCM audio playback.
   * Priority:
   * 1. Downloaded sound pack samples in internal storage (random selection without consecutive repeats).
   * 2. Local built-in offline comic sound fallback.
   */
  @JavascriptInterface
  fun playSound(type: String) {
    scope.launch(Dispatchers.Default) {
      try {
        val samplesToPlay: ShortArray? = when (type) {
          "start" -> {
            val pool = if (downloadedStartSamples.isNotEmpty()) downloadedStartSamples else offlineStartSamples
            if (pool.isNotEmpty()) {
              val idx = pickNonConsecutiveIndex(pool.size, lastStartIdx)
              lastStartIdx = idx
              pool[idx]
            } else null
          }
          "stop" -> {
            val pool = if (downloadedStopSamples.isNotEmpty()) downloadedStopSamples else offlineStopSamples
            if (pool.isNotEmpty()) {
              val idx = pickNonConsecutiveIndex(pool.size, lastStopIdx)
              lastStopIdx = idx
              pool[idx]
            } else null
          }
          else -> null
        }

        if (samplesToPlay != null && samplesToPlay.isNotEmpty()) {
          playPcmDirect(samplesToPlay)
        }
      } catch (e: Exception) {
        Log.e("SmartSoundManager", "Error playing sound: $type", e)
      }
    }
  }

  private suspend fun playPcmDirect(samples: ShortArray) {
    var track: AudioTrack? = null
    try {
      val bufferSize = samples.size * 2
      val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
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
      track.play()

      val durationMs = (samples.size.toDouble() / sampleRate * 1000).toLong() + 50
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

  private fun pickNonConsecutiveIndex(size: Int, lastIdx: Int): Int {
    if (size <= 1) return 0
    var next = Random.nextInt(size)
    if (next == lastIdx) {
      next = (next + 1) % size
    }
    return next
  }

  fun release() {
    // No-op for direct AudioTrack instances
  }

  // ===== PCM Synthesis Algorithms (No external decoder needed) =====

  private fun synthesizeCartoonScream(variant: Int): ShortArray {
    val durationSec = if (variant == 1) 0.36 else 0.42
    val numSamples = (sampleRate * durationSec).toInt()
    val samples = ShortArray(numSamples)

    val pitchMultiplier = if (variant == 1) 1.0 else 1.22
    var phase = 0.0

    for (i in 0 until numSamples) {
      val t = i.toDouble() / sampleRate
      val progress = t / durationSec

      // Dramatic high cartoon scream curve with sharp comic vibrato
      val baseFreq = (780.0 + 900.0 * sin(PI * progress)) * pitchMultiplier
      val vibrato = 50.0 * sin(2.0 * PI * 34.0 * t)
      val currentFreq = baseFreq + vibrato

      phase += 2.0 * PI * currentFreq / sampleRate

      val raw = 0.6 * sin(phase) + 0.3 * sin(2.0 * phase) + 0.15 * sin(3.0 * phase)
      val attack = if (t < 0.015) t / 0.015 else 1.0
      val decay = exp(-4.2 * progress)
      val amp = (raw * attack * decay * 31000.0).toInt().coerceIn(-32767, 32767)

      samples[i] = amp.toShort()
    }
    return samples
  }

  private fun synthesizeCartoonGroan(variant: Int): ShortArray {
    val durationSec = if (variant == 1) 0.28 else 0.32
    val numSamples = (sampleRate * durationSec).toInt()
    val samples = ShortArray(numSamples)

    val pitchMultiplier = if (variant == 1) 1.0 else 0.85
    var phase = 0.0

    for (i in 0 until numSamples) {
      val t = i.toDouble() / sampleRate
      val progress = t / durationSec

      val currentFreq = (680.0 * exp(-5.2 * progress) + 150.0) * pitchMultiplier
      phase += 2.0 * PI * currentFreq / sampleRate

      val raw = 0.7 * sin(phase) + 0.3 * sin(2.0 * phase)
      val attack = if (t < 0.01) t / 0.01 else 1.0
      val decay = exp(-5.8 * progress)
      val amp = (raw * attack * decay * 30000.0).toInt().coerceIn(-32767, 32767)

      samples[i] = amp.toShort()
    }
    return samples
  }

  private fun readWavOrRawPcm(file: File): ShortArray? {
    return try {
      val bytes = FileInputStream(file).use { it.readBytes() }
      if (bytes.size < 44) return null

      // Check if RIFF WAV header
      val isWav = bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte()
      val pcmStart = if (isWav) 44 else 0
      val pcmBytes = bytes.size - pcmStart
      val numSamples = pcmBytes / 2
      val samples = ShortArray(numSamples)

      for (i in 0 until numSamples) {
        val low = bytes[pcmStart + i * 2].toInt() and 0xFF
        val high = bytes[pcmStart + i * 2 + 1].toInt()
        samples[i] = ((high shl 8) or low).toShort()
      }
      samples
    } catch (e: Exception) {
      Log.d("SmartSoundManager", "Error reading audio file: ${e.message}")
      null
    }
  }

  private fun writeWavFile(destination: File, samples: ShortArray, sampleRate: Int) {
    val numChannels = 1
    val bitsPerSample = 16
    val byteRate = sampleRate * numChannels * bitsPerSample / 8
    val dataSize = samples.size * 2
    val totalSize = 36 + dataSize

    val out = ByteArray(44 + dataSize)
    System.arraycopy("RIFF".toByteArray(), 0, out, 0, 4)
    writeLeInt(out, 4, totalSize)
    System.arraycopy("WAVE".toByteArray(), 0, out, 8, 4)
    System.arraycopy("fmt ".toByteArray(), 0, out, 12, 4)
    writeLeInt(out, 16, 16)
    writeLeShort(out, 20, 1.toShort())
    writeLeShort(out, 22, numChannels.toShort())
    writeLeInt(out, 24, sampleRate)
    writeLeInt(out, 28, byteRate)
    writeLeShort(out, 32, (numChannels * bitsPerSample / 8).toShort())
    writeLeShort(out, 34, bitsPerSample.toShort())
    System.arraycopy("data".toByteArray(), 0, out, 36, 4)
    writeLeInt(out, 40, dataSize)

    var offset = 44
    for (sample in samples) {
      val v = sample.toInt()
      out[offset++] = (v and 0xFF).toByte()
      out[offset++] = ((v shr 8) and 0xFF).toByte()
    }

    FileOutputStream(destination).use { fos ->
      fos.write(out)
    }
  }

  private fun writeLeInt(buffer: ByteArray, offset: Int, value: Int) {
    buffer[offset] = (value and 0xFF).toByte()
    buffer[offset + 1] = ((value shr 8) and 0xFF).toByte()
    buffer[offset + 2] = ((value shr 16) and 0xFF).toByte()
    buffer[offset + 3] = ((value shr 24) and 0xFF).toByte()
  }

  private fun writeLeShort(buffer: ByteArray, offset: Int, value: Short) {
    val v = value.toInt()
    buffer[offset] = (v and 0xFF).toByte()
    buffer[offset + 1] = ((v shr 8) and 0xFF).toByte()
  }
}

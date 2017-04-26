package com.soywiz.korau.awt

import com.soywiz.korau.format.AudioStream
import com.soywiz.korau.sound.NativeSound
import com.soywiz.korau.sound.NativeSoundProvider
import com.soywiz.korio.async.*
import com.soywiz.korio.coroutine.korioSuspendCoroutine
import java.io.ByteArrayInputStream
import javax.sound.sampled.*

class AwtNativeSoundProvider : NativeSoundProvider() {
	override val priority: Int = 1000

	override suspend fun createSound(data: ByteArray): NativeSound {
		return AwtNativeSound(data)
	}

	suspend override fun play(stream: AudioStream): Unit = suspendCancellableCoroutine { c ->
		spawn {
			executeInNewThread {
				val af = AudioFormat(stream.rate.toFloat(), 16, stream.channels, true, false)
				val info = DataLine.Info(SourceDataLine::class.java, af)
				val line = AudioSystem.getLine(info) as SourceDataLine

				line.open(af, 4096)
				line.start()

				val sdata = ShortArray(1024)
				val bdata = ByteArray(sdata.size * 2)
				//var writtenLength = 0L

				while (!c.cancelled) {
					//while (true) {
					//println(c.cancelled)
					//println(line.microsecondPosition)
					//println("" + line.longFramePosition + "/" + writtenLength + "/" + cancelled)
					val read = stream.read(sdata, 0, sdata.size)
					if (read <= 0) break
					var m = 0
					for (n in 0 until read) {
						val s = sdata[n].toInt()
						bdata[m++] = ((s ushr 0) and 0xFF).toByte()
						bdata[m++] = ((s ushr 8) and 0xFF).toByte()
					}
					//println(line.available())
					line.write(bdata, 0, m)
					//writtenLength += read / stream.channels
				}
				line.drain()
				line.stop()
				line.close()
				c.resume(Unit)
			}
		}
	}
}

class AwtNativeSound(val data: ByteArray) : NativeSound() {
	suspend override fun play(): Unit = korioSuspendCoroutine { c ->
		Thread {
			val sound = AudioSystem.getAudioInputStream(ByteArrayInputStream(data))
			val info = DataLine.Info(Clip::class.java, sound.format)
			val clip = AudioSystem.getLine(info) as Clip
			clip.open(sound)
			clip.addLineListener { event ->
				if (event.type === LineEvent.Type.STOP) {
					event.line.close()
					c.resume(Unit)
				}
			}
			clip.start()
		}.apply {
			isDaemon = true
		}.start()
	}
}
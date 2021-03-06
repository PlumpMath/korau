package com.soywiz.korau.android

import android.media.MediaPlayer
import com.soywiz.korau.sound.NativeSound
import com.soywiz.korau.sound.NativeSoundProvider
import com.soywiz.korio.coroutine.korioSuspendCoroutine
import com.soywiz.korio.crypto.Base64
import com.soywiz.korio.util.Pool

class AndroidNativeSoundProvider : NativeSoundProvider() {
	override val priority: Int = 700

	val mpPool = Pool(reset = {
		it.setOnCompletionListener(null)
		it.reset()
	}) { MediaPlayer() }

	fun getDurationInMs(url: String): Int {
		return mpPool.alloc { mp ->
			mp.setDataSource(url)
			mp.prepare()
			mp.duration
		}
	}

	override suspend fun createSound(data: ByteArray): NativeSound = AndroidNativeSound(this, "data:audio/mp3;base64," + Base64.encode(data))
	//suspend override fun createSound(file: VfsFile): NativeSound {
	//}
}

class AndroidNativeSound(val prov: AndroidNativeSoundProvider, val url: String) : NativeSound() {
	override val lengthInMs: Long by lazy { prov.getDurationInMs(url).toLong() }

	suspend override fun play(): Unit = korioSuspendCoroutine { c ->
		prov.mpPool.alloc { mp ->
			mp.setDataSource(url)
			mp.setOnCompletionListener {
				prov.mpPool.free(mp)
				c.resume(Unit)
			}
			mp.prepare()
			mp.start()
		}
	}
}
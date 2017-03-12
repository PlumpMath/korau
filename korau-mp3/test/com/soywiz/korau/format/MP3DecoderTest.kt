package com.soywiz.korau.format

import com.soywiz.korio.async.syncTest
import com.soywiz.korio.vfs.LocalVfs
import com.soywiz.korio.vfs.ResourcesVfs
import org.junit.Assert
import org.junit.Test

class MP3DecoderTest {
    @Test
    fun testDecodeWav() = syncTest {
        val output = ResourcesVfs["mp31.mp3"].readAudioData()
        val outputBytes = AudioFormats.encodeToByteArray(output, "out.wav")

        //output.play()
        //expected.play()

        //LocalVfs("c:/temp/mp31.mp3.wav").write(outputBytes)

        val expected = ResourcesVfs["mp31.mp3.wav"].readAudioData()
        val expectedBytes = AudioFormats.encodeToByteArray(expected, "out.wav")

        Assert.assertArrayEquals(expectedBytes, outputBytes)
    }
}
package com.soywiz.korau.format

import com.soywiz.korio.stream.AsyncStream
import net.sourceforge.lame.mp3.FrameSkip
import net.sourceforge.lame.mp3.GetAudio
import net.sourceforge.lame.mp3.Lame
import net.sourceforge.lame.util.AsyncStreamToRandomReader

class MP3Decoder : MP3() {
    suspend override fun decodeStream(data: AsyncStream): AudioStream? {
        val lame = Lame()
        lame.parser.inputFormat = GetAudio.SoundFileFormat.sf_mp123
        lame.audio.initInFile(lame.flags, AsyncStreamToRandomReader(data), FrameSkip())

        lame.parser.mp3InputData.totalFrames = lame.parser.mp3InputData.numSamples / lame.parser.mp3InputData.frameSize

        assert(lame.flags.inNumChannels in 1..2)

        val buffer = Array(2) { FloatArray(1152) }
        return AudioStream.generator(lame.flags.inSampleRate, lame.flags.inNumChannels) {
            val flags = lame.flags
            val iread = lame.audio.get_audio16(flags, buffer)
            if (iread > 0) {
                var opos = 0
                val out = ShortArray(iread * flags.inNumChannels)
                val mp3InputData = lame.parser.mp3InputData
                val framesDecodedCounter = mp3InputData.framesDecodedCounter + iread / mp3InputData.frameSize
                mp3InputData.framesDecodedCounter = framesDecodedCounter

                for (i in 0 until iread) {
                    var sample = buffer[0][i].toInt() and 0xffff
                    out[opos++] = sample.toShort()
                    if (flags.inNumChannels == 2) {
                        sample = buffer[1][i].toInt() and 0xffff
                        out[opos++] = sample.toShort()
                    }
                }
                out
            } else {
                null
            }
        }
    }
}
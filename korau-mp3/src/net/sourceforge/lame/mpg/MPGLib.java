/**
 * LAME MP3 encoding engine
 * <p>
 * Copyright (c) 1999-2000 Mark Taylor
 * Copyright (c) 2003 Olcios
 * Copyright (c) 2008 Robert Hegemann
 * <p>
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2 of the License, or (at your option) any later version.
 * <p>
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Library General Public License for more details.
 * <p>
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the
 * Free Software Foundation, Inc., 59 Temple Place - Suite 330,
 * Boston, MA 02111-1307, USA.
 *
 * @author Ken H�ndel
 */
package net.sourceforge.lame.mpg;

import net.sourceforge.lame.mp3.FrameSkip;
import net.sourceforge.lame.mp3.MP3Data;
import net.sourceforge.lame.mp3.PlottingData;

import java.util.List;

public class MPGLib {

    public final static int MP3_ERR = -1;
    public final static int MP3_OK = 0;
    final static int MP3_NEED_MORE = 1;

    private static final int smpls[][] = {{0, 384, 1152, 1152}, {0, 384, 1152, 576}};
    private static final int OUTSIZE_CLIPPED = 4096;
    Interface interf;

    public MPGLib(Interface interf) {
        this.interf = interf;
    }

    /* copy mono samples */
    protected void COPY_MONO(
            float[] pcm_l, int pcm_lPos,
            int processed_samples, float[] p
    ) {
        int p_samples = 0;
        for (int i = 0; i < processed_samples; i++)
            pcm_l[pcm_lPos++] = p[p_samples++];
    }

    /* copy stereo samples */
    protected void COPY_STEREO(
            float[] pcm_l, int pcm_lPos, float[] pcm_r,
            int pcm_rPos, int processed_samples, float[] p
    ) {
        int p_samples = 0;
        for (int i = 0; i < processed_samples; i++) {
            pcm_l[pcm_lPos++] = p[p_samples++];
            pcm_r[pcm_rPos++] = p[p_samples++];
        }
    }

    private int decode1_headersB_clipchoice(
            mpstr_tag pmp, byte[] buffer,
            int bufferPos, int len, float[] pcm_l, int pcm_lPos, float[] pcm_r,
            int pcm_rPos, MP3Data mp3data, FrameSkip enc, float[] p, int psize,
            IDecoder decodeMP3_ptr
    ) {

        mp3data.setHeader_parsed(false);

        ProcessedBytes pb = new ProcessedBytes();
        int ret = decodeMP3_ptr.decode(pmp, buffer, bufferPos, len, p, psize, pb);
        int processed_samples = pb.pb;
        if (pmp.header_parsed || pmp.fsizeold > 0 || pmp.framesize > 0) {
            mp3data.setHeader_parsed(true);
            mp3data.setStereo(pmp.fr.stereo);
            mp3data.setSamplerate(Common.freqs[pmp.fr.sampling_frequency]);
            mp3data.setMode(pmp.fr.mode);
            mp3data.setMode_ext(pmp.fr.mode_ext);
            mp3data.setFrameSize(smpls[pmp.fr.lsf][pmp.fr.lay]);

            /* free format, we need the entire frame before we can determine
             * the bitrate.  If we haven't gotten the entire frame, bitrate=0 */
            if (pmp.fsizeold > 0) /* works for free format and fixed, no overrun, temporal results are < 400.e6 */
                mp3data.setBitrate((int) (8 * (4 + pmp.fsizeold) * mp3data.getSamplerate() /
                        (1.e3 * mp3data.getFrameSize()) + 0.5));
            else if (pmp.framesize > 0)
                mp3data.setBitrate((int) (8 * (4 + pmp.framesize) * mp3data.getSamplerate() /
                        (1.e3 * mp3data.getFrameSize()) + 0.5));
            else
                mp3data.setBitrate(Common.tabsel_123[pmp.fr.lsf][pmp.fr.lay - 1][pmp.fr.bitrate_index]);


            if (pmp.num_frames > 0) {
                /* Xing VBR header found and num_frames was set */
                mp3data.setTotalFrames(pmp.num_frames);
                mp3data.setNumSamples(mp3data.getFrameSize() * pmp.num_frames);
                enc.setEncoderDelay(pmp.enc_delay);
                enc.setEncoderPadding(pmp.enc_padding);
            }
        }

        switch (ret) {
            case MP3_OK:
                switch (pmp.fr.stereo) {
                    case 1:
                        COPY_MONO(pcm_l, pcm_lPos, processed_samples, p);
                        break;
                    case 2:
                        processed_samples = (processed_samples) >> 1;
                        COPY_STEREO(pcm_l, pcm_lPos, pcm_r, pcm_rPos, processed_samples, p);
                        break;
                    default:
                        processed_samples = -1;
                        assert (false);
                        break;
                }
                break;

            case MP3_NEED_MORE:
                processed_samples = 0;
                break;

            case MP3_ERR:
                processed_samples = -1;
                break;

            default:
                processed_samples = -1;
                assert (false);
                break;
        }

        return processed_samples;
    }

    public mpstr_tag hip_decode_init() {
        return interf.InitMP3();
    }

    public int hip_decode_exit(mpstr_tag hip) {
        if (hip != null) {
            interf.ExitMP3(hip);
            hip = null;
        }
        return 0;
    }

    public int hip_decode1_headers(
            mpstr_tag hip, byte[] buffer,
            int len,
            final float[] pcm_l, final float[] pcm_r, MP3Data mp3data,
            FrameSkip enc
    ) {
        if (hip != null) {
            IDecoder dec = new IDecoder() {

                @Override
                public int decode(mpstr_tag mp, byte[] in, int bufferPos, int isize,
                                  float[] out, int osize, ProcessedBytes done) {
                    return interf.decodeMP3(mp, in, bufferPos, isize, out, osize, done);
                }
            };
            float[] out = new float[OUTSIZE_CLIPPED];
            return decode1_headersB_clipchoice(hip, buffer, 0, len, pcm_l, 0,
                    pcm_r, 0, mp3data, enc, out, OUTSIZE_CLIPPED, dec);
        }
        return -1;
    }

    interface IDecoder {
        int decode(mpstr_tag mp, byte[] in, int bufferPos, int isize, float[] out, int osize, ProcessedBytes done);
    }

    public static class buf {
        byte[] pnt;
        int size;
        int pos;
    }

    public static class mpstr_tag {
        /**
         * Buffer linked list, first list entry points to oldest buffer.
         */
        List<buf> list;
        /**
         * Valid Xing vbr header detected?
         */
        boolean vbr_header;
        /**
         * Set if vbr header present.
         */
        int num_frames;
        /**
         * Set if vbr header present.
         */
        int enc_delay;
        /**
         * Set if vbr header present.
         */
        int enc_padding;
        /**
         * Header of current frame has been parsed.
         * <p/>
         * Note: Header_parsed, side_parsed and data_parsed must be all set
         * before the full frame has been parsed.
         */
        boolean header_parsed;
        /**
         * Header of sideinfo of current frame has been parsed.
         * <p/>
         * Note: Header_parsed, side_parsed and data_parsed must be all set
         * before the full frame has been parsed.
         */
        boolean side_parsed;
        /**
         * Note: Header_parsed, side_parsed and data_parsed must be all set
         * before the full frame has been parsed.
         */
        boolean data_parsed;
        /**
         * Free format frame?
         */
        boolean free_format;
        /**
         * Last frame was free format?
         */
        boolean old_free_format;
        int bsize;
        int framesize;
        /**
         * Number of bytes used for side information, including 2 bytes for
         * CRC-16 if present.
         */
        int ssize;
        int dsize;
        /**
         * Size of previous frame, -1 for first.
         */
        int fsizeold;
        int fsizeold_nopadding;
        /**
         * Holds the parameters decoded from the header.
         */
        Frame fr = new Frame();
        /**
         * Bit stream space used.
         */
        byte bsspace[][] = new byte[2][MPG123.MAXFRAMESIZE + 1024];
        float hybrid_block[][][] = new float[2][2][MPG123.SBLIMIT
                * MPG123.SSLIMIT];
        int hybrid_blc[] = new int[2];
        long header;
        int bsnum;
        float synth_buffs[][][] = new float[2][2][0x110];
        int synth_bo;
        /**
         * Bit-stream is yet to be synchronized.
         */
        boolean sync_bitstream;

        int bitindex;
        byte[] wordpointer;
        int wordpointerPos;
        PlottingData pinfo;
    }

    static class ProcessedBytes {
        int pb;
    }

}

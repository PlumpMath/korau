/*
 *      MP3 bitstream Output interface for LAME
 *
 *      Copyright (c) 1999-2000 Mark Taylor
 *      Copyright (c) 1999-2002 Takehiro Tominaga
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Library General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the
 * Free Software Foundation, Inc., 59 Temple Place - Suite 330,
 * Boston, MA 02111-1307, USA.
 *
 * $Id: BitStream.java,v 1.27 2012/03/23 10:02:29 kenchis Exp $
 */
package net.sourceforge.lame.mp3;

import net.sourceforge.lame.mpg.MPGLib;

public class BitStream {

    private static final int CRC16_POLYNOMIAL = 0x8005;

    /*
     * we work with ints, so when doing bit manipulation, we limit ourselves to
     * MAX_LENGTH-2 just to be on the safe side
     */
    private static final int MAX_LENGTH = 32;

    GainAnalysis ga;
    MPGLib mpg;
    VBRTag vbr;
    /**
     * Bit stream buffer.
     */
    private byte[] buf;
    /**
     * Pointer to top byte in buffer.
     */
    private int bufByteIdx;
    /**
     * Pointer to top bit of top byte in buffer.
     */
    private int bufBitIdx;

    public static boolean EQ(float a, float b) {
        return (Math.abs(a) > Math.abs(b)) ? (Math.abs((a) - (b)) <= (Math
                .abs(a) * 1e-6f))
                : (Math.abs((a) - (b)) <= (Math.abs(b) * 1e-6f));
    }

    public final void setModules(GainAnalysis ga, MPGLib mpg,
                                 VBRTag vbr) {
        this.ga = ga;
        this.mpg = mpg;
        this.vbr = vbr;
    }

    /**
     * compute bitsperframe and mean_bits for a layer III frame
     */
    public final int getframebits(final LameGlobalFlags gfp) {
        final LameInternalFlags gfc = gfp.internal_flags;
        int bit_rate;

		/* get bitrate in kbps [?] */
        if (gfc.bitrate_index != 0)
            bit_rate = Tables.bitrate_table[gfp.getMpegVersion()][gfc.bitrate_index];
        else
            bit_rate = gfp.getBitRate();
        assert (8 <= bit_rate && bit_rate <= 640);

		/* main encoding routine toggles padding on and off */
    /* one Layer3 Slot consists of 8 bits */
        return 8 * ((gfp.getMpegVersion() + 1) * 72000 * bit_rate / gfp.getOutSampleRate() + gfc.padding);
    }


    /**
     * write j bits into the bit stream, ignoring frame headers
     */
    private void putbits_noheaders(final LameInternalFlags gfc, final int val,
                                   int j) {
        assert (j < MAX_LENGTH - 2);

        while (j > 0) {
            int k;
            if (bufBitIdx == 0) {
                bufBitIdx = 8;
                bufByteIdx++;
                assert (bufByteIdx < Lame.LAME_MAXMP3BUFFER);
                buf[bufByteIdx] = 0;
            }

            k = Math.min(j, bufBitIdx);
            j -= k;

            bufBitIdx -= k;

            assert (j < MAX_LENGTH); /* 32 too large on 32 bit machines */
            assert (bufBitIdx < MAX_LENGTH);

            buf[bufByteIdx] |= ((val >> j) << bufBitIdx);
        }
    }


    private int CRC_update(int value, int crc) {
        value <<= 8;
        for (int i = 0; i < 8; i++) {
            value <<= 1;
            crc <<= 1;

            if ((((crc ^ value) & 0x10000) != 0))
                crc ^= CRC16_POLYNOMIAL;
        }
        return crc;
    }

    public final void CRC_writeheader(final LameInternalFlags gfc,
                                      final byte[] header) {
        int crc = 0xffff;
        /* (jo) init crc16 for error_protection */

        crc = CRC_update(header[2] & 0xff, crc);
        crc = CRC_update(header[3] & 0xff, crc);
        for (int i = 6; i < gfc.sideinfo_len; i++) {
            crc = CRC_update(header[i] & 0xff, crc);
        }

        header[4] = (byte) (crc >> 8);
        header[5] = (byte) (crc & 255);
    }


    public final void add_dummy_byte(final LameGlobalFlags gfp, final int val,
                                     int n) {
        final LameInternalFlags gfc = gfp.internal_flags;
        int i;

        while (n-- > 0) {
            putbits_noheaders(gfc, val, 8);

            for (i = 0; i < LameInternalFlags.MAX_HEADER_BUF; ++i)
                gfc.header[i].write_timing += 8;
        }
    }

    // From machine.h

    public final void init_bit_stream_w(final LameInternalFlags gfc) {
        buf = new byte[Lame.LAME_MAXMP3BUFFER];

        gfc.h_ptr = gfc.w_ptr = 0;
        gfc.header[gfc.h_ptr].write_timing = 0;
        bufByteIdx = -1;
        bufBitIdx = 0;
    }
}

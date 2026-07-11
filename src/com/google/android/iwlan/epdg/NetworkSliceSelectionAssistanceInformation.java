package com.google.android.iwlan.epdg;

import android.telephony.data.NetworkSliceInfo;
import android.util.Log;

import java.util.Arrays;
import java.util.List;

public class NetworkSliceSelectionAssistanceInformation {
    private static final String TAG =
            NetworkSliceSelectionAssistanceInformation.class.getSimpleName();

    // S-NSSAI value lengths as defined in TS 24.501 Section 9.11.2.8
    private static final int LENGTH_SST_ONLY = 1;
    private static final int LENGTH_SST_AND_MAPPED_SST = 2;
    private static final int LENGTH_SST_AND_SD = 4;
    private static final int LENGTH_SST_SD_AND_MAPPED_SST = 5;
    private static final int LENGTH_SST_SD_MAPPED_SST_AND_MAPPED_SD = 8;

    static final List<Integer> VALID_LENGTHS =
            Arrays.asList(
                    LENGTH_SST_ONLY,
                    LENGTH_SST_AND_MAPPED_SST,
                    LENGTH_SST_AND_SD,
                    LENGTH_SST_SD_AND_MAPPED_SST,
                    LENGTH_SST_SD_MAPPED_SST_AND_MAPPED_SD);

    private static final int RESERVED_SD_VALUE = 0xFFFFFF;

    public static NetworkSliceInfo getSliceInfo(byte[] snssai) {
        if (snssai == null) {
            return null;
        }
        /*
         * From 3GPP TS 24.501 Section 9.11.2.8, Content structure of the Value of S-NSSAI
         *
         * <p>Slice Service Type - 1 byte
         * Slice Differentiator - 3 bytes
         * Mapped HPLMN Slice Service Type - 1 byte
         * Mapped HPLMN Slice Differentiator - 3 bytes
         *
         * <p>Depending on the value of the length field the following S-NSSAI contents are
         * included:
         * Bits 8 7 6 5 4 3 2 1
         * 0 0 0 0 0 0 0 1 SST
         * 0 0 0 0 0 0 1 0 SST and mapped HPLMN SST
         * 0 0 0 0 0 1 0 0 SST and SD
         * 0 0 0 0 0 1 0 1 SST, SD and mapped HPLMN SST
         * 0 0 0 0 1 0 0 0 SST, SD, mapped HPLMN SST and mapped HPLMN SD
         * All other values are reserved.
         */
        NetworkSliceInfo.Builder sliceInfoBuilder = new NetworkSliceInfo.Builder();
        int len = snssai.length;
        // validity checks. See TS 24.501 Section 9.11.2.8
        // length should be valid
        if (!VALID_LENGTHS.contains(len)) {
            Log.d(TAG, "Invalid S-NSSAI length : " + snssai.length);
            return null;
        }

        sliceInfoBuilder.setSliceServiceType(parseSliceServiceType(snssai, 0));
        switch (len) {
            case LENGTH_SST_ONLY -> {}
            case LENGTH_SST_AND_MAPPED_SST ->
                    sliceInfoBuilder.setMappedHplmnSliceServiceType(
                            parseSliceServiceType(snssai, 1));
            case LENGTH_SST_AND_SD ->
                    sliceInfoBuilder.setSliceDifferentiator(parseSliceDifferentiator(snssai, 1));
            case LENGTH_SST_SD_AND_MAPPED_SST -> {
                sliceInfoBuilder.setSliceDifferentiator(parseSliceDifferentiator(snssai, 1));
                sliceInfoBuilder.setMappedHplmnSliceServiceType(parseSliceServiceType(snssai, 4));
            }
            case LENGTH_SST_SD_MAPPED_SST_AND_MAPPED_SD -> {
                sliceInfoBuilder.setSliceDifferentiator(parseSliceDifferentiator(snssai, 1));
                sliceInfoBuilder.setMappedHplmnSliceServiceType(parseSliceServiceType(snssai, 4));
                sliceInfoBuilder.setMappedHplmnSliceDifferentiator(
                        parseSliceDifferentiator(snssai, 5));
            }
        }

        return sliceInfoBuilder.build();
    }

    static int parseSliceServiceType(byte[] snssai, int offset) {
        if (offset < 0 || snssai.length < offset + 1) {
            return NetworkSliceInfo.SLICE_SERVICE_TYPE_NONE;
        }
        /*
         * From 3GPP TS 23.003: Values 0 to 127 belong to the standardized SST range and they are
         * defined in 3GPP TS 23.501. Values 128 to 255 belong to the Operator-specific range
         */
        return Byte.toUnsignedInt(snssai[offset]);
    }

    static int parseSliceDifferentiator(byte[] snssai, int offset) {
        /*
         * Slice Descriptor is 3 bytes long
         * The SD field has a reserved value "no SD value associated with the SST"
         * defined as hexadecimal FFFFFF
         */
        if (offset >= 0 && snssai.length >= offset + 3) {
            // Constructs the 24-bit integer from 3 bytes (Big-Endian/Network Order)
            int sd =
                    ((snssai[offset] & 0xFF) << 16)
                            | ((snssai[offset + 1] & 0xFF) << 8)
                            | ((snssai[offset + 2] & 0xFF));
            return (sd == RESERVED_SD_VALUE) ? NetworkSliceInfo.SLICE_DIFFERENTIATOR_NO_SLICE : sd;
        }
        return NetworkSliceInfo.SLICE_DIFFERENTIATOR_NO_SLICE;
    }
}

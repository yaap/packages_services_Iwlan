package com.google.android.iwlan.epdg;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.telephony.data.NetworkSliceInfo;

import com.google.common.collect.ImmutableList;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.List;

@RunWith(JUnit4.class)
public class NetworkSliceSelectionAssistanceInformationTest {

    @Test
    public void testGtSliceInfo_withNullNssai_returnsNull() {
        assertNull(NetworkSliceSelectionAssistanceInformation.getSliceInfo(null));
    }

    @Test
    public void testGetSliceInfo_withInvalidLengthNssai_returnsNull() {
        // Valid lengths are 1, 2, 4, 5, 8.
        List<byte[]> invalidNssais =
                ImmutableList.of(
                        new byte[] {}, // length 0
                        new byte[] {1, 1, 1}, // length 3
                        new byte[] {1, 1, 1, 2, 1, 1}, // length 6
                        new byte[] {1, 1, 1, 2, 1, 1, 2, 1, 1, 1} // length 10
                        );

        for (byte[] nssai : invalidNssais) {
            assertNull(NetworkSliceSelectionAssistanceInformation.getSliceInfo(nssai));
        }
    }

    @Test
    public void testGetSliceInfo_sstOnly() {
        byte[] nssai = {3}; // SST = 3
        parseAndAssertSliceInfo(
                nssai,
                /* expectedSst= */ 3,
                /* expectedSd= */ NetworkSliceInfo.SLICE_DIFFERENTIATOR_NO_SLICE,
                /* expectedMappedSst= */ NetworkSliceInfo.SLICE_SERVICE_TYPE_NONE,
                /* expectedMappedSd= */ NetworkSliceInfo.SLICE_DIFFERENTIATOR_NO_SLICE);
    }

    @Test
    public void testGetSliceInfo_sstAndMappedSst() {
        byte[] nssai = {3, 2}; // SST = 3, Mapped SST = 2
        parseAndAssertSliceInfo(
                nssai,
                /* expectedSst= */ 3,
                /* expectedSd= */ NetworkSliceInfo.SLICE_DIFFERENTIATOR_NO_SLICE,
                /* expectedMappedSst= */ 2,
                /* expectedMappedSd= */ NetworkSliceInfo.SLICE_DIFFERENTIATOR_NO_SLICE);
    }

    @Test
    public void testGetSliceInfo_sstAndSd() {
        byte[] nssai = {3, 0x0a, 0x0b, 0x0c}; // SST = 3, SD = 0x0A0B0C
        parseAndAssertSliceInfo(
                nssai,
                /* expectedSst= */ 3,
                /* expectedSd= */ 0x0A0B0C,
                /* expectedMappedSst= */ NetworkSliceInfo.SLICE_SERVICE_TYPE_NONE,
                /* expectedMappedSd= */ NetworkSliceInfo.SLICE_DIFFERENTIATOR_NO_SLICE);
    }

    @Test
    public void testGetSliceInfo_sstAndSd_handlesUnsignedConversion() {
        byte[] nssai = {1, (byte) 0xFF, (byte) 0xFF, (byte) 0xEF}; // SST = 1, SD = 0xFFFFEF
        parseAndAssertSliceInfo(
                nssai,
                /* expectedSst= */ 1,
                /* expectedSd= */ 0xFFFFEF,
                /* expectedMappedSst= */ NetworkSliceInfo.SLICE_SERVICE_TYPE_NONE,
                /* expectedMappedSd= */ NetworkSliceInfo.SLICE_DIFFERENTIATOR_NO_SLICE);
    }

    @Test
    public void testGetSliceInfo_sstSdAndMappedSst() {
        byte[] nssai = {3, 0x0a, 0x0b, 0x0c, 2}; // SST = 3, SD = 0x0A0B0C, Mapped SST = 2
        parseAndAssertSliceInfo(
                nssai,
                /* expectedSst= */ 3,
                /* expectedSd= */ 0x0A0B0C,
                /* expectedMappedSst= */ 2,
                /* expectedMappedSd= */ NetworkSliceInfo.SLICE_DIFFERENTIATOR_NO_SLICE);
    }

    @Test
    public void testGetSliceInfo_allFields() {
        byte[] nssai = {
            3, 0x0a, 0x0b, 0x0c, 2, 0x0F, 0x0E, 0x0D
        }; // SST=3, SD=0x0A0B0C, Mapped SST=2, Mapped SD=0x0F0E0D
        parseAndAssertSliceInfo(
                nssai,
                /* expectedSst= */ 3,
                /* expectedSd= */ 0x0A0B0C,
                /* expectedMappedSst= */ 2,
                /* expectedMappedSd= */ 0x0F0E0D);
    }

    /** Helper method to parse NSSAI and assert the state of the resulting NetworkSliceInfo. */
    private void parseAndAssertSliceInfo(
            byte[] nssai,
            int expectedSst,
            int expectedSd,
            int expectedMappedSst,
            int expectedMappedSd) {
        NetworkSliceInfo sliceInfo = NetworkSliceSelectionAssistanceInformation.getSliceInfo(nssai);

        assertNotNull(sliceInfo);
        assertEquals(expectedSst, sliceInfo.getSliceServiceType());
        assertEquals(expectedSd, sliceInfo.getSliceDifferentiator());
        assertEquals(expectedMappedSst, sliceInfo.getMappedHplmnSliceServiceType());
        assertEquals(expectedMappedSd, sliceInfo.getMappedHplmnSliceDifferentiator());
    }
}

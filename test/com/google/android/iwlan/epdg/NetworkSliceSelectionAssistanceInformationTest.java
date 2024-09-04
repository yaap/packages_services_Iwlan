package com.google.android.iwlan.epdg;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.telephony.data.NetworkSliceInfo;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class NetworkSliceSelectionAssistanceInformationTest {
    private static final String TAG = "NssaiTest";

    @Before
    public void setUp() throws Exception {}

    @After
    public void cleanUp() throws Exception {}

    @Test
    public void testNullNssai() throws Exception {
        NetworkSliceInfo si = NetworkSliceSelectionAssistanceInformation.getSliceInfo(null);
        assertNull(si);
    }

    @Test
    public void testNssaiWithInvalidLength() throws Exception {
        // only these lengths are valid: 1, 2, 4, 5, 8

        byte[] nssai0 = {}; // length 0
        byte[] nssai3 = {1, 1, 1}; // length 3
        byte[] nssai6 = {1, 1, 1, 2, 1, 1}; // length 6
        byte[] nssai10 = {1, 1, 1, 2, 1, 1, 2, 1, 1, 1}; // length 10

        NetworkSliceInfo si;

        si = NetworkSliceSelectionAssistanceInformation.getSliceInfo(nssai0);
        assertNull(si);

        si = NetworkSliceSelectionAssistanceInformation.getSliceInfo(nssai3);
        assertNull(si);

        si = NetworkSliceSelectionAssistanceInformation.getSliceInfo(nssai6);
        assertNull(si);

        si = NetworkSliceSelectionAssistanceInformation.getSliceInfo(nssai10);
        assertNull(si);
    }

    @Test
    public void testNssaiWithSST() throws Exception {
        byte[] nssai = {3}; // SST = 3
        NetworkSliceInfo si;

        si = NetworkSliceSelectionAssistanceInformation.getSliceInfo(nssai);

        assertNotNull(si);
        assertEquals(3, si.getSliceServiceType());
        assertEquals(NetworkSliceInfo.SLICE_SERVICE_TYPE_NONE, si.getMappedHplmnSliceServiceType());
        assertEquals(NetworkSliceInfo.SLICE_DIFFERENTIATOR_NO_SLICE, si.getSliceDifferentiator());
        assertEquals(
                NetworkSliceInfo.SLICE_DIFFERENTIATOR_NO_SLICE,
                si.getMappedHplmnSliceDifferentiator());
    }

    @Test
    public void testNssaiWithSSTandMappedSST() throws Exception {
        byte[] nssai = {3, 2}; // SST = 3, mapped SST=2
        NetworkSliceInfo si;

        si = NetworkSliceSelectionAssistanceInformation.getSliceInfo(nssai);

        assertNotNull(si);
        assertEquals(3, si.getSliceServiceType());
        assertEquals(2, si.getMappedHplmnSliceServiceType());
        assertEquals(NetworkSliceInfo.SLICE_DIFFERENTIATOR_NO_SLICE, si.getSliceDifferentiator());
        assertEquals(
                NetworkSliceInfo.SLICE_DIFFERENTIATOR_NO_SLICE,
                si.getMappedHplmnSliceDifferentiator());
    }

    @Test
    public void testNssaiWithSSTandSD() throws Exception {
        byte[] nssai = {3, 0x0a, 0x0b, 0x0c}; // SST = 3, SD=0x0a0b0c
        NetworkSliceInfo si;

        si = NetworkSliceSelectionAssistanceInformation.getSliceInfo(nssai);

        assertNotNull(si);
        assertEquals(3, si.getSliceServiceType());
        assertEquals(NetworkSliceInfo.SLICE_SERVICE_TYPE_NONE, si.getMappedHplmnSliceServiceType());
        assertEquals(0x0a0b0c, si.getSliceDifferentiator());
        assertEquals(
                NetworkSliceInfo.SLICE_DIFFERENTIATOR_NO_SLICE,
                si.getMappedHplmnSliceDifferentiator());
    }

    @Test
    public void testNssaiWithSSTandSDandMappedSST() throws Exception {
        byte[] nssai = {3, 0x0a, 0x0b, 0x0c, 3}; // SST = 3, SD=0x0a0b0c, Mapped SST=3
        NetworkSliceInfo si;

        si = NetworkSliceSelectionAssistanceInformation.getSliceInfo(nssai);

        assertNotNull(si);
        assertEquals(3, si.getSliceServiceType());
        assertEquals(3, si.getMappedHplmnSliceServiceType());
        assertEquals(0x0a0b0c, si.getSliceDifferentiator());
        assertEquals(
                NetworkSliceInfo.SLICE_DIFFERENTIATOR_NO_SLICE,
                si.getMappedHplmnSliceDifferentiator());
    }

    @Test
    public void testNssaiWithSSTandSDandMappedSSTandMappedSD() throws Exception {
        byte[] nssai = {
            3, 0x0a, 0x0b, 0x0c, 2, 0x0F, 0x0E, 0x0D
        }; // SST = 3, SD=0x0a0b0c, Mapped SST=2, Mapped SD =0x0f0e0d
        NetworkSliceInfo si;

        si = NetworkSliceSelectionAssistanceInformation.getSliceInfo(nssai);

        assertNotNull(si);
        assertEquals(3, si.getSliceServiceType());
        assertEquals(2, si.getMappedHplmnSliceServiceType());
        assertEquals(0x0a0b0c, si.getSliceDifferentiator());
        assertEquals(0x0f0e0d, si.getMappedHplmnSliceDifferentiator());
    }
}

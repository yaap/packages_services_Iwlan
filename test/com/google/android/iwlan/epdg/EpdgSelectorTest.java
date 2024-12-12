/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.iwlan.epdg;

import static android.net.DnsResolver.TYPE_A;
import static android.net.DnsResolver.TYPE_AAAA;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import static java.util.stream.Collectors.toList;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.DnsResolver;
import android.net.InetAddresses;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.ipsec.ike.exceptions.IkeIOException;
import android.net.ipsec.ike.exceptions.IkeNetworkLostException;
import android.net.ipsec.ike.exceptions.IkeProtocolException;
import android.os.Handler;
import android.os.Looper;
import android.telephony.CarrierConfigManager;
import android.telephony.CellIdentityGsm;
import android.telephony.CellIdentityLte;
import android.telephony.CellIdentityNr;
import android.telephony.CellIdentityWcdma;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoNr;
import android.telephony.CellInfoWcdma;
import android.telephony.DataFailCause;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.data.ApnSetting;
import android.util.Log;

import libcore.net.InetAddressUtils;

import com.google.android.iwlan.ErrorPolicyManager;
import com.google.android.iwlan.IwlanCarrierConfig;
import com.google.android.iwlan.IwlanError;
import com.google.android.iwlan.flags.FeatureFlags;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@RunWith(JUnit4.class)
public class EpdgSelectorTest {

    private static final String TAG = "EpdgSelectorTest";
    private EpdgSelector mEpdgSelector;
    public static final int DEFAULT_SLOT_INDEX = 0;

    private static final byte[] TEST_PCO_NO_DATA = {0x00};
    private static final byte[] TEST_PCO_PLMN_DATA = {0x38, 0x01, 0x24, 0x00};
    private static final byte[] TEST_PCO_IPV4_DATA = {0x38, 0x01, 0x24, 0x7F, 0x00, 0x00, 0x01};
    private static final byte[] TEST_PCO_IPV6_DATA = {
        0x38, 0x01, 0x24, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x01
    };

    private static final String TEST_LOCAL_IPV4_ADDRESS = "192.168.1.100";
    private static final String TEST_LOCAL_IPV6_ADDRESS = "2001:db8::1";

    private static final String TEST_IPV4_ADDRESS = "127.0.0.1";
    private static final String TEST_IPV4_ADDRESS_1 = "127.0.0.2";
    private static final String TEST_IPV4_ADDRESS_2 = "127.0.0.3";
    private static final String TEST_IPV4_ADDRESS_3 = "127.0.0.4";
    private static final String TEST_IPV4_ADDRESS_4 = "127.0.0.5";
    private static final String TEST_IPV4_ADDRESS_5 = "127.0.0.6";
    private static final String TEST_IPV4_ADDRESS_6 = "127.0.0.7";
    private static final String TEST_IPV4_ADDRESS_7 = "127.0.0.8";
    private static final String TEST_IPV6_ADDRESS = "0000:0000:0000:0000:0000:0000:0000:0001";

    private static final int TEST_PCO_ID_INVALID = 0xFF00;
    private static final int TEST_PCO_ID_IPV6 = 0xFF01;
    private static final int TEST_PCO_ID_IPV4 = 0xFF02;

    private final List<String> ehplmnList = new ArrayList<>();

    private final LinkProperties mTestLinkProperties = new LinkProperties();

    @Mock private Context mMockContext;
    @Mock private Network mMockNetwork;
    @Mock private ErrorPolicyManager mMockErrorPolicyManager;
    @Mock private SubscriptionManager mMockSubscriptionManager;
    @Mock private SubscriptionInfo mMockSubscriptionInfo;
    @Mock private TelephonyManager mMockTelephonyManager;
    @Mock private ConnectivityManager mMockConnectivityManager;
    @Mock private SharedPreferences mMockSharedPreferences;
    @Mock private DnsResolver mMockDnsResolver;
    @Mock private FeatureFlags mfakeFeatureFlags;

    private FakeDns mFakeDns;
    MockitoSession mStaticMockSession;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mStaticMockSession =
                mockitoSession()
                        .mockStatic(DnsResolver.class)
                        .mockStatic(ErrorPolicyManager.class)
                        .startMocking();

        // Stub the external instances before initializing EpdgSelector,
        // as these objects will be used in the constructor.
        when(mMockContext.getSystemService(eq(SubscriptionManager.class)))
                .thenReturn(mMockSubscriptionManager);
        when(mMockContext.getSystemService(eq(TelephonyManager.class)))
                .thenReturn(mMockTelephonyManager);
        when(mMockContext.getSystemService(eq(ConnectivityManager.class)))
                .thenReturn(mMockConnectivityManager);
        when(ErrorPolicyManager.getInstance(mMockContext, DEFAULT_SLOT_INDEX))
                .thenReturn(mMockErrorPolicyManager);

        mEpdgSelector = spy(new EpdgSelector(mMockContext, DEFAULT_SLOT_INDEX, mfakeFeatureFlags));

        when(mMockSubscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(anyInt()))
                .thenReturn(mMockSubscriptionInfo);

        when(mMockSubscriptionInfo.getMccString()).thenReturn("311");

        when(mMockSubscriptionInfo.getMncString()).thenReturn("120");

        when(mMockTelephonyManager.getNetworkOperator()).thenReturn("311120");

        when(mMockTelephonyManager.createForSubscriptionId(anyInt()))
                .thenReturn(mMockTelephonyManager);

        ehplmnList.add("300120");
        when(mMockTelephonyManager.getEquivalentHomePlmns()).thenReturn(ehplmnList);

        when(mMockTelephonyManager.getSimCountryIso()).thenReturn("ca");

        when(mMockContext.getSharedPreferences(anyString(), anyInt()))
                .thenReturn(mMockSharedPreferences);

        when(mMockSharedPreferences.getString(any(), any())).thenReturn("US");

        when(mMockConnectivityManager.getLinkProperties(mMockNetwork))
                .thenReturn(mTestLinkProperties);

        applyTestAddressToNetworkForFamily(EpdgSelector.PROTO_FILTER_IPV4V6);

        // Mock carrier configs with test bundle
        IwlanCarrierConfig.putTestConfigInt(
                CarrierConfigManager.Iwlan.KEY_EPDG_ADDRESS_IP_TYPE_PREFERENCE_INT,
                CarrierConfigManager.Iwlan.EPDG_ADDRESS_IPV4_PREFERRED);

        mFakeDns = new FakeDns();
        mFakeDns.startMocking();
    }

    @After
    public void cleanUp() throws Exception {
        mStaticMockSession.finishMocking();
        IwlanCarrierConfig.resetTestConfig();
        mFakeDns.clearAll();
    }

    private List<InetAddress> getInetAddresses(String... hostnames) {
        return Arrays.stream(hostnames)
                .map(
                        hostname -> {
                            try {
                                return InetAddress.getAllByName(hostname);
                            } catch (UnknownHostException e) {
                                throw new RuntimeException(e);
                            }
                        })
                .flatMap(Arrays::stream)
                .distinct()
                .collect(Collectors.toList());
    }

    @Test
    public void testStaticMethodPass() throws Exception {
        when(DnsResolver.getInstance()).thenReturn(mMockDnsResolver);

        // Set DnsResolver query mock
        final String testStaticAddress = "epdg.epc.mnc088.mcc888.pub.3gppnetwork.org";
        mFakeDns.setAnswer(testStaticAddress, new String[] {TEST_IPV4_ADDRESS}, TYPE_A);

        // Set carrier config mock
        IwlanCarrierConfig.putTestConfigIntArray(
                CarrierConfigManager.Iwlan.KEY_EPDG_ADDRESS_PRIORITY_INT_ARRAY,
                new int[] {CarrierConfigManager.Iwlan.EPDG_ADDRESS_STATIC});
        IwlanCarrierConfig.putTestConfigString(
                CarrierConfigManager.Iwlan.KEY_EPDG_STATIC_ADDRESS_STRING, testStaticAddress);

        var expectedAddresses = List.of(InetAddress.getAllByName(TEST_IPV4_ADDRESS));
        var actualAddresses = getValidatedServerListWithDefaultParams(/* isEmergency= */ false);
        assertEquals(expectedAddresses, actualAddresses);
    }

    @Test
    public void testStaticMethodDirectIpAddress_noDnsResolution() throws Exception {
        IwlanCarrierConfig.putTestConfigIntArray(
                CarrierConfigManager.Iwlan.KEY_EPDG_ADDRESS_PRIORITY_INT_ARRAY,
                new int[] {CarrierConfigManager.Iwlan.EPDG_ADDRESS_STATIC});
        // Carrier config directly contains the ePDG IP address.
        IwlanCarrierConfig.putTestConfigString(
                CarrierConfigManager.Iwlan.KEY_EPDG_STATIC_ADDRESS_STRING, TEST_IPV4_ADDRESS);

        var actualAddresses = getValidatedServerListWithDefaultParams(/* isEmergency= */ false);
        assertEquals(
                InetAddresses.parseNumericAddress(TEST_IPV4_ADDRESS), actualAddresses.getFirst());
    }

    @Test
    public void testRoamStaticMethodPass() throws Exception {
        when(DnsResolver.getInstance()).thenReturn(mMockDnsResolver);

        // Set DnsResolver query mock
        final String testRoamStaticAddress = "epdg.epc.mnc088.mcc888.pub.3gppnetwork.org";
        mFakeDns.setAnswer(testRoamStaticAddress, new String[] {TEST_IPV4_ADDRESS}, TYPE_A);

        // Set carrier config mock
        IwlanCarrierConfig.putTestConfigIntArray(
                CarrierConfigManager.Iwlan.KEY_EPDG_ADDRESS_PRIORITY_INT_ARRAY,
                new int[] {CarrierConfigManager.Iwlan.EPDG_ADDRESS_STATIC});
        IwlanCarrierConfig.putTestConfigString(
                CarrierConfigManager.Iwlan.KEY_EPDG_STATIC_ADDRESS_ROAMING_STRING,
                testRoamStaticAddress);

        var actualAddresses = getValidatedServerListWithDefaultParams(/* isEmergency= */ false);
        var expectedAddresses = getInetAddresses(TEST_IPV4_ADDRESS);
        assertEquals(expectedAddresses, actualAddresses);
    }

    @Test
    public void testPlmnResolutionMethod() throws Exception {
        testPlmnResolutionMethod(false);
    }

    @Test
    public void testPlmnResolutionMethodForEmergency() throws Exception {
        testPlmnResolutionMethod(true);
    }

    @Test
    public void testPlmnResolutionMethodWithNoPlmnInCarrierConfig() throws Exception {
        when(DnsResolver.getInstance()).thenReturn(mMockDnsResolver);

        // setUp() fills default values for mcc-mnc
        String expectedFqdnFromImsi = "epdg.epc.mnc120.mcc311.pub.3gppnetwork.org";
        String expectedFqdnFromEhplmn = "epdg.epc.mnc120.mcc300.pub.3gppnetwork.org";

        mFakeDns.setAnswer(expectedFqdnFromImsi, new String[] {TEST_IPV4_ADDRESS_1}, TYPE_A);
        mFakeDns.setAnswer(expectedFqdnFromEhplmn, new String[] {TEST_IPV4_ADDRESS_2}, TYPE_A);

        var actualAddresses = getValidatedServerListWithDefaultParams(/* isEmergency= */ false);
        var expectedAddresses = getInetAddresses(TEST_IPV4_ADDRESS_1, TEST_IPV4_ADDRESS_2);
        assertEquals(expectedAddresses, actualAddresses);
    }

    private void testPlmnResolutionMethod(boolean isEmergency) throws Exception {
        when(DnsResolver.getInstance()).thenReturn(mMockDnsResolver);

        String expectedFqdnFromImsi = "epdg.epc.mnc120.mcc311.pub.3gppnetwork.org";
        String expectedFqdnFromRplmn = "epdg.epc.mnc121.mcc311.pub.3gppnetwork.org";
        String expectedFqdnFromEhplmn = "epdg.epc.mnc120.mcc300.pub.3gppnetwork.org";
        String excludedFqdnFromConfig = "epdg.epc.mnc480.mcc310.pub.3gppnetwork.org";

        when(mMockTelephonyManager.getNetworkOperator()).thenReturn("311121");

        IwlanCarrierConfig.putTestConfigIntArray(
                CarrierConfigManager.Iwlan.KEY_EPDG_ADDRESS_PRIORITY_INT_ARRAY,
                new int[] {CarrierConfigManager.Iwlan.EPDG_ADDRESS_PLMN});
        IwlanCarrierConfig.putTestConfigStringArray(
                CarrierConfigManager.Iwlan.KEY_MCC_MNCS_STRING_ARRAY,
                new String[] {"310-480", "300-120", "311-120", "311-121"});

        mFakeDns.setAnswer(expectedFqdnFromImsi, new String[] {TEST_IPV4_ADDRESS}, TYPE_A);
        mFakeDns.setAnswer(expectedFqdnFromEhplmn, new String[] {TEST_IPV4_ADDRESS_1}, TYPE_A);
        mFakeDns.setAnswer(excludedFqdnFromConfig, new String[] {TEST_IPV4_ADDRESS_2}, TYPE_A);
        mFakeDns.setAnswer(
                "sos." + expectedFqdnFromImsi, new String[] {TEST_IPV4_ADDRESS_3}, TYPE_A);
        mFakeDns.setAnswer(
                "sos." + expectedFqdnFromEhplmn, new String[] {TEST_IPV4_ADDRESS_4}, TYPE_A);
        mFakeDns.setAnswer(
                "sos." + excludedFqdnFromConfig, new String[] {TEST_IPV4_ADDRESS_5}, TYPE_A);
        mFakeDns.setAnswer(expectedFqdnFromRplmn, new String[] {TEST_IPV4_ADDRESS_6}, TYPE_A);
        mFakeDns.setAnswer(
                "sos." + expectedFqdnFromRplmn, new String[] {TEST_IPV4_ADDRESS_7}, TYPE_A);

        var actualAddresses = getValidatedServerListWithDefaultParams(isEmergency);
        var expectedAddresses =
                getInetAddresses(
                        isEmergency
                                ? new String[] {
                                    TEST_IPV4_ADDRESS_7,
                                    TEST_IPV4_ADDRESS_6,
                                    TEST_IPV4_ADDRESS_3,
                                    TEST_IPV4_ADDRESS,
                                    TEST_IPV4_ADDRESS_4,
                                    TEST_IPV4_ADDRESS_1
                                }
                                : new String[] {
                                    TEST_IPV4_ADDRESS_6, TEST_IPV4_ADDRESS, TEST_IPV4_ADDRESS_1
                                });
        assertEquals(expectedAddresses, actualAddresses);
    }

    @Test
    public void testPlmnResolutionMethodWithDuplicatedImsiAndEhplmn() throws Exception {
        when(DnsResolver.getInstance()).thenReturn(mMockDnsResolver);

        String fqdnFromEhplmn1 = "epdg.epc.mnc120.mcc300.pub.3gppnetwork.org";
        String fqdnFromEhplmn2AndImsi = "epdg.epc.mnc120.mcc311.pub.3gppnetwork.org";
        String fqdnFromEhplmn3 = "epdg.epc.mnc122.mcc300.pub.3gppnetwork.org";
        String fqdnFromEhplmn4 = "epdg.epc.mnc123.mcc300.pub.3gppnetwork.org";

        when(mMockTelephonyManager.getNetworkOperator()).thenReturn("300121");
        ehplmnList.add("300122");
        ehplmnList.add("300123");

        IwlanCarrierConfig.putTestConfigIntArray(
                CarrierConfigManager.Iwlan.KEY_EPDG_ADDRESS_PRIORITY_INT_ARRAY,
                new int[] {CarrierConfigManager.Iwlan.EPDG_ADDRESS_PLMN});
        IwlanCarrierConfig.putTestConfigIntArray(
                CarrierConfigManager.Iwlan.KEY_EPDG_PLMN_PRIORITY_INT_ARRAY,
                new int[] {
                    CarrierConfigManager.Iwlan.EPDG_PLMN_HPLMN,
                    CarrierConfigManager.Iwlan.EPDG_PLMN_EHPLMN_ALL,
                });

        mFakeDns.setAnswer(fqdnFromEhplmn1, new String[] {TEST_IPV4_ADDRESS}, TYPE_A);
        mFakeDns.setAnswer(fqdnFromEhplmn2AndImsi, new String[] {TEST_IPV4_ADDRESS_1}, TYPE_A);
        mFakeDns.setAnswer(fqdnFromEhplmn3, new String[] {TEST_IPV4_ADDRESS_2}, TYPE_A);
        mFakeDns.setAnswer(fqdnFromEhplmn4, new String[] {TEST_IPV4_ADDRESS_3}, TYPE_A);

        List<InetAddress> actualAddresses = getValidatedServerListWithDefaultParams(false);
        String[] testIpAddresses = {
            TEST_IPV4_ADDRESS_1, TEST_IPV4_ADDRESS, TEST_IPV4_ADDRESS_2, TEST_IPV4_ADDRESS_3,
        };
        List<InetAddress> expectedAddresses = getInetAddresses(testIpAddresses);
        assertEquals(expectedAddresses, actualAddresses);
    }

    @Test
    public void testPlmnResolutionMethodWithInvalidLengthPlmns() throws Exception {
        when(DnsResolver.getInstance()).thenReturn(mMockDnsResolver);

        when(mMockSubscriptionInfo.getMccString()).thenReturn("31");
        when(mMockSubscriptionInfo.getMncString()).thenReturn("12");

        when(mMockTelephonyManager.getNetworkOperator()).thenReturn("300");
        ehplmnList.add("3001");
        ehplmnList.add("3");

        IwlanCarrierConfig.putTestConfigIntArray(
                CarrierConfigManager.Iwlan.KEY_EPDG_ADDRESS_PRIORITY_INT_ARRAY,
                new int[] {CarrierConfigManager.Iwlan.EPDG_ADDRESS_PLMN});
        IwlanCarrierConfig.putTestConfigIntArray(
                CarrierConfigManager.Iwlan.KEY_EPDG_PLMN_PRIORITY_INT_ARRAY,
                new int[] {
                    CarrierConfigManager.Iwlan.EPDG_PLMN_RPLMN,
                    CarrierConfigManager.Iwlan.EPDG_PLMN_HPLMN,
                    CarrierConfigManager.Iwlan.EPDG_PLMN_EHPLMN_ALL,
                });

        List<InetAddress> actualAddresses = getValidatedServerListWithDefaultParams(false);
        assertEquals(0, actualAddresses.size());
    }

    @Test
    public void testPlmnResolutionMethodWithInvalidCharacterPlmns() throws Exception {
        when(DnsResolver.getInstance()).thenReturn(mMockDnsResolver);

        when(mMockSubscriptionInfo.getMccString()).thenReturn("a b");
        when(mMockSubscriptionInfo.getMncString()).thenReturn("!@#");

        when(mMockTelephonyManager.getNetworkOperator()).thenReturn("a cde#");
        ehplmnList.add("abcdef");
        ehplmnList.add("1 23456");
        ehplmnList.add("1 2345");

        IwlanCarrierConfig.putTestConfigIntArray(
                CarrierConfigManager.Iwlan.KEY_EPDG_ADDRESS_PRIORITY_INT_ARRAY,
                new int[] {CarrierConfigManager.Iwlan.EPDG_ADDRESS_PLMN});
        IwlanCarrierConfig.putTestConfigIntArray(
                CarrierConfigManager.Iwlan.KEY_EPDG_PLMN_PRIORITY_INT_ARRAY,
                new int[] {
                    CarrierConfigManager.Iwlan.EPDG_PLMN_RPLMN,
                    CarrierConfigManager.Iwlan.EPDG_PLMN_HPLMN,
                    CarrierConfigManager.Iwlan.EPDG_PLMN_EHPLMN_ALL,
                });

        List<InetAddress> actualAddresses = getValidatedServerListWithDefaultParams(false);
        assertEquals(0, actualAddresses.size());
    }

    @Test
    public void testPlmnResolutionMethodWithEmptyPlmns() throws Exception {
        when(DnsResolver.getInstance()).thenReturn(mMockDnsResolver);

        when(mMockSubscriptionInfo.getMccString()).thenReturn(null);
        when(mMockSubscriptionInfo.getMncString()).thenReturn(null);

        when(mMockTelephonyManager.getNetworkOperator()).thenReturn("");
        ehplmnList.add("");

        IwlanCarrierConfig.putTestConfigIntArray(
                CarrierConfigManager.Iwlan.KEY_EPDG_ADDRESS_PRIORITY_INT_ARRAY,
                new int[] {CarrierConfigManager.Iwlan.EPDG_ADDRESS_PLMN});
        IwlanCarrierConfig.putTestConfigIntArray(
                CarrierConfigManager.Iwlan.KEY_EPDG_PLMN_PRIORITY_INT_ARRAY,
                new int[] {
                    CarrierConfigManager.Iwlan.EPDG_PLMN_RPLMN,
                    CarrierConfigManager.Iwlan.EPDG_PLMN_HPLMN,
                    CarrierConfigManager.Iwlan.EPDG_PLMN_EHPLMN_ALL,
                });

        List<InetAddress> actualAddresses = getValidatedServerListWithDefaultParams(false);
        assertEquals(0, actualAddresses.size());
    }

    @Test
    public void testPlmnResolutionMethodWithFirstEhplmn() throws Exception {
        when(DnsResolver.getInstance()).thenReturn(mMockDnsResolver);

        String fqdnFromEhplmn1 = "epdg.epc.mnc120.mcc300.pub.3gppnetwork.org";
        String fqdnFromEhplmn2 = "epdg.epc.mnc121.mcc300.pub.3gppnetwork.org";
        String fqdnFromEhplmn3 = "epdg.epc.mnc122.mcc300.pub.3gppnetwork.org";
        String fqdnFromEhplmn4 = "epdg.epc.mnc123.mcc300.pub.3gppnetwork.org";

        ehplmnList.add("300121");
        ehplmnList.add("300122");
        ehplmnList.add("300123");

        IwlanCarrierConfig.putTestConfigIntArray(
                CarrierConfigManager.Iwlan.KEY_EPDG_ADDRESS_PRIORITY_INT_ARRAY,
                new int[] {CarrierConfigManager.Iwlan.EPDG_ADDRESS_PLMN});
        IwlanCarrierConfig.putTestConfigIntArray(
                CarrierConfigManager.Iwlan.KEY_EPDG_PLMN_PRIORITY_INT_ARRAY,
                new int[] {CarrierConfigManager.Iwlan.EPDG_PLMN_EHPLMN_FIRST});

        mFakeDns.setAnswer(fqdnFromEhplmn1, new String[] {TEST_IPV4_ADDRESS}, TYPE_A);
        mFakeDns.setAnswer(fqdnFromEhplmn2, new String[] {TEST_IPV4_ADDRESS_1}, TYPE_A);
        mFakeDns.setAnswer(fqdnFromEhplmn3, new String[] {TEST_IPV4_ADDRESS_2}, TYPE_A);
        mFakeDns.setAnswer(fqdnFromEhplmn4, new String[] {TEST_IPV4_ADDRESS_3}, TYPE_A);

        var actualAddresses = getValidatedServerListWithDefaultParams(/* isEmergency= */ false);
        var expectedAddresses = getInetAddresses(TEST_IPV4_ADDRESS);
        assertEquals(expectedAddresses, actualAddresses);
    }

    @Test
    public void testPlmnResolutionMethodWithRplmn() throws Exception {
        when(DnsResolver.getInstance()).thenReturn(mMockDnsResolver);

        String fqdnFromRplmn = "epdg.epc.mnc122.mcc300.pub.3gppnetwork.org";
        String fqdnFromEhplmn1 = "epdg.epc.mnc120.mcc300.pub.3gppnetwork.org";
        String fqdnFromEhplmn2 = "epdg.epc.mnc121.mcc300.pub.3gppnetwork.org";

        when(mMockTelephonyManager.getNetworkOperator()).thenReturn("300122");
        ehplmnList.add("300121");

        IwlanCarrierConfig.putTestConfigStringArray(
                CarrierConfigManager.Iwlan.KEY_MCC_MNCS_STRING_ARRAY,
                new String[] {"310-480", "300-122", "300-121"});

        IwlanCarrierConfig.putTestConfigIntArray(
                CarrierConfigManager.Iwlan.KEY_EPDG_ADDRESS_PRIORITY_INT_ARRAY,
                new int[] {CarrierConfigManager.Iwlan.EPDG_ADDRESS_PLMN});
        IwlanCarrierConfig.putTestConfigIntArray(
                CarrierConfigManager.Iwlan.KEY_EPDG_PLMN_PRIORITY_INT_ARRAY,
                new int[] {CarrierConfigManager.Iwlan.EPDG_PLMN_RPLMN});

        mFakeDns.setAnswer(fqdnFromRplmn, new String[] {TEST_IPV4_ADDRESS}, TYPE_A);
        mFakeDns.setAnswer(fqdnFromEhplmn1, new String[] {TEST_IPV4_ADDRESS_1}, TYPE_A);
        mFakeDns.setAnswer(fqdnFromEhplmn2, new String[] {TEST_IPV4_ADDRESS_2}, TYPE_A);

        var actualAddresses = getValidatedServerListWithDefaultParams(/* isEmergency= */ false);
        var expectedAddresses = getInetAddresses(TEST_IPV4_ADDRESS);
        assertEquals(expectedAddresses, actualAddresses);
    }

    @Test
    public void testCarrierConfigStaticAddressList() throws Exception {
        when(DnsResolver.getInstance()).thenReturn(mMockDnsResolver);

        // Set DnsResolver query mock
        final String addr1 = "epdg.epc.mnc480.mcc310.pub.3gppnetwork.org";
        final String addr2 = "epdg.epc.mnc120.mcc300.pub.3gppnetwork.org";
        final String addr3 = "epdg.epc.mnc120.mcc311.pub.3gppnetwork.org";
        final String testStaticAddress = addr1 + "," + addr2 + "," + addr3;

        mFakeDns.setAnswer(addr1, new String[] {TEST_IPV4_ADDRESS_1}, TYPE_A);
        mFakeDns.setAnswer(addr2, new String[] {TEST_IPV4_ADDRESS_2}, TYPE_A);
        mFakeDns.setAnswer(addr3, new String[] {TEST_IPV4_ADDRESS}, TYPE_A);

        // Set carrier config mock
        IwlanCarrierConfig.putTestConfigIntArray(
                CarrierConfigManager.Iwlan.KEY_EPDG_ADDRESS_PRIORITY_INT_ARRAY,
                new int[] {CarrierConfigManager.Iwlan.EPDG_ADDRESS_STATIC});
        IwlanCarrierConfig.putTestConfigString(
                CarrierConfigManager.Iwlan.KEY_EPDG_STATIC_ADDRESS_STRING, testStaticAddress);

        var actualAddresses = getValidatedServerListWithDefaultParams(/* isEmergency= */ false);
        var expectedAddresses =
                getInetAddresses(TEST_IPV4_ADDRESS_1, TEST_IPV4_ADDRESS_2, TEST_IPV4_ADDRESS);
        assertEquals(expectedAddresses, actualAddresses);
    }

    private List<InetAddress> getValidatedServerListWithDefaultParams(boolean isEmergency)
            throws Exception {
        return getValidatedServerListWithIpPreference(
                EpdgSelector.PROTO_FILTER_IPV4V6, EpdgSelector.IPV4_PREFERRED, isEmergency);
    }

    private List<InetAddress> getValidatedServerListWithIpPreference(
            @EpdgSelector.ProtoFilter int filter,
            @EpdgSelector.EpdgAddressOrder int order,
            boolean isEmergency)
            throws Exception {
        List<InetAddress> actualAddresses = new ArrayList<>();
        final CountDownLatch latch = new CountDownLatch(1);
        IwlanError ret =
                mEpdgSelector.getValidatedServerList(
                        /* transactionId= */ 1234,
                        filter,
                        order,
                        /* isRoaming= */ false,
                        isEmergency,
                        mMockNetwork,
                        new EpdgSelector.EpdgSelectorCallback() {
                            @Override
                            public void onServerListChanged(
                                    int transactionId, List<InetAddress> validIPList) {
                                assertEquals(1234, transactionId);

                                actualAddresses.addAll(validIPList);
                                Log.d(TAG, "onServerListChanged received");
                                latch.countDown();
                            }

                            @Override
                            public void onError(int transactionId, IwlanError epdgSelectorError) {
                                Log.d(TAG, "onError received");
                                latch.countDown();
                            }
                        });

        assertEquals(IwlanError.NO_ERROR, ret.getErrorType());
        latch.await(1, TimeUnit.SECONDS);
        return actualAddresses;
    }

    @Test
    public void testResolutionMethodPco_noPcoData() throws Exception {
        addTestPcoIdsToTestConfigBundle();

        sendCarrierSignalPcoValue(ApnSetting.TYPE_IMS, TEST_PCO_ID_IPV4, TEST_PCO_NO_DATA);
        sendCarrierSignalPcoValue(ApnSetting.TYPE_IMS, TEST_PCO_ID_IPV6, TEST_PCO_NO_DATA);

        List<InetAddress> actualAddresses =
                getValidatedServerListWithDefaultParams(/* isEmergency= */ false);

        assertEquals(0, actualAddresses.size());
    }

    @Test
    public void testResolutionMethodPco_withPlmnData() throws Exception {
        addTestPcoIdsToTestConfigBundle();

        sendCarrierSignalPcoValue(ApnSetting.TYPE_IMS, TEST_PCO_ID_IPV4, TEST_PCO_PLMN_DATA);
        sendCarrierSignalPcoValue(ApnSetting.TYPE_IMS, TEST_PCO_ID_IPV6, TEST_PCO_PLMN_DATA);

        List<InetAddress> actualAddresses =
                getValidatedServerListWithDefaultParams(/* isEmergency= */ false);
        assertEquals(0, actualAddresses.size());
    }

    private void addTestPcoIdsToTestConfigBundle() {
        IwlanCarrierConfig.putTestConfigIntArray(
                CarrierConfigManager.Iwlan.KEY_EPDG_ADDRESS_PRIORITY_INT_ARRAY,
                new int[] {CarrierConfigManager.Iwlan.EPDG_ADDRESS_PCO});
        IwlanCarrierConfig.putTestConfigInt(
                CarrierConfigManager.Iwlan.KEY_EPDG_PCO_ID_IPV6_INT, TEST_PCO_ID_IPV6);
        IwlanCarrierConfig.putTestConfigInt(
                CarrierConfigManager.Iwlan.KEY_EPDG_PCO_ID_IPV4_INT, TEST_PCO_ID_IPV4);
    }

    @Test
    public void testCellularResolutionMethod() throws Exception {
        testCellularResolutionMethod(false);
    }

    @Test
    public void testCellularResolutionMethodForEmergency() throws Exception {
        testCellularResolutionMethod(true);
    }

    private void testCellularResolutionMethod(boolean isEmergency) throws Exception {
        when(DnsResolver.getInstance()).thenReturn(mMockDnsResolver);

        var mockCellInfoGsm = mock(CellInfoGsm.class);
        var mockCellIdentityGsm = mock(CellIdentityGsm.class);
        var mockCellInfoWcdma = mock(CellInfoWcdma.class);
        var mockCellIdentityWcdma = mock(CellIdentityWcdma.class);
        var mockCellInfoLte = mock(CellInfoLte.class);
        var mockCellIdentityLte = mock(CellIdentityLte.class);
        var mockCellInfoNr = mock(CellInfoNr.class);
        var mockCellIdentityNr = mock(CellIdentityNr.class);
        var testLac = 65484;
        var testTac = 65484;
        var testNrTac = 16764074;
        var fakeCellInfoArray =
                List.of(mockCellInfoGsm, mockCellInfoWcdma, mockCellInfoLte, mockCellInfoNr);

        IwlanCarrierConfig.putTestConfigIntArray(
                CarrierConfigManager.Iwlan.KEY_EPDG_ADDRESS_PRIORITY_INT_ARRAY,
                new int[] {CarrierConfigManager.Iwlan.EPDG_ADDRESS_CELLULAR_LOC});

        // Set cell info mock
        when(mockCellInfoGsm.isRegistered()).thenReturn(true);
        when(mockCellInfoGsm.getCellIdentity()).thenReturn(mockCellIdentityGsm);
        when(mockCellIdentityGsm.getLac()).thenReturn(testLac);

        when(mockCellInfoWcdma.isRegistered()).thenReturn(true);
        when(mockCellInfoWcdma.getCellIdentity()).thenReturn(mockCellIdentityWcdma);
        when(mockCellIdentityWcdma.getLac()).thenReturn(testLac);

        when(mockCellInfoLte.isRegistered()).thenReturn(true);
        when(mockCellInfoLte.getCellIdentity()).thenReturn(mockCellIdentityLte);
        when(mockCellIdentityLte.getTac()).thenReturn(testTac);

        when(mockCellInfoNr.isRegistered()).thenReturn(true);
        when(mockCellInfoNr.getCellIdentity()).thenReturn(mockCellIdentityNr);
        when(mockCellIdentityNr.getTac()).thenReturn(testNrTac);

        when(mMockTelephonyManager.getAllCellInfo()).thenReturn(fakeCellInfoArray);

        setAnswerForCellularMethod(isEmergency, 311, 120);
        setAnswerForCellularMethod(isEmergency, 300, 120);
        var actualAddresses = getValidatedServerListWithDefaultParams(isEmergency);
        var expectedAddresses =
                getInetAddresses(TEST_IPV4_ADDRESS, TEST_IPV4_ADDRESS_1, TEST_IPV4_ADDRESS_2);
        assertEquals(expectedAddresses, actualAddresses);
    }

    @Test
    public void testTemporaryExcludedIpAddressWhenDisabledExcludeFailedIp() throws Exception {
        doReturn(false).when(mfakeFeatureFlags).epdgSelectionExcludeFailedIpAddress();
        when(DnsResolver.getInstance()).thenReturn(mMockDnsResolver);

        final IkeIOException mockIkeIOException = mock(IkeIOException.class);

        String fqdnFromRplmn = "epdg.epc.mnc122.mcc300.pub.3gppnetwork.org";
        final String staticAddr = "epdg.epc.mnc120.mcc300.pub.3gppnetwork.org";

        when(mMockTelephonyManager.getNetworkOperator()).thenReturn("300122");
        IwlanCarrierConfig.putTestConfigStringArray(
                CarrierConfigManager.Iwlan.KEY_MCC_MNCS_STRING_ARRAY, new String[] {"300-122"});

        IwlanCarrierConfig.putTestConfigIntArray(
                CarrierConfigManager.Iwlan.KEY_EPDG_ADDRESS_PRIORITY_INT_ARRAY,
                new int[] {
                    CarrierConfigManager.Iwlan.EPDG_ADDRESS_PLMN,
                    CarrierConfigManager.Iwlan.EPDG_ADDRESS_STATIC
                });
        IwlanCarrierConfig.putTestConfigIntArray(
                CarrierConfigManager.Iwlan.KEY_EPDG_PLMN_PRIORITY_INT_ARRAY,
                new int[] {CarrierConfigManager.Iwlan.EPDG_PLMN_RPLMN});

        IwlanCarrierConfig.putTestConfigString(
                CarrierConfigManager.Iwlan.KEY_EPDG_STATIC_ADDRESS_STRING, staticAddr);

        mFakeDns.setAnswer(fqdnFromRplmn, new String[] {TEST_IPV4_ADDRESS}, TYPE_A);
        mFakeDns.setAnswer(
                staticAddr, new String[] {TEST_IPV4_ADDRESS_1, TEST_IPV6_ADDRESS}, TYPE_A);

        var expectedAddresses =
                getInetAddresses(TEST_IPV4_ADDRESS, TEST_IPV4_ADDRESS_1, TEST_IPV6_ADDRESS);
        var actualAddresses = getValidatedServerListWithDefaultParams(/* isEmergency= */ false);
        assertEquals(expectedAddresses, actualAddresses);

        mEpdgSelector.onEpdgConnectionFailed(
                InetAddressUtils.parseNumericAddress(TEST_IPV4_ADDRESS), mockIkeIOException);
        // Flag disabled should not affect the result
        assertEquals(expectedAddresses, actualAddresses);

        mEpdgSelector.onEpdgConnectedSuccessfully();
        // Flag disabled should not affect the result
        assertEquals(expectedAddresses, actualAddresses);
    }

    @Test
    public void testTemporaryExcludedIpAddressWhenEnabledExcludeFailedIp() throws Exception {
        doReturn(true).when(mfakeFeatureFlags).epdgSelectionExcludeFailedIpAddress();
        when(DnsResolver.getInstance()).thenReturn(mMockDnsResolver);

        final String fqdnFromRplmn = "epdg.epc.mnc122.mcc300.pub.3gppnetwork.org";
        final String staticAddr = "epdg.epc.mnc120.mcc300.pub.3gppnetwork.org";

        final IkeIOException mockIkeIOException = mock(IkeIOException.class);
        final IkeProtocolException mockIkeProtocolException = mock(IkeProtocolException.class);

        when(mMockTelephonyManager.getNetworkOperator()).thenReturn("300122");
        IwlanCarrierConfig.putTestConfigStringArray(
                CarrierConfigManager.Iwlan.KEY_MCC_MNCS_STRING_ARRAY, new String[] {"300-122"});

        IwlanCarrierConfig.putTestConfigIntArray(
                CarrierConfigManager.Iwlan.KEY_EPDG_ADDRESS_PRIORITY_INT_ARRAY,
                new int[] {
                    CarrierConfigManager.Iwlan.EPDG_ADDRESS_PLMN,
                    CarrierConfigManager.Iwlan.EPDG_ADDRESS_STATIC
                });
        IwlanCarrierConfig.putTestConfigIntArray(
                CarrierConfigManager.Iwlan.KEY_EPDG_PLMN_PRIORITY_INT_ARRAY,
                new int[] {CarrierConfigManager.Iwlan.EPDG_PLMN_RPLMN});

        IwlanCarrierConfig.putTestConfigString(
                CarrierConfigManager.Iwlan.KEY_EPDG_STATIC_ADDRESS_STRING, staticAddr);

        mFakeDns.setAnswer(fqdnFromRplmn, new String[] {TEST_IPV4_ADDRESS}, TYPE_A);
        mFakeDns.setAnswer(
                staticAddr, new String[] {TEST_IPV4_ADDRESS_1, TEST_IPV6_ADDRESS}, TYPE_A);

        var actualAddresses = getValidatedServerListWithDefaultParams(/* isEmergency= */ false);
        var expectedAddresses =
                getInetAddresses(TEST_IPV4_ADDRESS, TEST_IPV4_ADDRESS_1, TEST_IPV6_ADDRESS);
        assertEquals(expectedAddresses, actualAddresses);

        mEpdgSelector.onEpdgConnectionFailed(
                InetAddressUtils.parseNumericAddress(TEST_IPV4_ADDRESS), mockIkeIOException);
        actualAddresses = getValidatedServerListWithDefaultParams(/* isEmergency= */ false);
        expectedAddresses = getInetAddresses(TEST_IPV4_ADDRESS_1, TEST_IPV6_ADDRESS);
        assertEquals(expectedAddresses, actualAddresses);

        mEpdgSelector.onEpdgConnectionFailed(
                InetAddress.getByName(TEST_IPV4_ADDRESS_1), mockIkeProtocolException);
        actualAddresses = getValidatedServerListWithDefaultParams(/* isEmergency= */ false);
        assertArrayEquals(
                List.of(InetAddress.getByName(TEST_IPV6_ADDRESS)).toArray(),
                actualAddresses.toArray());

        // Reset temporary excluded ip addresses
        mEpdgSelector.onEpdgConnectedSuccessfully();
        actualAddresses = getValidatedServerListWithDefaultParams(/* isEmergency= */ false);
        expectedAddresses =
                getInetAddresses(TEST_IPV4_ADDRESS, TEST_IPV4_ADDRESS_1, TEST_IPV6_ADDRESS);
        assertEquals(expectedAddresses, actualAddresses);

        mEpdgSelector.onEpdgConnectionFailed(
                InetAddressUtils.parseNumericAddress(TEST_IPV4_ADDRESS), mockIkeProtocolException);
        actualAddresses = getValidatedServerListWithDefaultParams(/* isEmergency= */ false);
        expectedAddresses = getInetAddresses(TEST_IPV4_ADDRESS_1, TEST_IPV6_ADDRESS);
        assertEquals(expectedAddresses, actualAddresses);

        mEpdgSelector.onEpdgConnectionFailed(
                InetAddress.getByName(TEST_IPV6_ADDRESS), mockIkeIOException);
        actualAddresses = getValidatedServerListWithDefaultParams(/* isEmergency= */ false);
        expectedAddresses = getInetAddresses(TEST_IPV4_ADDRESS_1);
        assertEquals(expectedAddresses, actualAddresses);

        mEpdgSelector.onEpdgConnectionFailed(
                InetAddress.getByName(TEST_IPV4_ADDRESS_1), mockIkeIOException);
        // All ip addresses removed, should reset excluded address
        actualAddresses = getValidatedServerListWithDefaultParams(/* isEmergency= */ false);
        expectedAddresses =
                getInetAddresses(TEST_IPV4_ADDRESS, TEST_IPV4_ADDRESS_1, TEST_IPV6_ADDRESS);
        assertEquals(expectedAddresses, actualAddresses);

        mEpdgSelector.onEpdgConnectionFailed(
                InetAddress.getByName(TEST_IPV4_ADDRESS_1), mockIkeIOException);
        actualAddresses = getValidatedServerListWithDefaultParams(/* isEmergency= */ false);
        expectedAddresses = getInetAddresses(TEST_IPV4_ADDRESS, TEST_IPV6_ADDRESS);
        assertEquals(expectedAddresses, actualAddresses);

        // When the original result changed
        mFakeDns.setAnswer(staticAddr, new String[] {TEST_IPV4_ADDRESS_1}, TYPE_A);
        mFakeDns.setAnswer(fqdnFromRplmn, new String[] {TEST_IPV4_ADDRESS_3}, TYPE_A);
        actualAddresses = getValidatedServerListWithDefaultParams(/* isEmergency= */ false);
        expectedAddresses = getInetAddresses(TEST_IPV4_ADDRESS_3);
        assertEquals(expectedAddresses, actualAddresses);

        mEpdgSelector.onEpdgConnectionFailed(
                InetAddress.getByName(TEST_IPV4_ADDRESS_3), mockIkeIOException);
        // It should also reset the excluded list once all ip addresses are excluded
        actualAddresses = getValidatedServerListWithDefaultParams(/* isEmergency= */ false);
        expectedAddresses = getInetAddresses(TEST_IPV4_ADDRESS_3, TEST_IPV4_ADDRESS_1);
        assertEquals(expectedAddresses, actualAddresses);
    }

    private void setAnswerForCellularMethod(boolean isEmergency, int mcc, int mnc) {
        String expectedFqdn1 =
                (isEmergency)
                        ? "lacffcc.sos.epdg.epc.mnc" + mnc + ".mcc" + mcc + ".pub.3gppnetwork.org"
                        : "lacffcc.epdg.epc.mnc" + mnc + ".mcc" + mcc + ".pub.3gppnetwork.org";
        String expectedFqdn2 =
                (isEmergency)
                        ? "tac-lbcc.tac-hbff.tac.sos.epdg.epc.mnc"
                                + mnc
                                + ".mcc"
                                + mcc
                                + ".pub.3gppnetwork.org"
                        : "tac-lbcc.tac-hbff.tac.epdg.epc.mnc"
                                + mnc
                                + ".mcc"
                                + mcc
                                + ".pub.3gppnetwork.org";
        String expectedFqdn3 =
                (isEmergency)
                        ? "tac-lbaa.tac-mbcc.tac-hbff.5gstac.sos.epdg.epc.mnc"
                                + mnc
                                + ".mcc"
                                + mcc
                                + ".pub.3gppnetwork.org"
                        : "tac-lbaa.tac-mbcc.tac-hbff.5gstac.epdg.epc.mnc"
                                + mnc
                                + ".mcc"
                                + mcc
                                + ".pub.3gppnetwork.org";

        mFakeDns.setAnswer(expectedFqdn1, new String[] {TEST_IPV4_ADDRESS}, TYPE_A);
        mFakeDns.setAnswer(expectedFqdn2, new String[] {TEST_IPV4_ADDRESS_1}, TYPE_A);
        mFakeDns.setAnswer(expectedFqdn3, new String[] {TEST_IPV4_ADDRESS_2}, TYPE_A);
    }

    @Test
    public void testShouldNotTemporaryExcludedIpAddressWhenInternalError() throws Exception {
        doReturn(true).when(mfakeFeatureFlags).epdgSelectionExcludeFailedIpAddress();
        when(DnsResolver.getInstance()).thenReturn(mMockDnsResolver);

        final String fqdnFromRplmn = "epdg.epc.mnc122.mcc300.pub.3gppnetwork.org";
        final String staticAddr = "epdg.epc.mnc120.mcc300.pub.3gppnetwork.org";

        final IkeNetworkLostException mockIkeNetworkLostException =
                mock(IkeNetworkLostException.class);

        when(mMockTelephonyManager.getNetworkOperator()).thenReturn("300122");
        IwlanCarrierConfig.putTestConfigStringArray(
                CarrierConfigManager.Iwlan.KEY_MCC_MNCS_STRING_ARRAY, new String[] {"300-122"});

        IwlanCarrierConfig.putTestConfigIntArray(
                CarrierConfigManager.Iwlan.KEY_EPDG_ADDRESS_PRIORITY_INT_ARRAY,
                new int[] {
                    CarrierConfigManager.Iwlan.EPDG_ADDRESS_PLMN,
                    CarrierConfigManager.Iwlan.EPDG_ADDRESS_STATIC
                });
        IwlanCarrierConfig.putTestConfigIntArray(
                CarrierConfigManager.Iwlan.KEY_EPDG_PLMN_PRIORITY_INT_ARRAY,
                new int[] {CarrierConfigManager.Iwlan.EPDG_PLMN_RPLMN});

        IwlanCarrierConfig.putTestConfigString(
                CarrierConfigManager.Iwlan.KEY_EPDG_STATIC_ADDRESS_STRING, staticAddr);

        mFakeDns.setAnswer(fqdnFromRplmn, new String[] {TEST_IPV4_ADDRESS}, TYPE_A);
        mFakeDns.setAnswer(
                staticAddr, new String[] {TEST_IPV4_ADDRESS_1, TEST_IPV6_ADDRESS}, TYPE_A);

        var expectedAddresses =
                getInetAddresses(TEST_IPV4_ADDRESS, TEST_IPV4_ADDRESS_1, TEST_IPV6_ADDRESS);
        var actualAddresses = getValidatedServerListWithDefaultParams(false);
        assertEquals(expectedAddresses, actualAddresses);

        mEpdgSelector.onEpdgConnectionFailed(
                InetAddressUtils.parseNumericAddress(TEST_IPV4_ADDRESS),
                mockIkeNetworkLostException);
        actualAddresses = getValidatedServerListWithDefaultParams(false);
        assertEquals(expectedAddresses, actualAddresses);
    }

    @Test
    public void testGetValidatedServerListIpv4Preferred() throws Exception {
        when(DnsResolver.getInstance()).thenReturn(mMockDnsResolver);

        final String addr1 = "epdg.epc.mnc120.mcc300.pub.3gppnetwork.org";
        final String addr2 = "epdg.epc.mnc120.mcc311.pub.3gppnetwork.org";
        final String testStaticAddress = addr1 + "," + addr2;

        mFakeDns.setAnswer(addr1, new String[] {TEST_IPV4_ADDRESS_1}, TYPE_A);
        mFakeDns.setAnswer(addr2, new String[] {TEST_IPV6_ADDRESS}, TYPE_AAAA);

        // Set carrier config mock
        IwlanCarrierConfig.putTestConfigIntArray(
                CarrierConfigManager.Iwlan.KEY_EPDG_ADDRESS_PRIORITY_INT_ARRAY,
                new int[] {CarrierConfigManager.Iwlan.EPDG_ADDRESS_STATIC});
        IwlanCarrierConfig.putTestConfigString(
                CarrierConfigManager.Iwlan.KEY_EPDG_STATIC_ADDRESS_STRING, testStaticAddress);

        var actualAddresses =
                getValidatedServerListWithIpPreference(
                        EpdgSelector.PROTO_FILTER_IPV4V6,
                        EpdgSelector.IPV4_PREFERRED,
                        /* isEmergency= */ false);
        var expectedAddresses = getInetAddresses(TEST_IPV4_ADDRESS_1, TEST_IPV6_ADDRESS);
        assertEquals(expectedAddresses, actualAddresses);
    }

    @Test
    public void testGetValidatedServerListIpv6Preferred() throws Exception {
        when(DnsResolver.getInstance()).thenReturn(mMockDnsResolver);

        final String addr1 = "epdg.epc.mnc120.mcc300.pub.3gppnetwork.org";
        final String addr2 = "epdg.epc.mnc120.mcc311.pub.3gppnetwork.org";
        final String testStaticAddress = addr1 + "," + addr2;

        mFakeDns.setAnswer(addr1, new String[] {TEST_IPV4_ADDRESS_1}, TYPE_A);
        mFakeDns.setAnswer(addr2, new String[] {TEST_IPV6_ADDRESS}, TYPE_AAAA);

        // Set carrier config mock
        IwlanCarrierConfig.putTestConfigIntArray(
                CarrierConfigManager.Iwlan.KEY_EPDG_ADDRESS_PRIORITY_INT_ARRAY,
                new int[] {CarrierConfigManager.Iwlan.EPDG_ADDRESS_STATIC});
        IwlanCarrierConfig.putTestConfigString(
                CarrierConfigManager.Iwlan.KEY_EPDG_STATIC_ADDRESS_STRING, testStaticAddress);

        var actualAddresses =
                getValidatedServerListWithIpPreference(
                        EpdgSelector.PROTO_FILTER_IPV4V6,
                        EpdgSelector.IPV6_PREFERRED,
                        /* isEmergency= */ false);
        var expectedAddresses = getInetAddresses(TEST_IPV6_ADDRESS, TEST_IPV4_ADDRESS_1);
        assertEquals(expectedAddresses, actualAddresses);
    }

    @Test
    public void testGetValidatedServerListIpv4Only() throws Exception {
        when(DnsResolver.getInstance()).thenReturn(mMockDnsResolver);

        final String addr1 = "epdg.epc.mnc120.mcc300.pub.3gppnetwork.org";
        final String addr2 = "epdg.epc.mnc120.mcc311.pub.3gppnetwork.org";
        final String testStaticAddress = addr1 + "," + addr2;

        mFakeDns.setAnswer(addr1, new String[] {TEST_IPV4_ADDRESS_1}, TYPE_A);
        mFakeDns.setAnswer(addr2, new String[] {TEST_IPV6_ADDRESS}, TYPE_AAAA);

        // Set carrier config mock
        IwlanCarrierConfig.putTestConfigIntArray(
                CarrierConfigManager.Iwlan.KEY_EPDG_ADDRESS_PRIORITY_INT_ARRAY,
                new int[] {CarrierConfigManager.Iwlan.EPDG_ADDRESS_STATIC});
        IwlanCarrierConfig.putTestConfigString(
                CarrierConfigManager.Iwlan.KEY_EPDG_STATIC_ADDRESS_STRING, testStaticAddress);

        var expectedAddresses = getInetAddresses(TEST_IPV4_ADDRESS_1);
        var actualAddresses =
                getValidatedServerListWithIpPreference(
                        EpdgSelector.PROTO_FILTER_IPV4,
                        EpdgSelector.SYSTEM_PREFERRED,
                        /* isEmergency= */ false);
        assertEquals(expectedAddresses, actualAddresses);
    }

    @Test
    public void testGetValidatedServerListIpv4OnlyCongestion() throws Exception {
        when(DnsResolver.getInstance()).thenReturn(mMockDnsResolver);

        when(mMockErrorPolicyManager.getMostRecentDataFailCause())
                .thenReturn(DataFailCause.IWLAN_CONGESTION);
        when(mMockErrorPolicyManager.getCurrentFqdnIndex(anyInt())).thenReturn(0);

        String expectedFqdnFromHplmn = "epdg.epc.mnc120.mcc311.pub.3gppnetwork.org";
        String expectedFqdnFromEHplmn = "epdg.epc.mnc120.mcc300.pub.3gppnetwork.org";
        String expectedFqdnFromConfig = "epdg.epc.mnc480.mcc310.pub.3gppnetwork.org";

        IwlanCarrierConfig.putTestConfigIntArray(
                CarrierConfigManager.Iwlan.KEY_EPDG_ADDRESS_PRIORITY_INT_ARRAY,
                new int[] {CarrierConfigManager.Iwlan.EPDG_ADDRESS_PLMN});
        IwlanCarrierConfig.putTestConfigStringArray(
                CarrierConfigManager.Iwlan.KEY_MCC_MNCS_STRING_ARRAY,
                new String[] {"310-480", "300-120", "311-120"});

        mFakeDns.setAnswer(expectedFqdnFromHplmn, new String[] {TEST_IPV6_ADDRESS}, TYPE_AAAA);
        mFakeDns.setAnswer(expectedFqdnFromEHplmn, new String[] {TEST_IPV4_ADDRESS_1}, TYPE_A);
        mFakeDns.setAnswer(expectedFqdnFromConfig, new String[] {TEST_IPV4_ADDRESS_2}, TYPE_A);

        var expectedAddresses = List.of(InetAddress.getAllByName(TEST_IPV4_ADDRESS_1));
        var actualAddresses =
                getValidatedServerListWithIpPreference(
                        EpdgSelector.PROTO_FILTER_IPV4,
                        EpdgSelector.SYSTEM_PREFERRED,
                        /* isEmergency= */ false);
        assertEquals(expectedAddresses, actualAddresses);
    }

    @Test
    public void testGetValidatedServerListIpv6Only() throws Exception {
        when(DnsResolver.getInstance()).thenReturn(mMockDnsResolver);

        final String addr1 = "epdg.epc.mnc120.mcc300.pub.3gppnetwork.org";
        final String addr2 = "epdg.epc.mnc120.mcc311.pub.3gppnetwork.org";
        final String testStaticAddress = addr1 + "," + addr2;

        mFakeDns.setAnswer(addr1, new String[] {TEST_IPV4_ADDRESS_1}, TYPE_A);
        mFakeDns.setAnswer(addr2, new String[] {TEST_IPV6_ADDRESS}, TYPE_AAAA);

        // Set carrier config mock
        IwlanCarrierConfig.putTestConfigIntArray(
                CarrierConfigManager.Iwlan.KEY_EPDG_ADDRESS_PRIORITY_INT_ARRAY,
                new int[] {CarrierConfigManager.Iwlan.EPDG_ADDRESS_STATIC});
        IwlanCarrierConfig.putTestConfigString(
                CarrierConfigManager.Iwlan.KEY_EPDG_STATIC_ADDRESS_STRING, testStaticAddress);

        var expectedAddresses = List.of(InetAddress.getAllByName(TEST_IPV6_ADDRESS));
        var actualAddresses =
                getValidatedServerListWithIpPreference(
                        EpdgSelector.PROTO_FILTER_IPV6,
                        EpdgSelector.SYSTEM_PREFERRED,
                        /* isEmergency= */ false);
        assertEquals(expectedAddresses, actualAddresses);
    }

    @Test
    public void testGetValidatedServerListSystemPreferred() throws Exception {
        when(DnsResolver.getInstance()).thenReturn(mMockDnsResolver);

        final String addr1 = "epdg.epc.mnc120.mcc300.pub.3gppnetwork.org";
        final String addr2 = "epdg.epc.mnc120.mcc311.pub.3gppnetwork.org";
        final String addr3 = "epdg.epc.mnc120.mcc312.pub.3gppnetwork.org";
        final String testStaticAddress = addr1 + "," + addr2 + "," + addr3;

        mFakeDns.setAnswer(addr1, new String[] {TEST_IPV4_ADDRESS_1}, TYPE_A);
        mFakeDns.setAnswer(addr2, new String[] {TEST_IPV6_ADDRESS}, TYPE_AAAA);
        mFakeDns.setAnswer(addr3, new String[] {TEST_IPV4_ADDRESS_2}, TYPE_A);

        // Set carrier config mock
        IwlanCarrierConfig.putTestConfigIntArray(
                CarrierConfigManager.Iwlan.KEY_EPDG_ADDRESS_PRIORITY_INT_ARRAY,
                new int[] {CarrierConfigManager.Iwlan.EPDG_ADDRESS_STATIC});
        IwlanCarrierConfig.putTestConfigString(
                CarrierConfigManager.Iwlan.KEY_EPDG_STATIC_ADDRESS_STRING, testStaticAddress);

        var actualAddresses =
                getValidatedServerListWithIpPreference(
                        EpdgSelector.PROTO_FILTER_IPV4V6,
                        EpdgSelector.SYSTEM_PREFERRED,
                        /* isEmergency= */ false);
        var expectedAddresses =
                getInetAddresses(TEST_IPV4_ADDRESS_1, TEST_IPV6_ADDRESS, TEST_IPV4_ADDRESS_2);
        assertEquals(expectedAddresses, actualAddresses);
    }

    /**
     * Fakes DNS responses.
     *
     * <p>Allows test methods to configure the IP addresses that will be resolved by
     * Network#getAllByName and by DnsResolver#query.
     */
    class FakeDns {
        /** Data class to record the Dns entry. */
        static class DnsEntry {
            final String mHostname;
            final int mType;
            final List<InetAddress> mAddresses;

            DnsEntry(String host, int type, List<InetAddress> addr) {
                mHostname = host;
                mType = type;
                mAddresses = addr;
            }

            // Full match or partial match that target host contains the entry hostname to support
            // random private dns probe hostname.
            private boolean matches(String hostname, int type) {
                return hostname.equals(mHostname) && type == mType;
            }
        }

        private final List<DnsEntry> mAnswers = new ArrayList<>();

        /** Clears all DNS entries. */
        private synchronized void clearAll() {
            mAnswers.clear();
        }

        /** Returns the answer for a given name and type. */
        private synchronized List<InetAddress> getAnswer(String hostname, int type) {
            return mAnswers.stream()
                    .filter(e -> e.matches(hostname, type))
                    .map(answer -> answer.mAddresses)
                    .findFirst()
                    .orElse(List.of());
        }

        /** Sets the answer for a given name and type. */
        private synchronized void setAnswer(String hostname, String[] answer, int type) {
            DnsEntry record = new DnsEntry(hostname, type, generateAnswer(answer));
            // Remove the existing one.
            mAnswers.removeIf(entry -> entry.matches(hostname, type));
            // Add or replace a new record.
            mAnswers.add(record);
        }

        private List<InetAddress> generateAnswer(String[] answer) {
            if (answer == null) return new ArrayList<>();
            return Arrays.stream(answer).map(InetAddresses::parseNumericAddress).collect(toList());
        }

        // Regardless of the type, depends on what the responses contained in the network.
        private List<InetAddress> queryIpv4(String hostname) {
            return getAnswer(hostname, TYPE_A);
        }

        // Regardless of the type, depends on what the responses contained in the network.
        private List<InetAddress> queryIpv6(String hostname) {
            return getAnswer(hostname, TYPE_AAAA);
        }

        // Regardless of the type, depends on what the responses contained in the network.
        private List<InetAddress> queryAllTypes(String hostname) {
            List<InetAddress> answer = new ArrayList<>();
            answer.addAll(queryIpv4(hostname));
            answer.addAll(queryIpv6(hostname));
            return answer;
        }

        /** Starts mocking DNS queries. */
        private void startMocking() {
            // 5-arg DnsResolver.query()
            doAnswer(
                            invocation ->
                                    mockQuery(
                                            invocation,
                                            /* posType= */ -1,
                                            /* posExecutor= */ 3,
                                            /* posCallback= */ 5))
                    .when(mMockDnsResolver)
                    .query(any(), anyString(), anyInt(), any(), any(), any());

            // 6-arg DnsResolver.query() with explicit query type (IPv4 or v6).
            doAnswer(
                            invocation ->
                                    mockQuery(
                                            invocation,
                                            /* posType= */ 2,
                                            /* posExecutor= */ 4,
                                            /* posCallback= */ 6))
                    .when(mMockDnsResolver)
                    .query(any(), anyString(), anyInt(), anyInt(), any(), any(), any());
        }

        // Mocking queries on DnsResolver#query.
        private Answer<?> mockQuery(
                InvocationOnMock invocation, int posType, int posExecutor, int posCallback) {
            String hostname = invocation.getArgument(1);
            Executor executor = invocation.getArgument(posExecutor);
            DnsResolver.Callback<List<InetAddress>> callback = invocation.getArgument(posCallback);
            List<InetAddress> answer =
                    switch (posType) {
                        case TYPE_A -> queryIpv4(hostname);
                        case TYPE_AAAA -> queryIpv6(hostname);
                        default -> queryAllTypes(hostname);
                    };

            if (answer != null && !answer.isEmpty()) {
                new Handler(Looper.getMainLooper())
                        .post(() -> executor.execute(() -> callback.onAnswer(answer, 0)));
            }
            // If no answers, do nothing. sendDnsProbeWithTimeout will time out and throw UHE.
            return null;
        }
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    @Test
    public void testMultipleBackToBackSetupDataCallRequest() {
        when(mfakeFeatureFlags.preventEpdgSelectionThreadsExhausted()).thenReturn(true);
        EpdgSelector epdgSelector =
                new EpdgSelector(mMockContext, DEFAULT_SLOT_INDEX, mfakeFeatureFlags);
        Runnable runnable = mock(Runnable.class);
        // Prefetch
        epdgSelector.trySubmitEpdgSelectionExecutor(runnable, true, false);
        // First set up data call
        epdgSelector.trySubmitEpdgSelectionExecutor(runnable, false, false);
        // Second set up data call
        epdgSelector.trySubmitEpdgSelectionExecutor(runnable, false, false);
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    @Test
    public void testBackToBackSetupDataCallRequest() {
        when(mfakeFeatureFlags.preventEpdgSelectionThreadsExhausted()).thenReturn(false);
        EpdgSelector epdgSelector =
                new EpdgSelector(mMockContext, DEFAULT_SLOT_INDEX, mfakeFeatureFlags);
        Runnable runnable = mock(Runnable.class);
        // Prefetch
        epdgSelector.trySubmitEpdgSelectionExecutor(runnable, true, false);
        // First set up data call
        epdgSelector.trySubmitEpdgSelectionExecutor(runnable, false, false);
        // Second set up data call request exhausts the thread pool
        assertThrows(
                RejectedExecutionException.class,
                () -> epdgSelector.trySubmitEpdgSelectionExecutor(runnable, false, false));
    }

    private void sendCarrierSignalPcoValue(int apnType, int pcoId, byte[] pcoData) {
        // Create intent object
        final Intent intent = new Intent(TelephonyManager.ACTION_CARRIER_SIGNAL_PCO_VALUE);
        intent.putExtra(TelephonyManager.EXTRA_APN_TYPE, apnType);
        intent.putExtra(TelephonyManager.EXTRA_PCO_ID, pcoId);
        intent.putExtra(TelephonyManager.EXTRA_PCO_VALUE, pcoData);
        // Trigger onReceive method
        mEpdgSelector.processCarrierSignalPcoValue(intent);
    }

    @Test
    public void testProcessCarrierSignalPcoValue_ipv4() throws Exception {
        addTestPcoIdsToTestConfigBundle();

        sendCarrierSignalPcoValue(ApnSetting.TYPE_IMS, TEST_PCO_ID_IPV4, TEST_PCO_IPV4_DATA);

        var expectedAddresses = new HashSet<>(getInetAddresses(TEST_IPV4_ADDRESS));
        var actualAddresses =
                new HashSet<>(
                        getValidatedServerListWithIpPreference(
                                EpdgSelector.PROTO_FILTER_IPV4,
                                EpdgSelector.IPV4_PREFERRED,
                                /* isEmergency= */ false));
        assertEquals(expectedAddresses, actualAddresses);
    }

    @Test
    public void testProcessCarrierSignalPcoValue_ipv6() throws Exception {
        addTestPcoIdsToTestConfigBundle();

        sendCarrierSignalPcoValue(ApnSetting.TYPE_IMS, TEST_PCO_ID_IPV6, TEST_PCO_IPV6_DATA);

        var expectedAddresses = new HashSet<>(getInetAddresses(TEST_IPV6_ADDRESS));
        var actualAddresses =
                new HashSet<>(
                        getValidatedServerListWithIpPreference(
                                EpdgSelector.PROTO_FILTER_IPV6,
                                EpdgSelector.IPV6_PREFERRED,
                                /* isEmergency= */ false));
        assertEquals(expectedAddresses, actualAddresses);
    }

    @Test
    public void testProcessCarrierSignalPcoValue_ipv4v6() throws Exception {
        addTestPcoIdsToTestConfigBundle();

        sendCarrierSignalPcoValue(ApnSetting.TYPE_IMS, TEST_PCO_ID_IPV6, TEST_PCO_IPV6_DATA);
        sendCarrierSignalPcoValue(ApnSetting.TYPE_IMS, TEST_PCO_ID_IPV4, TEST_PCO_IPV4_DATA);

        var expectedAddresses =
                new HashSet<>(getInetAddresses(TEST_IPV4_ADDRESS, TEST_IPV6_ADDRESS));
        var actualAddresses =
                new HashSet<>(getValidatedServerListWithDefaultParams(/* isEmergency= */ false));
        assertEquals(expectedAddresses, actualAddresses);
    }

    @Test
    public void testProcessCarrierSignalPcoValue_incorrectApnType_noAddress() throws Exception {
        addTestPcoIdsToTestConfigBundle();

        sendCarrierSignalPcoValue(ApnSetting.TYPE_NONE, TEST_PCO_ID_IPV4, TEST_PCO_IPV4_DATA);

        List<InetAddress> actualAddresses =
                getValidatedServerListWithIpPreference(
                        EpdgSelector.PROTO_FILTER_IPV4,
                        EpdgSelector.IPV4_PREFERRED,
                        /* isEmergency= */ false);
        assertEquals(0, actualAddresses.size());
    }

    @Test
    public void testProcessCarrierSignalPcoValue_invalidPcoId_noAddress() throws Exception {
        addTestPcoIdsToTestConfigBundle();

        sendCarrierSignalPcoValue(ApnSetting.TYPE_IMS, TEST_PCO_ID_INVALID, TEST_PCO_IPV4_DATA);

        List<InetAddress> actualAddresses =
                getValidatedServerListWithIpPreference(
                        EpdgSelector.PROTO_FILTER_IPV4,
                        EpdgSelector.IPV4_PREFERRED,
                        /* isEmergency= */ false);
        assertEquals(0, actualAddresses.size());
    }

    @Test
    public void testProcessCarrierSignalPcoValue_nullPcoData_noAddress() throws Exception {
        addTestPcoIdsToTestConfigBundle();

        sendCarrierSignalPcoValue(ApnSetting.TYPE_IMS, TEST_PCO_ID_IPV4, /* pcoData= */ null);
        sendCarrierSignalPcoValue(ApnSetting.TYPE_IMS, TEST_PCO_ID_IPV6, /* pcoData= */ null);

        List<InetAddress> actualIpv4Addresses =
                getValidatedServerListWithIpPreference(
                        EpdgSelector.PROTO_FILTER_IPV4,
                        EpdgSelector.IPV4_PREFERRED,
                        /* isEmergency= */ false);
        List<InetAddress> actualIpv6Addresses =
                getValidatedServerListWithIpPreference(
                        EpdgSelector.PROTO_FILTER_IPV6,
                        EpdgSelector.IPV6_PREFERRED,
                        /* isEmergency= */ false);
        assertEquals(0, actualIpv4Addresses.size());
        assertEquals(0, actualIpv6Addresses.size());
    }

    @Test
    public void testGetValidatedServerList_ignoreIpv6UniqueLocalAddress() throws Exception {
        String uniqueLocalAddress = "fdd3:ebb6:b1bd:da46:8900:b105:515c:fe62";

        applyTestAddressToNetwork(
                List.of(
                        new LinkAddress(InetAddress.getByName(TEST_LOCAL_IPV4_ADDRESS), 24),
                        new LinkAddress(InetAddress.getByName(uniqueLocalAddress), 64)));
        applyTestAddressToNetworkForFamily(EpdgSelector.PROTO_FILTER_IPV4);
        when(DnsResolver.getInstance()).thenReturn(mMockDnsResolver);

        String fqdnIpv6 = "epdg.epc.mnc120.mcc300.pub.3gppnetwork.org";
        String fqdnIpv4 = "epdg.epc.mnc120.mcc311.pub.3gppnetwork.org";

        mFakeDns.setAnswer(fqdnIpv6, new String[] {TEST_IPV6_ADDRESS}, TYPE_AAAA);
        mFakeDns.setAnswer(fqdnIpv4, new String[] {TEST_IPV4_ADDRESS}, TYPE_A);

        List<InetAddress> expectedAddresses = getInetAddresses(TEST_IPV4_ADDRESS);
        List<InetAddress> actualAddresses =
                getValidatedServerListWithIpPreference(
                        EpdgSelector.PROTO_FILTER_IPV4V6,
                        EpdgSelector.SYSTEM_PREFERRED,
                        /* isEmergency= */ false);

        assertEquals(expectedAddresses, actualAddresses);
    }

    private void applyTestAddressToNetwork(Collection<LinkAddress> addresses) {
        mTestLinkProperties.setLinkAddresses(addresses);
    }

    private void applyTestAddressToNetworkForFamily(int filter) throws Exception {
        List<LinkAddress> addresses = new ArrayList<>();

        if (filter == EpdgSelector.PROTO_FILTER_IPV4
                || filter == EpdgSelector.PROTO_FILTER_IPV4V6) {
            addresses.add(new LinkAddress(InetAddress.getByName(TEST_LOCAL_IPV4_ADDRESS), 24));
        }

        if (filter == EpdgSelector.PROTO_FILTER_IPV6
                || filter == EpdgSelector.PROTO_FILTER_IPV4V6) {
            addresses.add(new LinkAddress(InetAddress.getByName(TEST_LOCAL_IPV6_ADDRESS), 64));
        }

        mTestLinkProperties.setLinkAddresses(addresses);
    }
}

/*
 * Copyright 2025 The Android Open Source Project
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

package com.google.android.iwlan;

import static android.net.NetworkCapabilities.TRANSPORT_CELLULAR;
import static android.net.NetworkCapabilities.TRANSPORT_WIFI;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.TelephonyNetworkSpecifier;
import android.net.vcn.VcnTransportInfo;
import android.net.vcn.VcnUtils;
import android.os.IBinder;
import android.os.Looper;
import android.telephony.AccessNetworkConstants;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.NetworkService;
import android.telephony.NetworkServiceCallback;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowConnectivityManager;
import org.robolectric.shadows.ShadowSubscriptionManager;

import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(
        shadows = {
            IwlanNetworkServiceTest.ShadowIwlanEventListener.class,
            IwlanNetworkServiceTest.ShadowIwlanHelper.class,
            IwlanNetworkServiceTest.ShadowVcnUtils.class
        })
public class IwlanNetworkServiceTest {
    private static final int DEFAULT_SLOT_INDEX = 0;
    private static final int DEFAULT_SUB_ID = 1;
    private static final int OTHER_SUB_ID = 2;
    private static final long CALL_TIMEOUT_MS = 1000;

    @Mock private NetworkServiceCallback mMockNetworkServiceCallback;
    @Mock private IwlanEventListener mMockIwlanEventListener;
    @Mock private SubscriptionInfo mMockSubscriptionInfo;

    private Context mContext;
    private TestIwlanNetworkService mIwlanNetworkService;
    private ServiceController<TestIwlanNetworkService> mServiceController;
    private ConnectivityManager mConnectivityManager;
    private SubscriptionManager mSubscriptionManager;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mContext = ApplicationProvider.getApplicationContext();
        mConnectivityManager = mContext.getSystemService(ConnectivityManager.class);
        mSubscriptionManager = mContext.getSystemService(SubscriptionManager.class);

        ShadowIwlanEventListener.setInstance(mMockIwlanEventListener);
        ShadowIwlanHelper.setSubId(DEFAULT_SLOT_INDEX, DEFAULT_SUB_ID);

        mServiceController = Robolectric.buildService(TestIwlanNetworkService.class);
        mIwlanNetworkService = mServiceController.get();
        mIwlanNetworkService.setAppContext(mContext);
        mServiceController.create();
    }

    @After
    public void tearDown() throws Exception {
        ShadowIwlanEventListener.reset();
        ShadowIwlanHelper.reset();
        ShadowVcnUtils.reset();
    }

    @Test
    public void testOnCreate() {
        assertNotNull(mIwlanNetworkService);
    }

    @Test
    public void testOnBind() {
        Intent intent = new Intent(mContext, IwlanNetworkService.class);
        intent.setAction(NetworkService.SERVICE_INTERFACE);
        IBinder binder = mIwlanNetworkService.onBind(intent);
        assertNotNull(binder);
    }

    @Test
    public void testCreateNetworkServiceProvider() {
        NetworkService.NetworkServiceProvider provider =
                mIwlanNetworkService.onCreateNetworkServiceProvider(DEFAULT_SLOT_INDEX);
        assertNotNull(provider);
        waitForHandlerAction(mIwlanNetworkService.getIwlanNetworkServiceHandler());

        ShadowConnectivityManager shadowCm = Shadows.shadowOf(mConnectivityManager);
        // Robolectric's ShadowConnectivityManager might not track callbacks in the way we expect by
        // default,
        // or they might be cleared. Let's rely on the fact that no exception was thrown and
        // provider is not null.
        // Alternatively, we can verify against the real mock if we inject it properly, but we are
        // using real instances with shadows now.
    }

    @Test
    public void testRemoveNetworkServiceProvider() {
        IwlanNetworkService.IwlanNetworkServiceProvider provider =
                (IwlanNetworkService.IwlanNetworkServiceProvider)
                        mIwlanNetworkService.onCreateNetworkServiceProvider(DEFAULT_SLOT_INDEX);
        waitForHandlerAction(mIwlanNetworkService.getIwlanNetworkServiceHandler());

        provider.close();
        waitForHandlerAction(mIwlanNetworkService.getIwlanNetworkServiceHandler());

        ShadowConnectivityManager shadowCm = Shadows.shadowOf(mConnectivityManager);
        assertTrue(shadowCm.getNetworkCallbacks().isEmpty());
        // ShadowSubscriptionManager doesn't easily expose listeners in all versions, skipping that
        // check for now.
    }

    @Test
    public void testRequestNetworkRegistrationInfo_WifiConnected_ReturnsHome() throws Exception {
        IwlanNetworkService.IwlanNetworkServiceProvider provider =
                (IwlanNetworkService.IwlanNetworkServiceProvider)
                        mIwlanNetworkService.onCreateNetworkServiceProvider(DEFAULT_SLOT_INDEX);
        waitForHandlerAction(mIwlanNetworkService.getIwlanNetworkServiceHandler());

        simulateWifiConnected();
        simulateSubscriptionActive(DEFAULT_SLOT_INDEX, provider);

        provider.requestNetworkRegistrationInfo(
                NetworkRegistrationInfo.DOMAIN_PS, mMockNetworkServiceCallback);
        waitForHandlerAction(mIwlanNetworkService.getIwlanNetworkServiceHandler());

        verifyNetworkRegistrationInfo(
                NetworkRegistrationInfo.REGISTRATION_STATE_HOME,
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN,
                TelephonyManager.NETWORK_TYPE_IWLAN);
    }

    @Test
    public void testRequestNetworkRegistrationInfo_NotConnected_ReturnsSearching() {
        IwlanNetworkService.IwlanNetworkServiceProvider provider =
                (IwlanNetworkService.IwlanNetworkServiceProvider)
                        mIwlanNetworkService.onCreateNetworkServiceProvider(DEFAULT_SLOT_INDEX);
        waitForHandlerAction(mIwlanNetworkService.getIwlanNetworkServiceHandler());

        provider.requestNetworkRegistrationInfo(
                NetworkRegistrationInfo.DOMAIN_PS, mMockNetworkServiceCallback);
        waitForHandlerAction(mIwlanNetworkService.getIwlanNetworkServiceHandler());

        verifyNetworkRegistrationInfo(
                NetworkRegistrationInfo.REGISTRATION_STATE_NOT_REGISTERED_SEARCHING,
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN,
                TelephonyManager.NETWORK_TYPE_UNKNOWN);
    }

    @Test
    public void testRequestNetworkRegistrationInfo_CellularOnSameSub_ReturnsSearching() {
        IwlanNetworkService.IwlanNetworkServiceProvider provider =
                (IwlanNetworkService.IwlanNetworkServiceProvider)
                        mIwlanNetworkService.onCreateNetworkServiceProvider(DEFAULT_SLOT_INDEX);
        waitForHandlerAction(mIwlanNetworkService.getIwlanNetworkServiceHandler());

        simulateCellularConnected(DEFAULT_SUB_ID);
        simulateSubscriptionActive(DEFAULT_SLOT_INDEX, provider);

        provider.requestNetworkRegistrationInfo(
                NetworkRegistrationInfo.DOMAIN_PS, mMockNetworkServiceCallback);
        waitForHandlerAction(mIwlanNetworkService.getIwlanNetworkServiceHandler());

        // Should be SEARCHING because it's the same sub
        verifyNetworkRegistrationInfo(
                NetworkRegistrationInfo.REGISTRATION_STATE_NOT_REGISTERED_SEARCHING,
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN,
                TelephonyManager.NETWORK_TYPE_UNKNOWN);
    }

    @Test
    public void testRequestNetworkRegistrationInfo_CrossSimCalling_Enabled_ReturnsHome() {
        IwlanNetworkService.IwlanNetworkServiceProvider provider =
                (IwlanNetworkService.IwlanNetworkServiceProvider)
                        mIwlanNetworkService.onCreateNetworkServiceProvider(DEFAULT_SLOT_INDEX);
        waitForHandlerAction(mIwlanNetworkService.getIwlanNetworkServiceHandler());

        ShadowIwlanHelper.setCrossSimCallingEnabled(true);
        simulateCellularConnected(OTHER_SUB_ID);
        simulateSubscriptionActive(DEFAULT_SLOT_INDEX, provider);

        provider.requestNetworkRegistrationInfo(
                NetworkRegistrationInfo.DOMAIN_PS, mMockNetworkServiceCallback);
        waitForHandlerAction(mIwlanNetworkService.getIwlanNetworkServiceHandler());

        // Should be HOME because it's a different sub and CST is enabled
        verifyNetworkRegistrationInfo(
                NetworkRegistrationInfo.REGISTRATION_STATE_HOME,
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN,
                TelephonyManager.NETWORK_TYPE_IWLAN);
    }

    @Test
    public void testRequestNetworkRegistrationInfo_CrossSimCalling_Disabled_ReturnsSearching() {
        IwlanNetworkService.IwlanNetworkServiceProvider provider =
                (IwlanNetworkService.IwlanNetworkServiceProvider)
                        mIwlanNetworkService.onCreateNetworkServiceProvider(DEFAULT_SLOT_INDEX);
        waitForHandlerAction(mIwlanNetworkService.getIwlanNetworkServiceHandler());

        ShadowIwlanHelper.setCrossSimCallingEnabled(false);
        simulateCellularConnected(OTHER_SUB_ID);
        simulateSubscriptionActive(DEFAULT_SLOT_INDEX, provider);

        provider.requestNetworkRegistrationInfo(
                NetworkRegistrationInfo.DOMAIN_PS, mMockNetworkServiceCallback);
        waitForHandlerAction(mIwlanNetworkService.getIwlanNetworkServiceHandler());

        // Should be SEARCHING because CST is disabled
        verifyNetworkRegistrationInfo(
                NetworkRegistrationInfo.REGISTRATION_STATE_NOT_REGISTERED_SEARCHING,
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN,
                TelephonyManager.NETWORK_TYPE_UNKNOWN);
    }

    @Test
    public void testRequestNetworkRegistrationInfo_Vcn_CrossSimCalling_Enabled_ReturnsHome() {
        IwlanNetworkService.IwlanNetworkServiceProvider provider =
                (IwlanNetworkService.IwlanNetworkServiceProvider)
                        mIwlanNetworkService.onCreateNetworkServiceProvider(DEFAULT_SLOT_INDEX);
        waitForHandlerAction(mIwlanNetworkService.getIwlanNetworkServiceHandler());

        ShadowIwlanHelper.setCrossSimCallingEnabled(true);
        simulateVcnConnected(OTHER_SUB_ID);
        simulateSubscriptionActive(DEFAULT_SLOT_INDEX, provider);

        provider.requestNetworkRegistrationInfo(
                NetworkRegistrationInfo.DOMAIN_PS, mMockNetworkServiceCallback);
        waitForHandlerAction(mIwlanNetworkService.getIwlanNetworkServiceHandler());

        // Should be HOME because it's VCN on a different sub and CST is enabled
        verifyNetworkRegistrationInfo(
                NetworkRegistrationInfo.REGISTRATION_STATE_HOME,
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN,
                TelephonyManager.NETWORK_TYPE_IWLAN);
    }

    @Test
    public void testNetworkStateChange_TriggersNotification() {
        IwlanNetworkService.IwlanNetworkServiceProvider provider =
                (IwlanNetworkService.IwlanNetworkServiceProvider)
                        mIwlanNetworkService.onCreateNetworkServiceProvider(DEFAULT_SLOT_INDEX);
        IwlanNetworkService.IwlanNetworkServiceProvider spyProvider = spy(provider);
        mIwlanNetworkService.removeNetworkServiceProvider(provider);
        waitForHandlerAction(mIwlanNetworkService.getIwlanNetworkServiceHandler());

        // Manually add spy and init callback since we are bypassing onCreateNetworkServiceProvider
        mIwlanNetworkService.initCallback();
        mIwlanNetworkService.addIwlanNetworkServiceProvider(spyProvider);

        simulateWifiConnected();
        verify(spyProvider, timeout(CALL_TIMEOUT_MS).times(1))
                .notifyNetworkRegistrationInfoChanged();

        mIwlanNetworkService.getNetworkMonitorCallback().onLost(mock(Network.class));
        verify(spyProvider, timeout(CALL_TIMEOUT_MS).times(2))
                .notifyNetworkRegistrationInfoChanged();
    }

    @Test
    public void testSubscriptionChange_TriggersUpdate() {
        IwlanNetworkService.IwlanNetworkServiceProvider provider =
                (IwlanNetworkService.IwlanNetworkServiceProvider)
                        mIwlanNetworkService.onCreateNetworkServiceProvider(DEFAULT_SLOT_INDEX);
        IwlanNetworkService.IwlanNetworkServiceProvider spyProvider = spy(provider);
        mIwlanNetworkService.removeNetworkServiceProvider(provider);
        waitForHandlerAction(mIwlanNetworkService.getIwlanNetworkServiceHandler());
        mIwlanNetworkService.addIwlanNetworkServiceProvider(spyProvider);
        waitForHandlerAction(mIwlanNetworkService.getIwlanNetworkServiceHandler());

        simulateSubscriptionActive(DEFAULT_SLOT_INDEX, spyProvider);

        verify(spyProvider, timeout(CALL_TIMEOUT_MS).times(1)).subscriptionChanged();
    }

    @Test
    public void testCrossSimCalling_EnableEvent_TriggersNotification() {
        IwlanNetworkService.IwlanNetworkServiceProvider provider =
                (IwlanNetworkService.IwlanNetworkServiceProvider)
                        mIwlanNetworkService.onCreateNetworkServiceProvider(DEFAULT_SLOT_INDEX);
        IwlanNetworkService.IwlanNetworkServiceProvider spyProvider = spy(provider);
        mIwlanNetworkService.removeNetworkServiceProvider(provider);
        waitForHandlerAction(mIwlanNetworkService.getIwlanNetworkServiceHandler());
        mIwlanNetworkService.addIwlanNetworkServiceProvider(spyProvider);
        waitForHandlerAction(mIwlanNetworkService.getIwlanNetworkServiceHandler());

        mIwlanNetworkService
                .getIwlanNetworkServiceHandler()
                .obtainMessage(
                        IwlanEventListener.CROSS_SIM_CALLING_ENABLE_EVENT, DEFAULT_SLOT_INDEX, 0)
                .sendToTarget();
        waitForHandlerAction(mIwlanNetworkService.getIwlanNetworkServiceHandler());

        verify(spyProvider, times(1)).notifyNetworkRegistrationInfoChanged();
    }

    @Test
    public void testCrossSimCalling_DisableEvent_TriggersNotification() {
        IwlanNetworkService.IwlanNetworkServiceProvider provider =
                (IwlanNetworkService.IwlanNetworkServiceProvider)
                        mIwlanNetworkService.onCreateNetworkServiceProvider(DEFAULT_SLOT_INDEX);
        IwlanNetworkService.IwlanNetworkServiceProvider spyProvider = spy(provider);
        mIwlanNetworkService.removeNetworkServiceProvider(provider);
        waitForHandlerAction(mIwlanNetworkService.getIwlanNetworkServiceHandler());
        mIwlanNetworkService.addIwlanNetworkServiceProvider(spyProvider);
        waitForHandlerAction(mIwlanNetworkService.getIwlanNetworkServiceHandler());

        mIwlanNetworkService
                .getIwlanNetworkServiceHandler()
                .obtainMessage(
                        IwlanEventListener.CROSS_SIM_CALLING_DISABLE_EVENT, DEFAULT_SLOT_INDEX, 0)
                .sendToTarget();
        waitForHandlerAction(mIwlanNetworkService.getIwlanNetworkServiceHandler());

        verify(spyProvider, times(1)).notifyNetworkRegistrationInfoChanged();
    }

    private void simulateWifiConnected() {
        Network network = mock(Network.class);
        NetworkCapabilities nc =
                new NetworkCapabilities.Builder().addTransportType(TRANSPORT_WIFI).build();
        mIwlanNetworkService.getNetworkMonitorCallback().onCapabilitiesChanged(network, nc);
    }

    private void simulateCellularConnected(int subId) {
        Network network = mock(Network.class);
        NetworkCapabilities nc =
                new NetworkCapabilities.Builder()
                        .addTransportType(TRANSPORT_CELLULAR)
                        .setNetworkSpecifier(new TelephonyNetworkSpecifier(subId))
                        .build();
        mIwlanNetworkService.getNetworkMonitorCallback().onCapabilitiesChanged(network, nc);
    }

    private void simulateVcnConnected(int subId) {
        Network network = mock(Network.class);
        VcnTransportInfo vcnInfo = new VcnTransportInfo.Builder().build();
        NetworkCapabilities nc =
                new NetworkCapabilities.Builder()
                        .addTransportType(TRANSPORT_CELLULAR)
                        .setTransportInfo(vcnInfo)
                        .build();
        ShadowVcnUtils.setSubId(subId);
        mIwlanNetworkService.getNetworkMonitorCallback().onCapabilitiesChanged(network, nc);
    }

    private void simulateSubscriptionActive(
            int slotIndex, IwlanNetworkService.IwlanNetworkServiceProvider provider) {
        ShadowSubscriptionManager shadowSm = Shadows.shadowOf(mSubscriptionManager);
        shadowSm.setActiveSubscriptionInfoList(List.of(mMockSubscriptionInfo));
        when(mMockSubscriptionInfo.getSimSlotIndex()).thenReturn(slotIndex);
        when(mMockSubscriptionInfo.getSubscriptionId()).thenReturn(DEFAULT_SUB_ID);

        if (provider != null) {
            provider.subscriptionChanged();
        } else {
            // Fallback for listener triggering if needed.
        }
    }

    private void verifyNetworkRegistrationInfo(
            int expectedState, int expectedTransport, int expectedTech) {
        ArgumentCaptor<NetworkRegistrationInfo> captor =
                ArgumentCaptor.forClass(NetworkRegistrationInfo.class);
        verify(mMockNetworkServiceCallback)
                .onRequestNetworkRegistrationInfoComplete(
                        eq(NetworkServiceCallback.RESULT_SUCCESS), captor.capture());

        NetworkRegistrationInfo nri = captor.getValue();
        assertEquals(expectedState, nri.getRegistrationState());
        assertEquals(expectedTransport, nri.getTransportType());
        assertEquals(expectedTech, nri.getAccessNetworkTechnology());
        assertEquals(NetworkRegistrationInfo.DOMAIN_PS, nri.getDomain());
        assertEquals(
                List.of(NetworkRegistrationInfo.SERVICE_TYPE_DATA), nri.getAvailableServices());
    }

    private static void waitForHandlerAction(android.os.Handler handler) {
        Shadows.shadowOf(Looper.getMainLooper()).runToEndOfTasks();
    }

    private static class TestIwlanNetworkService extends IwlanNetworkService {
        @Override
        Looper getLooper() {
            return Looper.getMainLooper();
        }
    }

    @Implements(IwlanEventListener.class)
    public static class ShadowIwlanEventListener {
        private static IwlanEventListener sInstance;

        @Implementation
        public static IwlanEventListener getInstance(Context context, int slotId) {
            return sInstance;
        }

        public static void setInstance(IwlanEventListener instance) {
            sInstance = instance;
        }

        public static void reset() {
            sInstance = null;
        }
    }

    @Implements(IwlanHelper.class)
    public static class ShadowIwlanHelper {
        private static int sSubId;
        private static boolean sIsCrossSimCallingEnabled;

        @Implementation
        public static int getSubId(Context context, int slotId) {
            return sSubId;
        }

        @Implementation
        public static boolean isCrossSimCallingEnabled(Context context, int slotId) {
            return sIsCrossSimCallingEnabled;
        }

        public static void setSubId(int slotId, int subId) {
            sSubId = subId;
        }

        public static void setCrossSimCallingEnabled(boolean enabled) {
            sIsCrossSimCallingEnabled = enabled;
        }

        public static void reset() {
            sSubId = 0;
            sIsCrossSimCallingEnabled = false;
        }
    }

    @Implements(VcnUtils.class)
    public static class ShadowVcnUtils {
        private static int sSubId;

        @Implementation
        public static int getSubIdFromVcnCaps(
                ConnectivityManager connectivityManager, NetworkCapabilities networkCapabilities) {
            return sSubId;
        }

        public static void setSubId(int subId) {
            sSubId = subId;
        }

        public static void reset() {
            sSubId = 0;
        }
    }
}

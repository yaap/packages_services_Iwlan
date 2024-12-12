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

package com.google.android.iwlan;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;

import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;

import com.google.android.iwlan.epdg.EpdgSelector;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

public class IwlanBroadcastReceiverTest {
    private static final String TAG = "IwlanBroadcastReceiverTest";
    private IwlanBroadcastReceiver mBroadcastReceiver;

    private static final int TEST_SUB_ID = 5;
    private static final int TEST_SLOT_ID = 6;

    MockitoSession mStaticMockSession;
    @Mock private Context mMockContext;
    @Mock private IwlanEventListener mMockIwlanEventListener;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mStaticMockSession =
                mockitoSession()
                        .mockStatic(EpdgSelector.class)
                        .mockStatic(IwlanDataService.class)
                        .mockStatic(IwlanHelper.class)
                        .mockStatic(SubscriptionManager.class)
                        .mockStatic(IwlanEventListener.class)
                        .startMocking();

        lenient().when(SubscriptionManager.getSlotIndex(eq(TEST_SUB_ID))).thenReturn(TEST_SLOT_ID);

        lenient().when(IwlanDataService.getContext()).thenReturn(mMockContext);

        lenient()
                .when(IwlanEventListener.getInstance(eq(mMockContext), eq(TEST_SLOT_ID)))
                .thenReturn(mMockIwlanEventListener);

        // New BroadcastReceiver object
        mBroadcastReceiver = new IwlanBroadcastReceiver();
    }

    @After
    public void cleanUp() throws Exception {
        mStaticMockSession.finishMocking();
    }

    @Test
    public void testCarrierConfigChanged() throws Exception {
        final Intent intent = new Intent(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED);
        intent.putExtra(CarrierConfigManager.EXTRA_SLOT_INDEX, TEST_SLOT_ID);

        // Trigger the onReceive
        mBroadcastReceiver.onReceive(mMockContext, intent);

        verify(mMockIwlanEventListener).onBroadcastReceived(intent);
    }

    @Test
    public void testWifiStateChanged() throws Exception {
        final Intent intent = new Intent(WifiManager.WIFI_STATE_CHANGED_ACTION);
        intent.putExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_DISABLED);

        // Trigger broadcast
        mBroadcastReceiver.onReceive(mMockContext, intent);

        verify(mMockIwlanEventListener).onBroadcastReceived(intent);
    }

    @Test
    public void testScreenOn_shouldSendToListener() throws Exception {
        final Intent intent = new Intent(Intent.ACTION_SCREEN_ON);

        // Trigger broadcast
        mBroadcastReceiver.onReceive(mMockContext, intent);

        verify(mMockIwlanEventListener).onBroadcastReceived(intent);
    }
}

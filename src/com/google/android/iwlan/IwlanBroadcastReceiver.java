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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.util.Log;

public class IwlanBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = "IwlanBroadcastReceiver";

    private static boolean mIsReceiverRegistered = false;
    private static IwlanBroadcastReceiver mInstance;

    public static void startListening(Context context) {
        if (mIsReceiverRegistered) {
            Log.d(TAG, "startListening: Receiver already registered");
            return;
        }
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        intentFilter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        intentFilter.addAction(Intent.ACTION_SCREEN_ON);
        context.registerReceiver(getInstance(), intentFilter);
        mIsReceiverRegistered = true;
    }

    public static void stopListening(Context context) {
        if (!mIsReceiverRegistered) {
            Log.d(TAG, "stopListening: Receiver not registered!");
            return;
        }
        context.unregisterReceiver(getInstance());
        mIsReceiverRegistered = false;
    }

    private static IwlanBroadcastReceiver getInstance() {
        if (mInstance == null) {
            mInstance = new IwlanBroadcastReceiver();
        }
        return mInstance;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "onReceive: " + action);
        switch (action) {
            case Intent.ACTION_AIRPLANE_MODE_CHANGED:
            case WifiManager.WIFI_STATE_CHANGED_ACTION:
            case Intent.ACTION_SCREEN_ON:
                IwlanEventListener.onBroadcastReceived(intent);
                break;
        }
    }
}

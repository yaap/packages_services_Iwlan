/*
 * Copyright (C) 2022 The Android Open Source Project
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

import android.os.Handler;

public class IwlanTunnelMetricsImpl implements TunnelMetricsInterface {
    Handler mIwlanDataServiceHandler;

    private static final int EVENT_BASE = IwlanEventListener.DATA_SERVICE_INTERNAL_EVENT_BASE;
    private static final int EVENT_TUNNEL_OPENED_METRICS = EVENT_BASE + 8;
    private static final int EVENT_TUNNEL_CLOSED_METRICS = EVENT_BASE + 9;

    public IwlanTunnelMetricsImpl(Handler handler) {
        mIwlanDataServiceHandler = handler;
    }

    public void onOpened(OnOpenedMetrics metricsData) {
        mIwlanDataServiceHandler.sendMessage(
                mIwlanDataServiceHandler.obtainMessage(EVENT_TUNNEL_OPENED_METRICS, metricsData));
    }

    public void onClosed(OnClosedMetrics metricsData) {
        mIwlanDataServiceHandler.sendMessage(
                mIwlanDataServiceHandler.obtainMessage(EVENT_TUNNEL_CLOSED_METRICS, metricsData));
    }
}

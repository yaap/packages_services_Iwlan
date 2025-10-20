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

import static android.telephony.PreciseDataConnectionState.NetworkValidationStatus;

import android.support.annotation.NonNull;

import com.google.android.iwlan.IwlanError;
import com.google.android.iwlan.TunnelMetricsInterface.OnClosedMetrics;
import com.google.android.iwlan.TunnelMetricsInterface.OnOpenedMetrics;

public interface EpdgTunnelCallback {
    /**
     * Called when the tunnel is opened.
     *
     * @param apnName apn for which the tunnel was opened
     * @param linkProperties link properties of the tunnel
     * @param onOpenedMetrics metrics for the tunnel
     */
    void onOpened(
            @NonNull String apnName,
            @NonNull TunnelLinkProperties linkProperties,
            OnOpenedMetrics onOpenedMetrics);

    /**
     * Called when the tunnel is closed OR if bring up fails
     *
     * @param apnName apn for which the tunnel was closed
     * @param error IwlanError carrying details of the error
     * @param onClosedMetrics metrics for the tunnel
     */
    void onClosed(
            @NonNull String apnName, @NonNull IwlanError error, OnClosedMetrics onClosedMetrics);

    /**
     * Called when updates upon network validation status change.
     *
     * @param apnName APN affected.
     * @param status The updated validation status of the network.
     */
    void onNetworkValidationStatusChanged(
            @NonNull String apnName, @NetworkValidationStatus int status);

    void onTunnelLinkPropertiesChanged(
            @NonNull String apnName, TunnelLinkProperties linkProperties);
}

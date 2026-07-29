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

import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.vcn.VcnUtils;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

/** Shadow for {@link VcnUtils}. */
@Implements(VcnUtils.class)
public class ShadowVcnUtils {
    private static int sSubId;

    @Implementation
    public static int getSubIdFromVcnCaps(
            ConnectivityManager connectivityManager, NetworkCapabilities networkCapabilities) {
        return sSubId;
    }

    /**
     * Sets the subscription ID to be returned by {@link #getSubIdFromVcnCaps}.
     *
     * @param subId the subscription ID to return
     */
    public static void setSubId(int subId) {
        sSubId = subId;
    }

    /** Resets the static state of the shadow. */
    public static void reset() {
        sSubId = 0;
    }
}

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

import android.content.Context;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

/** Shadow for {@link IwlanHelper}. */
@Implements(IwlanHelper.class)
public class ShadowIwlanHelper {
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

    /**
     * Sets the subscription ID to be returned by {@link #getSubId}.
     *
     * @param slotId the slot ID (currently unused by this shadow)
     * @param subId the subscription ID to return
     */
    public static void setSubId(int slotId, int subId) {
        sSubId = subId;
    }

    /**
     * Sets the cross-SIM calling enabled state to be returned by {@link #isCrossSimCallingEnabled}.
     *
     * @param enabled true if cross-SIM calling is enabled, false otherwise
     */
    public static void setCrossSimCallingEnabled(boolean enabled) {
        sIsCrossSimCallingEnabled = enabled;
    }

    /** Resets the static state of the shadow. */
    public static void reset() {
        sSubId = 0;
        sIsCrossSimCallingEnabled = false;
    }
}

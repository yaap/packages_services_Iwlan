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

/** Shadow for {@link IwlanEventListener}. */
@Implements(IwlanEventListener.class)
public class ShadowIwlanEventListener {
    private static IwlanEventListener sInstance;

    @Implementation
    public static IwlanEventListener getInstance(Context context, int slotId) {
        return sInstance;
    }

    /**
     * Sets the {@link IwlanEventListener} instance to be returned by {@link #getInstance}.
     *
     * @param instance the instance to return
     */
    public static void setInstance(IwlanEventListener instance) {
        sInstance = instance;
    }

    /** Resets the static state of the shadow. */
    public static void reset() {
        sInstance = null;
    }
}

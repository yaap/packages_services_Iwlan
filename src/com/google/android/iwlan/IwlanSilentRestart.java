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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.google.android.iwlan.epdg.EpdgTunnelManager;
import com.google.android.iwlan.flags.FeatureFlags;
import com.google.android.iwlan.flags.FeatureFlagsImpl;

public class IwlanSilentRestart extends ContentProvider {
    private static final String TAG = IwlanSilentRestart.class.getSimpleName();

    private static final String METHOD_RESTART_IWLAN = "restart_iwlan";

    private static final String PERMISSION_RESTART_IWLAN = "android.iwlan.permission.RESTART_IWLAN";

    private final FeatureFlags mFeatureFlags = new FeatureFlagsImpl();
    private Handler mHandler;

    @Override
    public boolean onCreate() {
        Log.d(TAG, "onCreate");
        if (!mFeatureFlags.iwlanSilentRestart()) {
            Log.d(TAG, "feature not available");
            return false;
        }

        HandlerThread handlerThread = new HandlerThread("IwlanSilentRestartThread");
        handlerThread.start();
        mHandler = new Handler(handlerThread.getLooper());

        return true;
    }

    private void clearAndExit() {
        deinitService();
        Log.i(TAG, "Restart com.google.android.iwlan by killing it");
        System.exit(0);
    }

    private void deinitService() {
        EpdgTunnelManager.deinit();
    }

    @Override
    public @Nullable Bundle call(
            @NonNull String authority,
            @NonNull String method,
            @Nullable String arg,
            @Nullable Bundle extras) {
        Log.d(TAG, "called " + authority + " " + method);

        if (METHOD_RESTART_IWLAN.equals(method)) {
            final Context context = getContext();
            if (context == null) {
                Log.d(TAG, "Cannot find context from the content provider");
                return null;
            }

            context.enforceCallingOrSelfPermission(PERMISSION_RESTART_IWLAN, null);
            Log.i(TAG, "Restart com.google.android.iwlan");

            mHandler.post(this::clearAndExit);
        }

        return null;
    }

    @Override
    public @Nullable Cursor query(
            @NonNull Uri uri,
            @Nullable String[] projection,
            @Nullable String selection,
            @Nullable String[] selectionArgs,
            @Nullable String sortOrder) {
        throw new UnsupportedOperationException();
    }

    @Override
    public @Nullable String getType(@NonNull Uri uri) {
        throw new UnsupportedOperationException();
    }

    @Override
    public @Nullable Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(
            @NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(
            @NonNull Uri uri,
            @Nullable ContentValues values,
            @Nullable String selection,
            @Nullable String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }
}

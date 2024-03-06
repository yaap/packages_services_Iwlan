/*
 * Copyright 2023 The Android Open Source Project
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
import android.os.PersistableBundle;
import android.support.annotation.NonNull;
import android.telephony.CarrierConfigManager;

/** Class for handling IWLAN carrier configuration. */
public class IwlanCarrierConfig {

    /**
     * Key for setting the delay in seconds to release the IWLAN connection after a handover to
     * WWAN. Refer to {@link #DEFAULT_HANDOVER_TO_WWAN_RELEASE_DELAY_SECOND_INT} for the default
     * value.
     */
    public static final String KEY_HANDOVER_TO_WWAN_RELEASE_DELAY_SECOND_INT =
            "iwlan.handover_to_wwan_release_delay_second_int";

    /**
     * Default delay in seconds for releasing the IWLAN connection after a WWAN handover. This is
     * the default value for {@link #KEY_HANDOVER_TO_WWAN_RELEASE_DELAY_SECOND_INT}.
     */
    public static final int DEFAULT_HANDOVER_TO_WWAN_RELEASE_DELAY_SECOND_INT = 0;

    private static PersistableBundle mHiddenBundle = new PersistableBundle();

    static {
        mHiddenBundle = createHiddenDefaultConfig();
    }

    /**
     * Creates a hidden default configuration.
     *
     * @return a PersistableBundle containing the hidden default configuration
     */
    private static @NonNull PersistableBundle createHiddenDefaultConfig() {
        PersistableBundle bundle = new PersistableBundle();
        bundle.putInt(
                KEY_HANDOVER_TO_WWAN_RELEASE_DELAY_SECOND_INT,
                DEFAULT_HANDOVER_TO_WWAN_RELEASE_DELAY_SECOND_INT);
        return bundle;
    }

    private static PersistableBundle getConfig(Context context, int slotId, String key) {
        CarrierConfigManager carrierConfigManager =
                context.getSystemService(CarrierConfigManager.class);
        if (carrierConfigManager == null) {
            return getDefaultConfig(key);
        }

        int subId = IwlanHelper.getSubId(context, slotId);
        PersistableBundle bundle = carrierConfigManager.getConfigForSubId(subId, key);
        return bundle.containsKey(key) ? bundle : getDefaultConfig(key);
    }

    private static PersistableBundle getDefaultConfig(String key) {
        PersistableBundle bundle = CarrierConfigManager.getDefaultConfig();
        if (bundle.containsKey(key)) {
            return bundle;
        }

        if (mHiddenBundle.containsKey(key)) {
            return mHiddenBundle;
        }

        throw new IllegalArgumentException("Default config not found for key: " + key);
    }

    /**
     * Gets a configuration int value for a given slot ID and key.
     *
     * @param context the application context
     * @param slotId the slot ID
     * @param key the configuration key
     * @return the configuration int value
     */
    public static int getConfigInt(Context context, int slotId, String key) {
        return getConfig(context, slotId, key).getInt(key);
    }

    /**
     * Gets a configuration long value for a given slot ID and key.
     *
     * @param context the application context
     * @param slotId the slot ID
     * @param key the configuration key
     * @return the configuration long value
     */
    public static long getConfigLong(Context context, int slotId, String key) {
        return getConfig(context, slotId, key).getLong(key);
    }

    /**
     * Gets a configuration double value for a given slot ID and key.
     *
     * @param context the application context
     * @param slotId the slot ID
     * @param key the configuration key
     * @return the configuration double value
     */
    public static double getConfigDouble(Context context, int slotId, String key) {
        return getConfig(context, slotId, key).getDouble(key);
    }

    /**
     * Gets a configuration boolean value for a given slot ID and key.
     *
     * @param context the application context
     * @param slotId the slot ID
     * @param key the configuration key
     * @return the configuration boolean value
     */
    public static boolean getConfigBoolean(Context context, int slotId, String key) {
        return getConfig(context, slotId, key).getBoolean(key);
    }

    /**
     * Gets a configuration string value for a given slot ID and key.
     *
     * @param context the application context
     * @param slotId the slot ID
     * @param key the configuration key
     * @return the configuration string value
     */
    public static String getConfigString(Context context, int slotId, String key) {
        return getConfig(context, slotId, key).getString(key);
    }

    /**
     * Gets a configuration int[] value for a given slot ID and key.
     *
     * @param context the application context
     * @param slotId the slot ID
     * @param key the configuration key
     * @return the configuration int[] value
     */
    public static int[] getConfigIntArray(Context context, int slotId, String key) {
        return getConfig(context, slotId, key).getIntArray(key);
    }

    /**
     * Gets a configuration long[] value for a given slot ID and key.
     *
     * @param context the application context
     * @param slotId the slot ID
     * @param key the configuration key
     * @return the configuration long[] value
     */
    public static long[] getConfigLongArray(Context context, int slotId, String key) {
        return getConfig(context, slotId, key).getLongArray(key);
    }

    /**
     * Gets a configuration double[] value for a given slot ID and key.
     *
     * @param context the application context
     * @param slotId the slot ID
     * @param key the configuration key
     * @return the configuration double[] value
     */
    public static double[] getConfigDoubleArray(Context context, int slotId, String key) {
        return getConfig(context, slotId, key).getDoubleArray(key);
    }

    /**
     * Gets a configuration boolean[] value for a given slot ID and key.
     *
     * @param context the application context
     * @param slotId the slot ID
     * @param key the configuration key
     * @return the configuration boolean[] value
     */
    public static boolean[] getConfigBooleanArray(Context context, int slotId, String key) {
        return getConfig(context, slotId, key).getBooleanArray(key);
    }

    /**
     * Gets a configuration string[] value for a given slot ID and key.
     *
     * @param context the application context
     * @param slotId the slot ID
     * @param key the configuration key
     * @return the configuration string[] value
     */
    public static String[] getConfigStringArray(Context context, int slotId, String key) {
        return getConfig(context, slotId, key).getStringArray(key);
    }

    /**
     * Gets the default configuration int value for a given key.
     *
     * @param key the configuration key
     * @return the default configuration int value
     * @throws IllegalArgumentException if the default configuration is null for the given key
     */
    public static int getDefaultConfigInt(String key) {
        return getDefaultConfig(key).getInt(key);
    }

    /**
     * Gets the default configuration long value for a given key.
     *
     * @param key the configuration key
     * @return the default configuration long value
     * @throws IllegalArgumentException if the default configuration is null for the given key
     */
    public static long getDefaultConfigLong(String key) {
        return getDefaultConfig(key).getLong(key);
    }

    /**
     * Gets the default configuration double value for a given key.
     *
     * @param key the configuration key
     * @return the default configuration double value
     * @throws IllegalArgumentException if the default configuration is null for the given key
     */
    public static double getDefaultConfigDouble(String key) {
        return getDefaultConfig(key).getDouble(key);
    }

    /**
     * Gets the default configuration string value for a given key.
     *
     * @param key the configuration key
     * @return the default configuration string value
     * @throws IllegalArgumentException if the default configuration is null for the given key
     */
    public static String getDefaultConfigString(String key) {
        return getDefaultConfig(key).getString(key);
    }

    /**
     * Gets the default configuration boolean value for a given key.
     *
     * @param key the configuration key
     * @return the default configuration boolean value
     * @throws IllegalArgumentException if the default configuration is null for the given key
     */
    public static boolean getDefaultConfigBoolean(String key) {
        return getDefaultConfig(key).getBoolean(key);
    }
    /**
     * Gets the default configuration int[] value for a given key.
     *
     * @param key the configuration key
     * @return the default configuration int[] value
     * @throws IllegalArgumentException if the default configuration is null for the given key
     */
    public static int[] getDefaultConfigIntArray(String key) {
        return getDefaultConfig(key).getIntArray(key);
    }

    /**
     * Gets the default configuration long value for a given key.
     *
     * @param key the configuration key
     * @return the default configuration long value
     * @throws IllegalArgumentException if the default configuration is null for the given key
     */
    public static long[] getDefaultConfigLongArray(String key) {
        return getDefaultConfig(key).getLongArray(key);
    }
    /**
     * Gets the default configuration double[] value for a given key.
     *
     * @param key the configuration key
     * @return the default configuration double[] value
     * @throws IllegalArgumentException if the default configuration is null for the given key
     */
    public static double[] getDefaultConfigDoubleArray(String key) {
        return getDefaultConfig(key).getDoubleArray(key);
    }

    /**
     * Gets the default configuration string[] value for a given key.
     *
     * @param key the configuration key
     * @return the default configuration string[] value
     * @throws IllegalArgumentException if the default configuration is null for the given key
     */
    public static String[] getDefaultConfigStringArray(String key) {
        return getDefaultConfig(key).getStringArray(key);
    }

    /**
     * Gets the default configuration boolean[] value for a given key.
     *
     * @param key the configuration key
     * @return the default configuration boolean[] value
     * @throws IllegalArgumentException if the default configuration is null for the given key
     */
    public static boolean[] getDefaultConfigBooleanArray(String key) {
        return getDefaultConfig(key).getBooleanArray(key);
    }
}

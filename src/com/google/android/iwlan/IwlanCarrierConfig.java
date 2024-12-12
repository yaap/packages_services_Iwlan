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
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.telephony.CarrierConfigManager;

import androidx.annotation.VisibleForTesting;

/** Class for handling IWLAN carrier configuration. */
public class IwlanCarrierConfig {
    static final String PREFIX = "iwlan.";

    /**
     * Key for setting the delay in seconds to release the IWLAN connection after a handover to
     * WWAN. Refer to {@link #DEFAULT_HANDOVER_TO_WWAN_RELEASE_DELAY_SECOND_INT} for the default
     * value.
     */
    public static final String KEY_HANDOVER_TO_WWAN_RELEASE_DELAY_SECOND_INT =
            PREFIX + "handover_to_wwan_release_delay_second_int";

    /**
     * Key to exclude IKE N1_MODE_CAPABILITY Notify payload during emergency session setup without
     * affecting normal sessions. See {@link #DEFAULT_N1_MODE_EXCLUSION_FOR_EMERGENCY_SESSION_BOOL}
     * for the default value.
     */
    public static final String KEY_N1_MODE_EXCLUSION_FOR_EMERGENCY_SESSION_BOOL =
            PREFIX + "n1_mode_exclusion_for_emergency_session_bool";

    /**
     * Key to decide whether N1 mode shall be enabled or disabled depending on 5G enabling status
     * via the UI/UX. See {@link #DEFAULT_UPDATE_N1_MODE_ON_UI_CHANGE_BOOL} for the default value.
     */
    public static final String KEY_UPDATE_N1_MODE_ON_UI_CHANGE_BOOL =
            PREFIX + "update_n1_mode_on_ui_change_bool";

    /**
     * Boolean indicating if distinct ePDG selection for emergency sessions is enabled. Refer to
     * {@link #DEFAULT_DISTINCT_EPDG_FOR_EMERGENCY_ALLOWED_BOOL} for the default value.
     */
    public static final String KEY_DISTINCT_EPDG_FOR_EMERGENCY_ALLOWED_BOOL =
            PREFIX + "distinct_epdg_for_emergency_allowed_bool";

    /**
     * Key to control whether the UE includes the IKE DEVICE_IDENTITY Notify payload when receiving
     * a request. See {@link #DEFAULT_IKE_DEVICE_IDENTITY_SUPPORTED_BOOL} for the default value.
     */
    public static final String KEY_IKE_DEVICE_IDENTITY_SUPPORTED_BOOL =
            PREFIX + "ike_device_identity_supported_bool";

    /**
     * Boolean indicating if reordering ike SA transforms enabled. Refer to {@link
     * #DEFAULT_IKE_SA_TRANSFORMS_REORDER_BOOL} for the default value.
     */
    public static final String KEY_IKE_SA_TRANSFORMS_REORDER_BOOL =
            PREFIX + "ike_sa_transforms_reorder_bool";

    /** Trigger network validation when making a call */
    public static final int NETWORK_VALIDATION_EVENT_MAKING_CALL = 0;

    /** Trigger network validation when screen on */
    public static final int NETWORK_VALIDATION_EVENT_SCREEN_ON = 1;

    /** Trigger network validation when no response on network */
    public static final int NETWORK_VALIDATION_EVENT_NO_RESPONSE = 2;

    @IntDef({
        NETWORK_VALIDATION_EVENT_MAKING_CALL,
        NETWORK_VALIDATION_EVENT_SCREEN_ON,
        NETWORK_VALIDATION_EVENT_NO_RESPONSE
    })
    public @interface NetworkValidationEvent {}

    /**
     * Key to control which events should trigger IWLAN underlying network validation when specific
     * event received, possible values in the int array:
     *
     * <ul>
     *   <li>0: NETWORK_VALIDATION_EVENT_MAKING_CALL
     *   <li>1: NETWORK_VALIDATION_EVENT_SCREEN_ON
     *   <li>2: NETWORK_VALIDATION_EVENT_NO_RESPONSE
     * </ul>
     */
    public static final String KEY_UNDERLYING_NETWORK_VALIDATION_EVENTS_INT_ARRAY =
            PREFIX + "underlying_network_validation_events_int_array";

    /**
     * IWLAN error policy configs that determine the behavior when error happens during ePDG tunnel
     * setup. Refer to {@link #DEFAULT_ERROR_POLICY_CONFIG_STRING} for the default value.
     *
     * <p>The Error Config is defined as an Array of APNs identified by "ApnName". Other than Apn
     * names this can also have "*" value which represents that this can be used as a generic
     * fallback when no other policy matches.
     *
     * <p>Each APN associated with "ApnName" has an array of "ErrorTypes". Where each element in
     * "ErrorTypes" array defines the config for the Error. The element in "ErrorTypes" array has
     * the following items:
     *
     * <ul>
     *   <li>"ErrorType": The type of error in String. Possible error types are:
     *       <ol>
     *         <li>"IKE_PROTOCOL_ERROR_TYPE" refers to the Notify Error coming in Notify payload.
     *             See https://tools.ietf.org/html/rfc4306#section-3.10.1 for global errors and
     *             carrier specific requirements for other carrier specific error codes.
     *         <li>"GENERIC_ERROR_TYPE" refers to the following IWLAN errors - "IO_EXCEPTION",
     *             "TIMEOUT_EXCEPTION", "SERVER_SELECTION_FAILED" and "TUNNEL_TRANSFORM_FAILED".
     *         <li>"*" represents that this policy is a generic fallback when no other policy
     *             matches.
     *       </ol>
     *   <li>"ErrorDetails": Array of errors specifics for which the policy needs to be applied to.
     *       Note: Array can be a mix of numbers, ranges and string formats. Following are the
     *       currently supported formats of elements in the array:
     *       <ol>
     *         <li>Number or Code: "24" - Number specific to the error.
     *         <li>Range: "9000-9050" - Range of specific errors.
     *         <li>Any: "*" value represents that this can be applied to all ErrorDetails when there
     *             is no specific match. This will be a single element array.
     *         <li>String: String describing the specific error. Current allowed string values -
     *             "IO_EXCEPTION", "TIMEOUT_EXCEPTION", "SERVER_SELECTION_FAILED" and
     *             "TUNNEL_TRANSFORM_FAILED"
     *       </ol>
     *       <p>"IKE_PROTOCOL_EXCEPTION" ErrorType expects the "error_detail" to be defined only in
     *       numbers or range of numbers. Examples: ["24"] or ["9000-9050"] or ["7", "14000-14050"]
     *       <p>"GENERIC_ERROR_TYPE" or "*" ErrorType expects only the following to be in
     *       "ErrorDetails" - "IO_EXCEPTION", "TIMEOUT_EXCEPTION", "SERVER_SELECTION_FAILED",
     *       "TUNNEL_TRANSFORM_FAILED" and "*". Examples: ["IO_EXCEPTION", "TIMEOUT_EXCEPTION"] or
     *       ["*"]
     *   <li>"RetryArray": Array of retry times (in secs) represented in string format. Following
     *       formats are currently supported:
     *       <ol>
     *         <li>["0","0", "0"] Retry immediately for maximum 3 times and then fail.
     *         <li>[] Empty array means to fail whenever the error happens.
     *         <li>["2", "4", "8"] Retry times are 2 secs, 4secs and 8 secs - fail after that.
     *         <li>["5", "10", "15", "-1"] Here the "-1" represents infinite retires with the retry
     *             time "15" the last retry number).
     *         <li>["2+r15"] 2 seconds + random time below 15 seconds, fail after that.
     *       </ol>
     *       <p>When fails, by default throttle for 24 hours.
     *   <li>"UnthrottlingEvents": Events for which the retry time can be unthrottled in string.
     *       Possible unthrottling events are:
     *       <ol>
     *         <li>"WIFI_DISABLE_EVENT": Wifi on to off toggle.
     *         <li>"APM_DISABLE_EVENT": APM on to off toggle.
     *         <li>"APM_ENABLE_EVENT": APM off to on toggle.
     *         <li>"WIFI_AP_CHANGED_EVENT": Wifi is connected to an AP with different SSID.
     *         <li>"WIFI_CALLING_DISABLE_EVENT": Wifi calling button on to off toggle.
     *       </ol>
     *   <li>"NumAttemptsPerFqdn" Integer to specify th count of tunnel setup attempts IWLAN must
     *       perform with the IP address(es) returned by a single FQDN, before moving on to the next
     *       FQDN. It is an optional field.
     *   <li>"HandoverAttemptCount": Integer to specify the number of handover request attempts
     *       before using initial attach instead. It is an optional field.
     *       <p>"HandoverAttemptCount" should not be defined in the config when "ErrorType" is
     *       defined as any other error types except "IKE_PROTOCOL_ERROR_TYPE", including "*".
     * </ul>
     *
     * <p>Note: When the value is "*" for any of "ApnName" or "ErrorType" or "ErrorDetails", it
     * means that the config definition applies to rest of the errors for which the config is not
     * defined. For example, if "ApnName" is "ims" and one of the "ErrorType" in it is defined as
     * "*" - this policy will be applied to the error that doesn't fall into other error types
     * defined under "ims".
     */
    public static final String KEY_ERROR_POLICY_CONFIG_STRING =
            PREFIX + "key_error_policy_config_string";

    /**
     * Default delay in seconds for releasing the IWLAN connection after a WWAN handover. This is
     * the default value for {@link #KEY_HANDOVER_TO_WWAN_RELEASE_DELAY_SECOND_INT}.
     */
    public static final int DEFAULT_HANDOVER_TO_WWAN_RELEASE_DELAY_SECOND_INT = 0;

    /**
     * The default value for determining whether the IKE N1_MODE_CAPABILITY Notify payload is
     * excluded during emergency session setup.
     */
    public static final boolean DEFAULT_N1_MODE_EXCLUSION_FOR_EMERGENCY_SESSION_BOOL = false;

    /**
     * The default value for determining whether N1 mode shall be enabled or disabled depending on
     * 5G enabling status via the UI/UX.
     */
    public static final boolean DEFAULT_UPDATE_N1_MODE_ON_UI_CHANGE_BOOL = false;

    /** This is the default value for {@link #KEY_DISTINCT_EPDG_FOR_EMERGENCY_ALLOWED_BOOL}. */
    public static final boolean DEFAULT_DISTINCT_EPDG_FOR_EMERGENCY_ALLOWED_BOOL = false;

    /**
     * Default value indicating whether the UE includes the IKE DEVICE_IDENTITY Notify payload upon
     * receiving a request. This is the default setting for {@link
     * #KEY_IKE_DEVICE_IDENTITY_SUPPORTED_BOOL}.
     */
    public static final boolean DEFAULT_IKE_DEVICE_IDENTITY_SUPPORTED_BOOL = false;

    /** This is the default value for {@link #KEY_IKE_SA_TRANSFORMS_REORDER_BOOL}. */
    public static final boolean DEFAULT_IKE_SA_TRANSFORMS_REORDER_BOOL = false;

    /**
     * The default value of which events should trigger IWLAN underlying network validation. This is
     * the default value for {@link #KEY_UNDERLYING_NETWORK_VALIDATION_EVENTS_INT_ARRAY}
     */
    public static final int[] DEFAULT_UNDERLYING_NETWORK_VALIDATION_EVENTS_INT_ARRAY = {};

    /**
     * The default value for determining IWLAN's behavior when error happens during ePDG tunnel
     * setup. This is the default value for {@link #KEY_ERROR_POLICY_CONFIG_STRING}.
     */
    public static final String DEFAULT_ERROR_POLICY_CONFIG_STRING =
            """
            [{
            "ApnName": "*",
            "ErrorTypes": [{
                "ErrorType": "*",
                "ErrorDetails": ["*"],
                "RetryArray": ["1","2","2","10","20","40","80","160",
                                "320","640","1280","1800","3600","-1"],
                "UnthrottlingEvents": ["APM_ENABLE_EVENT","APM_DISABLE_EVENT",
                                        "WIFI_DISABLE_EVENT","WIFI_AP_CHANGED_EVENT"]},{
                "ErrorType": "GENERIC_ERROR_TYPE",
                "ErrorDetails": ["IO_EXCEPTION"],
                "RetryArray": ["0","0","0","30","60+r15","120","-1"],
                "UnthrottlingEvents": ["APM_ENABLE_EVENT","APM_DISABLE_EVENT",
                                        "WIFI_DISABLE_EVENT","WIFI_AP_CHANGED_EVENT"]},{
                "ErrorType": "IKE_PROTOCOL_ERROR_TYPE",
                "ErrorDetails": ["*"],
                "RetryArray": ["5","10","10","20","40","80","160",
                                "320","640","1280","1800","3600","-1"],
                "UnthrottlingEvents": ["APM_ENABLE_EVENT","WIFI_DISABLE_EVENT",
                                        "WIFI_CALLING_DISABLE_EVENT"]},{
                "ErrorType": "IKE_PROTOCOL_ERROR_TYPE",
                "ErrorDetails": ["36"],
                "RetryArray": ["0","0","0","10","20","40","80","160",
                                "320","640","1280","1800","3600","-1"],
                "UnthrottlingEvents": ["APM_ENABLE_EVENT","WIFI_DISABLE_EVENT",
                                        "WIFI_CALLING_DISABLE_EVENT"],
                "HandoverAttemptCount": "3"}]
            }]
            """;

    private static final PersistableBundle sTestBundle = new PersistableBundle();

    private static PersistableBundle sHiddenBundle = new PersistableBundle();

    static {
        sHiddenBundle = createHiddenDefaultConfig();
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
        bundle.putBoolean(
                KEY_N1_MODE_EXCLUSION_FOR_EMERGENCY_SESSION_BOOL,
                DEFAULT_N1_MODE_EXCLUSION_FOR_EMERGENCY_SESSION_BOOL);
        bundle.putBoolean(
                KEY_UPDATE_N1_MODE_ON_UI_CHANGE_BOOL, DEFAULT_UPDATE_N1_MODE_ON_UI_CHANGE_BOOL);
        bundle.putBoolean(
                KEY_DISTINCT_EPDG_FOR_EMERGENCY_ALLOWED_BOOL,
                DEFAULT_DISTINCT_EPDG_FOR_EMERGENCY_ALLOWED_BOOL);
        bundle.putBoolean(
                KEY_IKE_DEVICE_IDENTITY_SUPPORTED_BOOL, DEFAULT_IKE_DEVICE_IDENTITY_SUPPORTED_BOOL);
        bundle.putBoolean(
                KEY_IKE_SA_TRANSFORMS_REORDER_BOOL, DEFAULT_IKE_SA_TRANSFORMS_REORDER_BOOL);
        bundle.putIntArray(
                KEY_UNDERLYING_NETWORK_VALIDATION_EVENTS_INT_ARRAY,
                DEFAULT_UNDERLYING_NETWORK_VALIDATION_EVENTS_INT_ARRAY);
        bundle.putString(KEY_ERROR_POLICY_CONFIG_STRING, DEFAULT_ERROR_POLICY_CONFIG_STRING);
        return bundle;
    }

    private static PersistableBundle getConfig(Context context, int slotId, String key) {
        if (sTestBundle.containsKey(key)) {
            return sTestBundle;
        }

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

        if (sHiddenBundle.containsKey(key)) {
            return sHiddenBundle;
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

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    public static void putTestConfigBundle(PersistableBundle bundle) {
        sTestBundle.putAll(bundle);
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    public static void putTestConfigInt(@NonNull String key, int value) {
        sTestBundle.putInt(key, value);
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    public static void putTestConfigLong(@NonNull String key, long value) {
        sTestBundle.putLong(key, value);
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    public static void putTestConfigDouble(@NonNull String key, double value) {
        sTestBundle.putDouble(key, value);
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    public static void putTestConfigBoolean(@NonNull String key, boolean value) {
        sTestBundle.putBoolean(key, value);
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    public static void putTestConfigString(@NonNull String key, String value) {
        sTestBundle.putString(key, value);
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    public static void putTestConfigIntArray(@NonNull String key, @NonNull int[] value) {
        sTestBundle.putIntArray(key, value);
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    public static void putTestConfigLongArray(@NonNull String key, @NonNull long[] value) {
        sTestBundle.putLongArray(key, value);
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    public static void putTestConfigDoubleArray(@NonNull String key, @NonNull double[] value) {
        sTestBundle.putDoubleArray(key, value);
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    public static void putTestConfigBooleanArray(@NonNull String key, @NonNull boolean[] value) {
        sTestBundle.putBooleanArray(key, value);
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    public static void putTestConfigStringArray(@NonNull String key, @NonNull String[] value) {
        sTestBundle.putStringArray(key, value);
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    public static void resetTestConfig() {
        sTestBundle.clear();
    }

    public static String getUnderlyingNetworkValidationEventString(
            @IwlanCarrierConfig.NetworkValidationEvent int event) {
        return switch (event) {
            case IwlanCarrierConfig.NETWORK_VALIDATION_EVENT_MAKING_CALL -> "MAKING_CALL";
            case IwlanCarrierConfig.NETWORK_VALIDATION_EVENT_SCREEN_ON -> "SCREEN_ON";
            case IwlanCarrierConfig.NETWORK_VALIDATION_EVENT_NO_RESPONSE -> "NO_RESPONSE";
            default -> "UNKNOWN";
        };
    }
}

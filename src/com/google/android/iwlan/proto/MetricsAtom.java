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

package com.google.android.iwlan.proto;

import android.net.ipsec.ike.exceptions.IkeIOException;
import android.net.ipsec.ike.exceptions.IkeInternalException;
import android.util.Log;

import com.google.android.iwlan.IwlanError;
import com.google.android.iwlan.IwlanStatsLog;

public class MetricsAtom {
    public static int INVALID_MESSAGE_ID = -1;
    private static final String TAG = "IwlanMetrics";

    public static final int NETWORK_VALIDATION_TRANSPORT_TYPE_UNSPECIFIED = 0;
    public static final int NETWORK_VALIDATION_TRANSPORT_TYPE_CELLULAR = 1;
    public static final int NETWORK_VALIDATION_TRANSPORT_TYPE_WIFI = 2;

    public static final int NETWORK_VALIDATION_EVENT_UNSPECIFIED = 0;
    public static final int NETWORK_VALIDATION_EVENT_MAKING_CALL = 1;
    public static final int NETWORK_VALIDATION_EVENT_SCREEN_ON = 2;
    public static final int NETWORK_VALIDATION_EVENT_NO_RESPONSE = 3;

    public static final int NETWORK_VALIDATION_RESULT_UNSPECIFIED = 0;
    public static final int NETWORK_VALIDATION_RESULT_INVALID = 1;
    public static final int NETWORK_VALIDATION_RESULT_VALID = 2;
    public static final int NETWORK_VALIDATION_RESULT_PARTIALLY_VALID = 3;
    public static final int NETWORK_VALIDATION_RESULT_SKIPPED = 4;

    private int mMessageId;
    private int mApnType;
    private boolean mIsHandover;
    private String mEpdgServerAddress;
    private int mSourceRat;
    private boolean mIsCellularRoaming;
    private boolean mIsNetworkConnected;
    private int mTransportType;
    private int mSetupRequestResult;
    private int mIwlanError;
    private int mDataCallFailCause;
    private int mProcessingDurationMillis;
    private int mEpdgServerSelectionDurationMillis;
    private int mIkeTunnelEstablishmentDurationMillis;
    private int mTunnelState;
    private int mHandoverFailureMode;
    private int mRetryDurationMillis;
    private int mWifiSignalValue;
    private String mIwlanErrorWrappedClassname;
    private String mIwlanErrorWrappedStackFirstFrame;
    private int mErrorCountOfSameCause;
    private boolean mIsNetworkValidated;
    private int mTriggerReason;
    private int mValidationResult;
    private int mValidationTransportType;
    private int mValidationDurationMills;
    private long mValidationStartTimeMills;

    public void setMessageId(int messageId) {
        this.mMessageId = messageId;
    }

    public int getMessageId() {
        return mMessageId;
    }

    public void setApnType(int apnType) {
        this.mApnType = apnType;
    }

    public void setIsHandover(boolean isHandover) {
        this.mIsHandover = isHandover;
    }

    public void setEpdgServerAddress(String epdgServerAddress) {
        this.mEpdgServerAddress = epdgServerAddress;
    }

    public void setSourceRat(int sourceRat) {
        this.mSourceRat = sourceRat;
    }

    public void setIsCellularRoaming(boolean isCellularRoaming) {
        this.mIsCellularRoaming = isCellularRoaming;
    }

    public void setIsNetworkConnected(boolean isNetworkConnected) {
        this.mIsNetworkConnected = isNetworkConnected;
    }

    public void setTransportType(int transportType) {
        this.mTransportType = transportType;
    }

    public void setSetupRequestResult(int setupRequestResult) {
        this.mSetupRequestResult = setupRequestResult;
    }

    public void setIwlanError(int iwlanError) {
        this.mIwlanError = iwlanError;
    }

    public void setDataCallFailCause(int dataCallFailCause) {
        this.mDataCallFailCause = dataCallFailCause;
    }

    public void setProcessingDurationMillis(int processingDurationMillis) {
        this.mProcessingDurationMillis = processingDurationMillis;
    }

    public void setEpdgServerSelectionDurationMillis(int epdgServerSelectionDurationMillis) {
        this.mEpdgServerSelectionDurationMillis = epdgServerSelectionDurationMillis;
    }

    public void setIkeTunnelEstablishmentDurationMillis(int ikeTunnelEstablishmentDurationMillis) {
        this.mIkeTunnelEstablishmentDurationMillis = ikeTunnelEstablishmentDurationMillis;
    }

    public void setTunnelState(int tunnelState) {
        this.mTunnelState = tunnelState;
    }

    public void setHandoverFailureMode(int handoverFailureMode) {
        this.mHandoverFailureMode = handoverFailureMode;
    }

    public void setRetryDurationMillis(int retryDurationMillis) {
        this.mRetryDurationMillis = retryDurationMillis;
    }

    public void setWifiSignalValue(int wifiSignalValue) {
        this.mWifiSignalValue = wifiSignalValue;
    }

    public void setIwlanErrorWrappedClassnameAndStack(IwlanError iwlanError) {
        Throwable iwlanErrorWrapped = iwlanError.getException();
        if (iwlanErrorWrapped instanceof IkeInternalException
                || iwlanErrorWrapped instanceof IkeIOException) {
            iwlanErrorWrapped = iwlanErrorWrapped.getCause();
        }

        if (iwlanErrorWrapped == null) {
            this.mIwlanErrorWrappedClassname = null;
            this.mIwlanErrorWrappedStackFirstFrame = null;
            return;
        }

        this.mIwlanErrorWrappedClassname = iwlanErrorWrapped.getClass().getCanonicalName();

        StackTraceElement[] iwlanErrorWrappedStackTraceElements = iwlanErrorWrapped.getStackTrace();
        this.mIwlanErrorWrappedStackFirstFrame =
                iwlanErrorWrappedStackTraceElements.length != 0
                        ? iwlanErrorWrappedStackTraceElements[0].toString()
                        : null;
    }

    public String getIwlanErrorWrappedClassname() {
        return mIwlanErrorWrappedClassname;
    }

    public String getIwlanErrorWrappedStackFirstFrame() {
        return mIwlanErrorWrappedStackFirstFrame;
    }

    public void setErrorCountOfSameCause(int errorCount) {
        mErrorCountOfSameCause = errorCount;
    }

    public int getErrorCountOfSameCause() {
        return mErrorCountOfSameCause;
    }

    public void setIsNetworkValidated(boolean isNetworkValidated) {
        mIsNetworkValidated = isNetworkValidated;
    }

    public void setTriggerReason(int reason) {
        mTriggerReason = reason;
    }

    public int getTriggerReason() {
        return mTriggerReason;
    }

    public void setValidationResult(int validationResult) {
        mValidationResult = validationResult;
    }

    public int getValidationResult() {
        return mValidationResult;
    }

    public void setValidationTransportType(int transportType) {
        mValidationTransportType = transportType;
    }

    public int getValidationTransportType() {
        return mValidationTransportType;
    }

    public void setValidationDurationMills(int validationDurationMills) {
        mValidationDurationMills = validationDurationMills;
    }

    public int getValidationDurationMills() {
        return mValidationDurationMills;
    }

    public void setValidationStartTimeMills(long validationStartTimeMills) {
        mValidationStartTimeMills = validationStartTimeMills;
    }

    public long getValidationStartTimeMills() {
        return mValidationStartTimeMills;
    }

    public void sendMetricsData() {
        if (mMessageId == IwlanStatsLog.IWLAN_SETUP_DATA_CALL_RESULT_REPORTED) {
            Log.d(TAG, "Send metrics data IWLAN_SETUP_DATA_CALL_RESULT_REPORTED");
            IwlanStatsLog.write(
                    mMessageId,
                    mApnType,
                    mIsHandover,
                    mEpdgServerAddress,
                    mSourceRat,
                    mIsCellularRoaming,
                    mIsNetworkConnected,
                    mTransportType,
                    mSetupRequestResult,
                    mIwlanError,
                    mDataCallFailCause,
                    mProcessingDurationMillis,
                    mEpdgServerSelectionDurationMillis,
                    mIkeTunnelEstablishmentDurationMillis,
                    mTunnelState,
                    mHandoverFailureMode,
                    mRetryDurationMillis,
                    mIwlanErrorWrappedClassname,
                    mIwlanErrorWrappedStackFirstFrame,
                    mErrorCountOfSameCause,
                    mIsNetworkValidated);
        } else if (mMessageId == IwlanStatsLog.IWLAN_PDN_DISCONNECTED_REASON_REPORTED) {
            Log.d(TAG, "Send metrics data IWLAN_PDN_DISCONNECTED_REASON_REPORTED");
            IwlanStatsLog.write(
                    mMessageId,
                    mDataCallFailCause,
                    mIsNetworkConnected,
                    mTransportType,
                    mWifiSignalValue);
        } else if (mMessageId
                == IwlanStatsLog.IWLAN_UNDERLYING_NETWORK_VALIDATION_RESULT_REPORTED) {
            Log.d(TAG, "Send metrics data IWLAN_UNDERLYING_NETWORK_VALIDATION_RESULT_REPORTED");
            IwlanStatsLog.write(
                    mMessageId,
                    mTriggerReason,
                    mValidationResult,
                    mValidationTransportType,
                    mValidationDurationMills);
        } else {
            Log.d("IwlanMetrics", "Invalid Message ID: " + mMessageId);
        }
    }
}

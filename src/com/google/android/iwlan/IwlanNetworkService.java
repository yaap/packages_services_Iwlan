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

import static android.net.NetworkCapabilities.TRANSPORT_CELLULAR;
import static android.net.NetworkCapabilities.TRANSPORT_WIFI;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkSpecifier;
import android.net.TelephonyNetworkSpecifier;
import android.net.TransportInfo;
import android.net.vcn.VcnTransportInfo;
import android.net.vcn.VcnUtils;
import android.os.Handler;
import android.os.HandlerExecutor;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;
import android.telephony.AccessNetworkConstants;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.NetworkService;
import android.telephony.NetworkServiceCallback;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class IwlanNetworkService extends NetworkService {
    private static final String TAG = IwlanNetworkService.class.getSimpleName();

    private enum Transport {
        UNSPECIFIED_NETWORK,
        MOBILE,
        WIFI
    }

    private final Map<Integer, IwlanNetworkServiceProvider> mIwlanNetworkServiceProviders =
            new ConcurrentHashMap<>();

    private Context mContext;
    private IwlanNetworkMonitorCallback mNetworkMonitorCallback;
    private IwlanOnSubscriptionsChangedListener mSubsChangeListener;
    private Handler mIwlanNetworkServiceHandler;
    private HandlerThread mIwlanNetworkServiceHandlerThread;
    private boolean mIsNetworkConnected;
    // The current subscription with the active internet PDN. Need not be the default data sub.
    // If internet is over WiFi, this value will be SubscriptionManager.INVALID_SUBSCRIPTION_ID.
    private int mConnectedDataSub = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    private Transport mDefaultDataTransport = Transport.UNSPECIFIED_NETWORK;

    interface Dependencies {
        Looper getLooper();
    }

    private class DefaultDependencies implements Dependencies {
        @Override
        public Looper getLooper() {
            mIwlanNetworkServiceHandlerThread = new HandlerThread("IwlanNetworkServiceThread");
            mIwlanNetworkServiceHandlerThread.start();
            return mIwlanNetworkServiceHandlerThread.getLooper();
        }
    }

    private Dependencies mDependencies = new DefaultDependencies();

    // This callback runs in the same thread as IwlanNetworkServiceHandler
    private final class IwlanNetworkMonitorCallback extends ConnectivityManager.NetworkCallback {
        /** Called when the framework connects and has declared a new network ready for use. */
        @Override
        public void onAvailable(Network network) {
            Log.d(TAG, "onAvailable: " + network);
        }

        /**
         * Called when the network is about to be lost, typically because there are no outstanding
         * requests left for it. This may be paired with a {@link
         * ConnectivityManager.NetworkCallback#onAvailable} call with the new replacement network
         * for graceful handover. This method is not guaranteed to be called before {@link
         * ConnectivityManager.NetworkCallback#onLost} is called, for example in case a network is
         * suddenly disconnected.
         */
        @Override
        public void onLosing(Network network, int maxMsToLive) {
            Log.d(TAG, "onLosing: maxMsToLive: " + maxMsToLive + " network: " + network);
        }

        /**
         * Called when a network disconnects or otherwise no longer satisfies this request or
         * callback.
         */
        @Override
        public void onLost(Network network) {
            Log.d(TAG, "onLost: " + network);
            updateNetworkStateAndNotifyIfChanged(
                    /* isConnected= */ false,
                    Transport.UNSPECIFIED_NETWORK,
                    SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        }

        /** Called when the network corresponding to this request changes {@link LinkProperties}. */

        @Override
        public void onLinkPropertiesChanged(Network network, LinkProperties linkProperties) {
            Log.d(TAG, "onLinkPropertiesChanged: " + linkProperties);
        }

        /** Called when access to the specified network is blocked or unblocked. */
        @Override
        public void onBlockedStatusChanged(Network network, boolean blocked) {
            // TODO: check if we need to handle this
            Log.d(TAG, "onBlockedStatusChanged: " + " BLOCKED:" + blocked);
        }

        @Override
        public void onCapabilitiesChanged(
                @NonNull Network network, @NonNull NetworkCapabilities networkCapabilities) {
            // onCapabilitiesChanged is guaranteed to be called immediately after onAvailable per
            // API
            Log.d(TAG, "onCapabilitiesChanged: " + network);
            if (networkCapabilities != null) {
                if (networkCapabilities.hasTransport(TRANSPORT_CELLULAR)) {
                    updateNetworkStateAndNotifyIfChanged(
                            /* isConnected= */ true,
                            Transport.MOBILE,
                            getConnectedDataSub(
                                    mContext.getSystemService(ConnectivityManager.class),
                                    networkCapabilities));
                } else if (networkCapabilities.hasTransport(TRANSPORT_WIFI)) {
                    updateNetworkStateAndNotifyIfChanged(
                            /* isConnected= */ true,
                            Transport.WIFI,
                            SubscriptionManager.INVALID_SUBSCRIPTION_ID);
                } else {
                    Log.w(TAG, "Network does not have cellular or wifi capability");
                }
            }
        }
    }

    private final class IwlanOnSubscriptionsChangedListener
            extends SubscriptionManager.OnSubscriptionsChangedListener {
        /**
         * Callback invoked when there is any change to any SubscriptionInfo. Typically, this method
         * invokes {@link SubscriptionManager#getActiveSubscriptionInfoList}
         */
        @Override
        public void onSubscriptionsChanged() {
            for (IwlanNetworkServiceProvider np : mIwlanNetworkServiceProviders.values()) {
                np.subscriptionChanged();
            }
        }
    }

    class IwlanNetworkServiceProvider extends NetworkServiceProvider {
        private final IwlanNetworkService mIwlanNetworkService;
        private final String SUB_TAG;
        private boolean mIsSubActive = false;

        /**
         * Constructor
         *
         * @param slotIndex SIM slot id the data service provider associated with.
         */
        public IwlanNetworkServiceProvider(int slotIndex, IwlanNetworkService iwlanNetworkService) {
            super(slotIndex);
            SUB_TAG = TAG + "[" + slotIndex + "]";
            mIwlanNetworkService = iwlanNetworkService;

            // Register IwlanEventListener
            List<Integer> events = new ArrayList<Integer>();
            events.add(IwlanEventListener.CROSS_SIM_CALLING_ENABLE_EVENT);
            events.add(IwlanEventListener.CROSS_SIM_CALLING_DISABLE_EVENT);
            IwlanEventListener.getInstance(mContext, slotIndex)
                    .addEventListener(events, getIwlanNetworkServiceHandler());
        }

        @Override
        public void requestNetworkRegistrationInfo(int domain, NetworkServiceCallback callback) {
            getIwlanNetworkServiceHandler()
                    .post(() -> handleRequestNetworkRegistrationInfo(domain, callback, this));
        }

        /**
         * Called when the instance of network service is destroyed (e.g. got unbind or binder died)
         * or when the network service provider is removed. The extended class should implement this
         * method to perform cleanup works.
         */
        @Override
        public void close() {
            mIwlanNetworkService.removeNetworkServiceProvider(this);
            IwlanEventListener.getInstance(mContext, getSlotIndex())
                    .removeEventListener(getIwlanNetworkServiceHandler());
        }

        void subscriptionChanged() {
            boolean subActive =
                    getSubscriptionManager()
                                    .getActiveSubscriptionInfoForSimSlotIndex(getSlotIndex())
                            != null;
            if (subActive == mIsSubActive) {
                return;
            }
            mIsSubActive = subActive;
            if (subActive) {
                Log.d(SUB_TAG, "sub changed from not_ready --> ready");
            } else {
                Log.d(SUB_TAG, "sub changed from ready --> not_ready");
            }

            notifyNetworkRegistrationInfoChanged();
        }
    }

    private final class IwlanNetworkServiceHandler extends Handler {
        private final String TAG = IwlanNetworkServiceHandler.class.getSimpleName();

        @Override
        public void handleMessage(Message msg) {
            Log.d(TAG, "msg.what = " + eventToString(msg.what));

            IwlanNetworkServiceProvider iwlanNetworkServiceProvider;

            switch (msg.what) {
                case IwlanEventListener.CROSS_SIM_CALLING_ENABLE_EVENT,
                        IwlanEventListener.CROSS_SIM_CALLING_DISABLE_EVENT -> {
                    iwlanNetworkServiceProvider = mIwlanNetworkServiceProviders.get(msg.arg1);
                    iwlanNetworkServiceProvider.notifyNetworkRegistrationInfoChanged();
                }
                default -> throw new IllegalStateException("Unexpected value: " + msg.what);
            }
        }

        IwlanNetworkServiceHandler(Looper looper) {
            super(looper);
        }
    }



    private void handleRequestNetworkRegistrationInfo(
            int domain, NetworkServiceCallback callback, IwlanNetworkServiceProvider np) {
        if (callback == null) {
            Log.d(TAG, "Error: callback is null. returning");
            return;
        }
        if (domain != NetworkRegistrationInfo.DOMAIN_PS) {
            callback.onRequestNetworkRegistrationInfoComplete(
                    NetworkServiceCallback.RESULT_ERROR_UNSUPPORTED, null);
            return;
        }
        NetworkRegistrationInfo.Builder nriBuilder = new NetworkRegistrationInfo.Builder();
        nriBuilder
                .setAvailableServices(List.of(NetworkRegistrationInfo.SERVICE_TYPE_DATA))
                .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WLAN)
                .setEmergencyOnly(!np.mIsSubActive)
                .setDomain(NetworkRegistrationInfo.DOMAIN_PS);
        int slotId = np.getSlotIndex();
        if (!isNetworkConnected(
                isActiveDataOnOtherSub(slotId),
                IwlanHelper.isCrossSimCallingEnabled(mContext, slotId))) {
            nriBuilder
                    .setRegistrationState(
                            NetworkRegistrationInfo.REGISTRATION_STATE_NOT_REGISTERED_SEARCHING)
                    .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_UNKNOWN);
            Log.d(
                    TAG + "[" + slotId + "]",
                    ": reg state" + " REGISTRATION_STATE_NOT_REGISTERED_SEARCHING");
        } else {
            nriBuilder
                    .setRegistrationState(NetworkRegistrationInfo.REGISTRATION_STATE_HOME)
                    .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_IWLAN);
            Log.d(TAG + "[" + slotId + "]", ": reg state REGISTRATION_STATE_HOME");
        }
        callback.onRequestNetworkRegistrationInfoComplete(
                NetworkServiceCallback.RESULT_SUCCESS, nriBuilder.build());
    }

    /**
     * Create the instance of {@link NetworkServiceProvider}. Network service provider must override
     * this method to facilitate the creation of {@link NetworkServiceProvider} instances. The
     * system will call this method after binding the network service for each active SIM slot id.
     *
     * @param slotIndex SIM slot id the network service associated with.
     * @return Network service object. Null if failed to create the provider (e.g. invalid slot
     *     index)
     */
    @Override
    public NetworkServiceProvider onCreateNetworkServiceProvider(int slotIndex) {
        Log.d(TAG, "onCreateNetworkServiceProvider: slotidx:" + slotIndex);

        // TODO: validity check slot index

        IwlanNetworkServiceProvider np = new IwlanNetworkServiceProvider(slotIndex, this);
        getIwlanNetworkServiceHandler().post(() -> handleNetworkServiceProviderCreated(np));
        return np;
    }

    private int getConnectedDataSub(
            ConnectivityManager connectivityManager, NetworkCapabilities networkCapabilities) {
        int subId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;

        NetworkSpecifier specifier = networkCapabilities.getNetworkSpecifier();
        TransportInfo transportInfo = networkCapabilities.getTransportInfo();

        if (specifier instanceof TelephonyNetworkSpecifier telephonyNetworkSpecifier) {
            subId = telephonyNetworkSpecifier.getSubscriptionId();
        } else if (transportInfo instanceof VcnTransportInfo) {
            subId = VcnUtils.getSubIdFromVcnCaps(connectivityManager, networkCapabilities);
        }
        return subId;
    }

    private boolean isActiveDataOnOtherSub(int slotId) {
        int subId = IwlanHelper.getSubId(mContext, slotId);
        return this.mConnectedDataSub != SubscriptionManager.INVALID_SUBSCRIPTION_ID
                && subId != this.mConnectedDataSub;
    }

    private boolean isNetworkConnected(boolean isActiveDataOnOtherSub, boolean isCstEnabled) {
        if (isActiveDataOnOtherSub && isCstEnabled) {
            // For cross-SIM IWLAN (Transport.MOBILE), an active data PDN must be maintained on the
            // other subscription.
            if (this.mIsNetworkConnected && (this.mDefaultDataTransport != Transport.MOBILE)) {
                Log.e(TAG, "Internet is on other slot, but default transport is not MOBILE!");
            }
            return this.mIsNetworkConnected;
        } else {
            // For all other cases, only wifi transport can be used.
            return ((this.mDefaultDataTransport == Transport.WIFI) && this.mIsNetworkConnected);
        }
    }

    private void updateNetworkStateAndNotifyIfChanged(
            boolean isConnected, Transport transport, int subId) {
        if (isConnected && (transport == Transport.UNSPECIFIED_NETWORK)) {
            return; // Invalid state
        }

        boolean subIdChanged = (this.mConnectedDataSub != subId);
        boolean connectionStateChanged = (this.mIsNetworkConnected != isConnected);
        boolean transportChanged = (this.mDefaultDataTransport != transport);

        if (subIdChanged) {
            this.mConnectedDataSub = subId;
        }
        if (connectionStateChanged) {
            this.mIsNetworkConnected = isConnected;
        }
        if (transportChanged) {
            this.mDefaultDataTransport = transport;
        }

        if (subIdChanged || connectionStateChanged || transportChanged) {
            notifyRegistrationInfoChanged();
        }
    }

    private void notifyRegistrationInfoChanged() {
        for (IwlanNetworkServiceProvider np : mIwlanNetworkServiceProviders.values()) {
            np.notifyNetworkRegistrationInfoChanged();
        }
    }

    private void handleNetworkServiceProviderCreated(IwlanNetworkServiceProvider np) {
        if (mIwlanNetworkServiceProviders.isEmpty()) {
            initCallback();
        }
        addIwlanNetworkServiceProvider(np);
    }

    void addIwlanNetworkServiceProvider(IwlanNetworkServiceProvider np) {
        int slotIndex = np.getSlotIndex();
        if (mIwlanNetworkServiceProviders.containsKey(slotIndex)) {
            throw new IllegalStateException(
                    "NetworkServiceProvider already exists for slot " + slotIndex);
        }
        mIwlanNetworkServiceProviders.put(slotIndex, np);
    }

    void removeNetworkServiceProvider(IwlanNetworkServiceProvider np) {
        getIwlanNetworkServiceHandler().post(() -> handleRemoveNetworkServiceProvider(np));
    }

    private void handleRemoveNetworkServiceProvider(IwlanNetworkServiceProvider np) {
        int slotId = np.getSlotIndex();
        IwlanNetworkServiceProvider nsp = mIwlanNetworkServiceProviders.remove(slotId);
        if (nsp == null) {
            Log.w(TAG + "[" + slotId + "]", "No NetworkServiceProvider exists for slot!");
            return;
        }
        if (mIwlanNetworkServiceProviders.isEmpty()) {
            deinitCallback();
        }
    }

    void initCallback() {
        // register for default network callback
        mNetworkMonitorCallback = new IwlanNetworkMonitorCallback();
        getConnectivityManager()
                .registerDefaultNetworkCallback(
                        mNetworkMonitorCallback, getIwlanNetworkServiceHandler());
        Log.d(TAG, "Registered with Connectivity Service");

        /* register with subscription manager */
        mSubsChangeListener = new IwlanOnSubscriptionsChangedListener();
        getSubscriptionManager()
                .addOnSubscriptionsChangedListener(
                        new HandlerExecutor(getIwlanNetworkServiceHandler()), mSubsChangeListener);
        Log.d(TAG, "Registered with Subscription Service");
    }

    private void deinitCallback() {
        // deinit network related stuff
        getConnectivityManager().unregisterNetworkCallback(mNetworkMonitorCallback);
        mNetworkMonitorCallback = null;

        // deinit subscription manager related stuff
        getSubscriptionManager().removeOnSubscriptionsChangedListener(mSubsChangeListener);
        mSubsChangeListener = null;
        if (mIwlanNetworkServiceHandlerThread != null) {
            mIwlanNetworkServiceHandlerThread.quit();
            mIwlanNetworkServiceHandlerThread = null;
        }
        mIwlanNetworkServiceHandler = null;
    }

    void setDependencies(Dependencies dependencies) {
        mDependencies = dependencies;
    }

    @NonNull
    private Handler getIwlanNetworkServiceHandler() {
        if (mIwlanNetworkServiceHandler == null) {
            mIwlanNetworkServiceHandler = new IwlanNetworkServiceHandler(mDependencies.getLooper());
        }
        return mIwlanNetworkServiceHandler;
    }

    private static String eventToString(int event) {
        return switch (event) {
            case IwlanEventListener.CROSS_SIM_CALLING_ENABLE_EVENT ->
                    "CROSS_SIM_CALLING_ENABLE_EVENT";
            case IwlanEventListener.CROSS_SIM_CALLING_DISABLE_EVENT ->
                    "CROSS_SIM_CALLING_DISABLE_EVENT";
            default -> "Unknown(" + event + ")";
        };
    }

    @Override
    public void onCreate() {
        mContext = getApplicationContext();
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "IwlanNetworkService onBind");
        return super.onBind(intent);
    }

    @NonNull
    private ConnectivityManager getConnectivityManager() {
        return Objects.requireNonNull(mContext.getSystemService(ConnectivityManager.class));
    }

    @NonNull
    private SubscriptionManager getSubscriptionManager() {
        return Objects.requireNonNull(mContext.getSystemService(SubscriptionManager.class));
    }
}

/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.ondeviceintelligence;

import android.Manifest;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.app.AppGlobals;
import android.app.ondeviceintelligence.DownloadCallback;
import android.app.ondeviceintelligence.Feature;
import android.app.ondeviceintelligence.IDownloadCallback;
import android.app.ondeviceintelligence.IFeatureCallback;
import android.app.ondeviceintelligence.IFeatureDetailsCallback;
import android.app.ondeviceintelligence.IListFeaturesCallback;
import android.app.ondeviceintelligence.IOnDeviceIntelligenceManager;
import android.app.ondeviceintelligence.IProcessingSignal;
import android.app.ondeviceintelligence.IResponseCallback;
import android.app.ondeviceintelligence.IStreamingResponseCallback;
import android.app.ondeviceintelligence.ITokenInfoCallback;
import android.app.ondeviceintelligence.OnDeviceIntelligenceException;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.content.res.Resources;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.ICancellationSignal;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.PersistableBundle;
import android.os.RemoteCallback;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.os.ShellCallback;
import android.os.UserHandle;
import android.provider.DeviceConfig;
import android.service.ondeviceintelligence.IOnDeviceIntelligenceService;
import android.service.ondeviceintelligence.IOnDeviceSandboxedInferenceService;
import android.service.ondeviceintelligence.IProcessingUpdateStatusCallback;
import android.service.ondeviceintelligence.IRemoteProcessingService;
import android.service.ondeviceintelligence.IRemoteStorageService;
import android.service.ondeviceintelligence.OnDeviceIntelligenceService;
import android.service.ondeviceintelligence.OnDeviceSandboxedInferenceService;
import android.text.TextUtils;
import android.util.Slog;

import com.android.internal.R;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.infra.AndroidFuture;
import com.android.internal.infra.ServiceConnector;
import com.android.internal.os.BackgroundThread;
import com.android.server.LocalServices;
import com.android.server.SystemService;

import java.io.FileDescriptor;
import java.util.Objects;
import java.util.Set;

/**
 * This is the system service for handling calls on the
 * {@link android.app.ondeviceintelligence.OnDeviceIntelligenceManager}. This
 * service holds connection references to the underlying remote services i.e. the isolated service
 * {@link OnDeviceSandboxedInferenceService} and a regular
 * service counter part {@link OnDeviceIntelligenceService}.
 *
 * Note: Both the remote services run under the SYSTEM user, as we cannot have separate instance of
 * the Inference service for each user, due to possible high memory footprint.
 *
 * @hide
 */
public class OnDeviceIntelligenceManagerService extends SystemService {

    private static final String TAG = OnDeviceIntelligenceManagerService.class.getSimpleName();
    private static final String KEY_SERVICE_ENABLED = "service_enabled";

    /** Handler message to {@link #resetTemporaryServices()} */
    private static final int MSG_RESET_TEMPORARY_SERVICE = 0;

    /** Default value in absence of {@link DeviceConfig} override. */
    private static final boolean DEFAULT_SERVICE_ENABLED = true;
    private static final String NAMESPACE_ON_DEVICE_INTELLIGENCE = "ondeviceintelligence";

    private final Context mContext;
    protected final Object mLock = new Object();


    private RemoteOnDeviceSandboxedInferenceService mRemoteInferenceService;
    private RemoteOnDeviceIntelligenceService mRemoteOnDeviceIntelligenceService;
    volatile boolean mIsServiceEnabled;

    @GuardedBy("mLock")
    private String[] mTemporaryServiceNames;

    /**
     * Handler used to reset the temporary service names.
     */
    @GuardedBy("mLock")
    private Handler mTemporaryHandler;

    public OnDeviceIntelligenceManagerService(Context context) {
        super(context);
        mContext = context;
        mTemporaryServiceNames = new String[0];
    }

    @Override
    public void onStart() {
        publishBinderService(
                Context.ON_DEVICE_INTELLIGENCE_SERVICE, getOnDeviceIntelligenceManagerService(),
                /* allowIsolated = */true);
        LocalServices.addService(OnDeviceIntelligenceManagerInternal.class,
                OnDeviceIntelligenceManagerService.this::getRemoteConfiguredPackageName);
    }

    @Override
    public void onBootPhase(int phase) {
        if (phase == SystemService.PHASE_SYSTEM_SERVICES_READY) {
            DeviceConfig.addOnPropertiesChangedListener(
                    NAMESPACE_ON_DEVICE_INTELLIGENCE,
                    BackgroundThread.getExecutor(),
                    (properties) -> onDeviceConfigChange(properties.getKeyset()));

            mIsServiceEnabled = isServiceEnabled();
        }
    }

    private void onDeviceConfigChange(@NonNull Set<String> keys) {
        if (keys.contains(KEY_SERVICE_ENABLED)) {
            mIsServiceEnabled = isServiceEnabled();
        }
    }

    private boolean isServiceEnabled() {
        return DeviceConfig.getBoolean(
                NAMESPACE_ON_DEVICE_INTELLIGENCE,
                KEY_SERVICE_ENABLED, DEFAULT_SERVICE_ENABLED);
    }

    private IBinder getOnDeviceIntelligenceManagerService() {
        return new IOnDeviceIntelligenceManager.Stub() {
            @Override
            public String getRemoteServicePackageName() {
                return OnDeviceIntelligenceManagerService.this.getRemoteConfiguredPackageName();
            }

            @Override
            public void getVersion(RemoteCallback remoteCallback) {
                Slog.i(TAG, "OnDeviceIntelligenceManagerInternal getVersion");
                Objects.requireNonNull(remoteCallback);
                mContext.enforceCallingOrSelfPermission(
                        Manifest.permission.USE_ON_DEVICE_INTELLIGENCE, TAG);
                if (!mIsServiceEnabled) {
                    Slog.w(TAG, "Service not available");
                    remoteCallback.sendResult(null);
                    return;
                }
                ensureRemoteIntelligenceServiceInitialized();
                mRemoteOnDeviceIntelligenceService.run(
                        service -> service.getVersion(remoteCallback));
            }

            @Override
            public void getFeature(int id, IFeatureCallback featureCallback)
                    throws RemoteException {
                Slog.i(TAG, "OnDeviceIntelligenceManagerInternal getFeatures");
                Objects.requireNonNull(featureCallback);
                mContext.enforceCallingOrSelfPermission(
                        Manifest.permission.USE_ON_DEVICE_INTELLIGENCE, TAG);
                if (!mIsServiceEnabled) {
                    Slog.w(TAG, "Service not available");
                    featureCallback.onFailure(
                            OnDeviceIntelligenceException.ON_DEVICE_INTELLIGENCE_SERVICE_UNAVAILABLE,
                            "OnDeviceIntelligenceManagerService is unavailable",
                            PersistableBundle.EMPTY);
                    return;
                }
                ensureRemoteIntelligenceServiceInitialized();
                mRemoteOnDeviceIntelligenceService.run(
                        service -> service.getFeature(Binder.getCallingUid(), id, featureCallback));
            }

            @Override
            public void listFeatures(IListFeaturesCallback listFeaturesCallback)
                    throws RemoteException {
                Slog.i(TAG, "OnDeviceIntelligenceManagerInternal getFeatures");
                Objects.requireNonNull(listFeaturesCallback);
                mContext.enforceCallingOrSelfPermission(
                        Manifest.permission.USE_ON_DEVICE_INTELLIGENCE, TAG);
                if (!mIsServiceEnabled) {
                    Slog.w(TAG, "Service not available");
                    listFeaturesCallback.onFailure(
                            OnDeviceIntelligenceException.ON_DEVICE_INTELLIGENCE_SERVICE_UNAVAILABLE,
                            "OnDeviceIntelligenceManagerService is unavailable",
                            PersistableBundle.EMPTY);
                    return;
                }
                ensureRemoteIntelligenceServiceInitialized();
                mRemoteOnDeviceIntelligenceService.run(
                        service -> service.listFeatures(Binder.getCallingUid(),
                                listFeaturesCallback));
            }

            @Override
            public void getFeatureDetails(Feature feature,
                    IFeatureDetailsCallback featureDetailsCallback)
                    throws RemoteException {
                Slog.i(TAG, "OnDeviceIntelligenceManagerInternal getFeatureStatus");
                Objects.requireNonNull(feature);
                Objects.requireNonNull(featureDetailsCallback);
                mContext.enforceCallingOrSelfPermission(
                        Manifest.permission.USE_ON_DEVICE_INTELLIGENCE, TAG);
                if (!mIsServiceEnabled) {
                    Slog.w(TAG, "Service not available");
                    featureDetailsCallback.onFailure(
                            OnDeviceIntelligenceException.ON_DEVICE_INTELLIGENCE_SERVICE_UNAVAILABLE,
                            "OnDeviceIntelligenceManagerService is unavailable",
                            PersistableBundle.EMPTY);
                    return;
                }
                ensureRemoteIntelligenceServiceInitialized();
                mRemoteOnDeviceIntelligenceService.run(
                        service -> service.getFeatureDetails(Binder.getCallingUid(), feature,
                                featureDetailsCallback));
            }

            @Override
            public void requestFeatureDownload(Feature feature,
                    AndroidFuture cancellationSignalFuture,
                    IDownloadCallback downloadCallback) throws RemoteException {
                Slog.i(TAG, "OnDeviceIntelligenceManagerInternal requestFeatureDownload");
                Objects.requireNonNull(feature);
                Objects.requireNonNull(downloadCallback);
                mContext.enforceCallingOrSelfPermission(
                        Manifest.permission.USE_ON_DEVICE_INTELLIGENCE, TAG);
                if (!mIsServiceEnabled) {
                    Slog.w(TAG, "Service not available");
                    downloadCallback.onDownloadFailed(
                            DownloadCallback.DOWNLOAD_FAILURE_STATUS_UNAVAILABLE,
                            "OnDeviceIntelligenceManagerService is unavailable",
                            PersistableBundle.EMPTY);
                }
                ensureRemoteIntelligenceServiceInitialized();
                mRemoteOnDeviceIntelligenceService.run(
                        service -> service.requestFeatureDownload(Binder.getCallingUid(), feature,
                                cancellationSignalFuture,
                                downloadCallback));
            }


            @Override
            public void requestTokenInfo(Feature feature,
                    Bundle request,
                    AndroidFuture cancellationSignalFuture,
                    ITokenInfoCallback tokenInfoCallback) throws RemoteException {
                Slog.i(TAG, "OnDeviceIntelligenceManagerInternal requestTokenInfo");
                Objects.requireNonNull(feature);
                Objects.requireNonNull(request);
                Objects.requireNonNull(tokenInfoCallback);

                mContext.enforceCallingOrSelfPermission(
                        Manifest.permission.USE_ON_DEVICE_INTELLIGENCE, TAG);
                if (!mIsServiceEnabled) {
                    Slog.w(TAG, "Service not available");
                    tokenInfoCallback.onFailure(
                            OnDeviceIntelligenceException.ON_DEVICE_INTELLIGENCE_SERVICE_UNAVAILABLE,
                            "OnDeviceIntelligenceManagerService is unavailable",
                            PersistableBundle.EMPTY);
                }
                ensureRemoteInferenceServiceInitialized();

                mRemoteInferenceService.run(
                        service -> service.requestTokenInfo(Binder.getCallingUid(), feature,
                                request,
                                cancellationSignalFuture,
                                tokenInfoCallback));
            }

            @Override
            public void processRequest(Feature feature,
                    Bundle request,
                    int requestType,
                    AndroidFuture cancellationSignalFuture,
                    AndroidFuture processingSignalFuture,
                    IResponseCallback responseCallback)
                    throws RemoteException {
                Slog.i(TAG, "OnDeviceIntelligenceManagerInternal processRequest");
                Objects.requireNonNull(feature);
                Objects.requireNonNull(responseCallback);
                mContext.enforceCallingOrSelfPermission(
                        Manifest.permission.USE_ON_DEVICE_INTELLIGENCE, TAG);
                if (!mIsServiceEnabled) {
                    Slog.w(TAG, "Service not available");
                    responseCallback.onFailure(
                            OnDeviceIntelligenceException.PROCESSING_ERROR_SERVICE_UNAVAILABLE,
                            "OnDeviceIntelligenceManagerService is unavailable",
                            PersistableBundle.EMPTY);
                }
                ensureRemoteInferenceServiceInitialized();
                mRemoteInferenceService.run(
                        service -> service.processRequest(Binder.getCallingUid(), feature, request,
                                requestType,
                                cancellationSignalFuture, processingSignalFuture,
                                responseCallback));
            }

            @Override
            public void processRequestStreaming(Feature feature,
                    Bundle request,
                    int requestType,
                    AndroidFuture cancellationSignalFuture,
                    AndroidFuture processingSignalFuture,
                    IStreamingResponseCallback streamingCallback) throws RemoteException {
                Slog.i(TAG, "OnDeviceIntelligenceManagerInternal processRequestStreaming");
                Objects.requireNonNull(feature);
                Objects.requireNonNull(streamingCallback);
                mContext.enforceCallingOrSelfPermission(
                        Manifest.permission.USE_ON_DEVICE_INTELLIGENCE, TAG);
                if (!mIsServiceEnabled) {
                    Slog.w(TAG, "Service not available");
                    streamingCallback.onFailure(
                            OnDeviceIntelligenceException.PROCESSING_ERROR_SERVICE_UNAVAILABLE,
                            "OnDeviceIntelligenceManagerService is unavailable",
                            PersistableBundle.EMPTY);
                }
                ensureRemoteInferenceServiceInitialized();
                mRemoteInferenceService.run(
                        service -> service.processRequestStreaming(Binder.getCallingUid(), feature,
                                request, requestType,
                                cancellationSignalFuture, processingSignalFuture,
                                streamingCallback));
            }

            @Override
            public void onShellCommand(FileDescriptor in, FileDescriptor out, FileDescriptor err,
                    String[] args, ShellCallback callback, ResultReceiver resultReceiver) {
                new OnDeviceIntelligenceShellCommand(OnDeviceIntelligenceManagerService.this).exec(
                        this, in, out, err, args, callback, resultReceiver);
            }
        };
    }

    private void ensureRemoteIntelligenceServiceInitialized() {
        synchronized (mLock) {
            if (mRemoteOnDeviceIntelligenceService == null) {
                String serviceName = getServiceNames()[0];
                Binder.withCleanCallingIdentity(() -> validateServiceElevated(serviceName, false));
                mRemoteOnDeviceIntelligenceService = new RemoteOnDeviceIntelligenceService(mContext,
                        ComponentName.unflattenFromString(serviceName),
                        UserHandle.SYSTEM.getIdentifier());
                mRemoteOnDeviceIntelligenceService.setServiceLifecycleCallbacks(
                        new ServiceConnector.ServiceLifecycleCallbacks<>() {
                            @Override
                            public void onConnected(
                                    @NonNull IOnDeviceIntelligenceService service) {
                                try {
                                    service.ready();
                                    service.registerRemoteServices(
                                            getRemoteProcessingService());
                                } catch (RemoteException ex) {
                                    Slog.w(TAG, "Failed to send connected event", ex);
                                }
                            }
                        });
            }
        }
    }

    @NonNull
    private IRemoteProcessingService.Stub getRemoteProcessingService() {
        return new IRemoteProcessingService.Stub() {
            @Override
            public void updateProcessingState(
                    Bundle processingState,
                    IProcessingUpdateStatusCallback callback) {
                ensureRemoteInferenceServiceInitialized();
                mRemoteInferenceService.run(
                        service -> service.updateProcessingState(
                                processingState, callback));
            }
        };
    }

    private void ensureRemoteInferenceServiceInitialized() {
        synchronized (mLock) {
            if (mRemoteInferenceService == null) {
                String serviceName = getServiceNames()[1];
                Binder.withCleanCallingIdentity(() -> validateServiceElevated(serviceName, true));
                mRemoteInferenceService = new RemoteOnDeviceSandboxedInferenceService(mContext,
                        ComponentName.unflattenFromString(serviceName),
                        UserHandle.SYSTEM.getIdentifier());
                mRemoteInferenceService.setServiceLifecycleCallbacks(
                        new ServiceConnector.ServiceLifecycleCallbacks<>() {
                            @Override
                            public void onConnected(
                                    @NonNull IOnDeviceSandboxedInferenceService service) {
                                try {
                                    ensureRemoteIntelligenceServiceInitialized();
                                    mRemoteOnDeviceIntelligenceService.run(
                                            intelligenceService -> intelligenceService.notifyInferenceServiceConnected());
                                    service.registerRemoteStorageService(
                                            getIRemoteStorageService());
                                } catch (RemoteException ex) {
                                    Slog.w(TAG, "Failed to send connected event", ex);
                                }
                            }
                        });
            }
        }
    }

    @NonNull
    private IRemoteStorageService.Stub getIRemoteStorageService() {
        return new IRemoteStorageService.Stub() {
            @Override
            public void getReadOnlyFileDescriptor(
                    String filePath,
                    AndroidFuture<ParcelFileDescriptor> future) {
                mRemoteOnDeviceIntelligenceService.run(
                        service -> service.getReadOnlyFileDescriptor(
                                filePath, future));
            }

            @Override
            public void getReadOnlyFeatureFileDescriptorMap(
                    Feature feature,
                    RemoteCallback remoteCallback) {
                mRemoteOnDeviceIntelligenceService.run(
                        service -> service.getReadOnlyFeatureFileDescriptorMap(
                                feature, remoteCallback));
            }
        };
    }

    private void validateServiceElevated(String serviceName, boolean checkIsolated) {
        try {
            if (TextUtils.isEmpty(serviceName)) {
                throw new IllegalStateException(
                        "Remote service is not configured to complete the request");
            }
            ComponentName serviceComponent = ComponentName.unflattenFromString(
                    serviceName);
            ServiceInfo serviceInfo = AppGlobals.getPackageManager().getServiceInfo(
                    serviceComponent,
                    PackageManager.MATCH_DIRECT_BOOT_AWARE
                            | PackageManager.MATCH_DIRECT_BOOT_UNAWARE, 0);
            if (serviceInfo != null) {
                if (!checkIsolated) {
                    checkServiceRequiresPermission(serviceInfo,
                            Manifest.permission.BIND_ON_DEVICE_INTELLIGENCE_SERVICE);
                    return;
                }

                checkServiceRequiresPermission(serviceInfo,
                        Manifest.permission.BIND_ON_DEVICE_SANDBOXED_INFERENCE_SERVICE);
                if (!isIsolatedService(serviceInfo)) {
                    throw new SecurityException(
                            "Call required an isolated service, but the configured service: "
                                    + serviceName + ", is not isolated");
                }
            } else {
                throw new IllegalStateException(
                        "Remote service is not configured to complete the request.");
            }
        } catch (RemoteException e) {
            throw new IllegalStateException("Could not fetch service info for remote services", e);
        }
    }

    private static void checkServiceRequiresPermission(ServiceInfo serviceInfo,
            String requiredPermission) {
        final String permission = serviceInfo.permission;
        if (!requiredPermission.equals(permission)) {
            throw new SecurityException(String.format(
                    "Service %s requires %s permission. Found %s permission",
                    serviceInfo.getComponentName(),
                    requiredPermission,
                    serviceInfo.permission));
        }
    }

    private static boolean isIsolatedService(@NonNull ServiceInfo serviceInfo) {
        return (serviceInfo.flags & ServiceInfo.FLAG_ISOLATED_PROCESS) != 0
                && (serviceInfo.flags & ServiceInfo.FLAG_EXTERNAL_SERVICE) == 0;
    }

    @Nullable
    public String getRemoteConfiguredPackageName() {
        try {
            String[] serviceNames = getServiceNames();
            ComponentName componentName = ComponentName.unflattenFromString(serviceNames[1]);
            if (componentName != null) {
                return componentName.getPackageName();
            }
        } catch (Resources.NotFoundException e) {
            Slog.e(TAG, "Could not find resource", e);
        }

        return null;
    }


    protected String[] getServiceNames() throws Resources.NotFoundException {
        // TODO 329240495 : Consider a small class with explicit field names for the two services
        synchronized (mLock) {
            if (mTemporaryServiceNames != null && mTemporaryServiceNames.length == 2) {
                return mTemporaryServiceNames;
            }
        }
        return new String[]{mContext.getResources().getString(
                R.string.config_defaultOnDeviceIntelligenceService),
                mContext.getResources().getString(
                        R.string.config_defaultOnDeviceSandboxedInferenceService)};
    }

    @RequiresPermission(Manifest.permission.USE_ON_DEVICE_INTELLIGENCE)
    public void setTemporaryServices(@NonNull String[] componentNames, int durationMs) {
        Objects.requireNonNull(componentNames);
        enforceShellOnly(Binder.getCallingUid(), "setTemporaryServices");
        mContext.enforceCallingPermission(
                Manifest.permission.USE_ON_DEVICE_INTELLIGENCE, TAG);
        synchronized (mLock) {
            mTemporaryServiceNames = componentNames;
            mRemoteInferenceService.unbind();
            mRemoteOnDeviceIntelligenceService.unbind();
            mRemoteOnDeviceIntelligenceService = null;
            mRemoteInferenceService = null;
            if (mTemporaryHandler == null) {
                mTemporaryHandler = new Handler(Looper.getMainLooper(), null, true) {
                    @Override
                    public void handleMessage(Message msg) {
                        if (msg.what == MSG_RESET_TEMPORARY_SERVICE) {
                            synchronized (mLock) {
                                resetTemporaryServices();
                            }
                        } else {
                            Slog.wtf(TAG, "invalid handler msg: " + msg);
                        }
                    }
                };
            } else {
                mTemporaryHandler.removeMessages(MSG_RESET_TEMPORARY_SERVICE);
            }

            if (durationMs != -1) {
                mTemporaryHandler.sendEmptyMessageDelayed(MSG_RESET_TEMPORARY_SERVICE, durationMs);
            }
        }
    }

    public void resetTemporaryServices() {
        synchronized (mLock) {
            if (mTemporaryHandler != null) {
                mTemporaryHandler.removeMessages(MSG_RESET_TEMPORARY_SERVICE);
                mTemporaryHandler = null;
            }

            mRemoteInferenceService = null;
            mRemoteOnDeviceIntelligenceService = null;
            mTemporaryServiceNames = new String[0];
        }
    }

    /**
     * Throws if the caller is not of a shell (or root) UID.
     *
     * @param callingUid pass Binder.callingUid().
     */
    public static void enforceShellOnly(int callingUid, String message) {
        if (callingUid == android.os.Process.SHELL_UID
                || callingUid == android.os.Process.ROOT_UID) {
            return; // okay
        }

        throw new SecurityException(message + ": Only shell user can call it");
    }
}

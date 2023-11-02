/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.shade;

import static android.content.res.Configuration.ORIENTATION_PORTRAIT;

import static com.android.keyguard.KeyguardClockSwitch.LARGE;
import static com.android.systemui.dump.LogBufferHelperKt.logcatLogBuffer;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static kotlinx.coroutines.flow.FlowKt.emptyFlow;

import android.annotation.IdRes;
import android.content.ContentResolver;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.UserManager;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewPropertyAnimator;
import android.view.ViewStub;
import android.view.ViewTreeObserver;
import android.view.accessibility.AccessibilityManager;

import androidx.constraintlayout.widget.ConstraintSet;

import com.android.internal.jank.InteractionJankMonitor;
import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.UiEventLogger;
import com.android.internal.logging.testing.UiEventLoggerFake;
import com.android.internal.statusbar.IStatusBarService;
import com.android.internal.util.LatencyTracker;
import com.android.keyguard.KeyguardClockSwitch;
import com.android.keyguard.KeyguardClockSwitchController;
import com.android.keyguard.KeyguardSliceViewController;
import com.android.keyguard.KeyguardStatusView;
import com.android.keyguard.KeyguardStatusViewController;
import com.android.keyguard.KeyguardUpdateMonitor;
import com.android.keyguard.LockIconViewController;
import com.android.keyguard.dagger.KeyguardQsUserSwitchComponent;
import com.android.keyguard.dagger.KeyguardStatusBarViewComponent;
import com.android.keyguard.dagger.KeyguardStatusViewComponent;
import com.android.keyguard.dagger.KeyguardUserSwitcherComponent;
import com.android.keyguard.logging.KeyguardLogger;
import com.android.systemui.SysuiTestCase;
import com.android.systemui.biometrics.AuthController;
import com.android.systemui.bouncer.domain.interactor.AlternateBouncerInteractor;
import com.android.systemui.classifier.FalsingCollectorFake;
import com.android.systemui.classifier.FalsingManagerFake;
import com.android.systemui.common.ui.data.repository.FakeConfigurationRepository;
import com.android.systemui.common.ui.view.LongPressHandlingView;
import com.android.systemui.doze.DozeLog;
import com.android.systemui.dump.DumpManager;
import com.android.systemui.flags.FeatureFlags;
import com.android.systemui.fragments.FragmentHostManager;
import com.android.systemui.fragments.FragmentService;
import com.android.systemui.keyguard.KeyguardUnlockAnimationController;
import com.android.systemui.keyguard.KeyguardViewConfigurator;
import com.android.systemui.keyguard.data.repository.FakeKeyguardRepository;
import com.android.systemui.keyguard.domain.interactor.KeyguardBottomAreaInteractor;
import com.android.systemui.keyguard.domain.interactor.KeyguardFaceAuthInteractor;
import com.android.systemui.keyguard.domain.interactor.KeyguardInteractor;
import com.android.systemui.keyguard.domain.interactor.KeyguardInteractorFactory;
import com.android.systemui.keyguard.domain.interactor.KeyguardTransitionInteractor;
import com.android.systemui.keyguard.ui.viewmodel.DreamingToLockscreenTransitionViewModel;
import com.android.systemui.keyguard.ui.viewmodel.GoneToDreamingLockscreenHostedTransitionViewModel;
import com.android.systemui.keyguard.ui.viewmodel.GoneToDreamingTransitionViewModel;
import com.android.systemui.keyguard.ui.viewmodel.KeyguardBottomAreaViewModel;
import com.android.systemui.keyguard.ui.viewmodel.KeyguardLongPressViewModel;
import com.android.systemui.keyguard.ui.viewmodel.LockscreenToDreamingTransitionViewModel;
import com.android.systemui.keyguard.ui.viewmodel.LockscreenToOccludedTransitionViewModel;
import com.android.systemui.keyguard.ui.viewmodel.OccludedToLockscreenTransitionViewModel;
import com.android.systemui.keyguard.ui.viewmodel.PrimaryBouncerToGoneTransitionViewModel;
import com.android.systemui.media.controls.pipeline.MediaDataManager;
import com.android.systemui.media.controls.ui.KeyguardMediaController;
import com.android.systemui.media.controls.ui.MediaHierarchyManager;
import com.android.systemui.model.SysUiState;
import com.android.systemui.navigationbar.NavigationBarController;
import com.android.systemui.navigationbar.NavigationModeController;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.FalsingManager;
import com.android.systemui.plugins.qs.QS;
import com.android.systemui.power.domain.interactor.PowerInteractor;
import com.android.systemui.qs.QSFragmentLegacy;
import com.android.systemui.res.R;
import com.android.systemui.scene.SceneTestUtils;
import com.android.systemui.scene.shared.flag.FakeSceneContainerFlags;
import com.android.systemui.screenrecord.RecordingController;
import com.android.systemui.shade.data.repository.FakeShadeRepository;
import com.android.systemui.shade.data.repository.ShadeRepository;
import com.android.systemui.shade.domain.interactor.ShadeInteractor;
import com.android.systemui.shade.transition.ShadeTransitionController;
import com.android.systemui.statusbar.CommandQueue;
import com.android.systemui.statusbar.KeyguardIndicationController;
import com.android.systemui.statusbar.LockscreenShadeTransitionController;
import com.android.systemui.statusbar.NotificationRemoteInputManager;
import com.android.systemui.statusbar.NotificationShadeDepthController;
import com.android.systemui.statusbar.NotificationShadeWindowController;
import com.android.systemui.statusbar.NotificationShelfController;
import com.android.systemui.statusbar.PulseExpansionHandler;
import com.android.systemui.statusbar.QsFrameTranslateController;
import com.android.systemui.statusbar.StatusBarStateControllerImpl;
import com.android.systemui.statusbar.SysuiStatusBarStateController;
import com.android.systemui.statusbar.VibratorHelper;
import com.android.systemui.statusbar.disableflags.data.repository.FakeDisableFlagsRepository;
import com.android.systemui.statusbar.notification.ConversationNotificationManager;
import com.android.systemui.statusbar.notification.DynamicPrivacyController;
import com.android.systemui.statusbar.notification.NotificationWakeUpCoordinator;
import com.android.systemui.statusbar.notification.NotificationWakeUpCoordinatorLogger;
import com.android.systemui.statusbar.notification.row.NotificationGutsManager;
import com.android.systemui.statusbar.notification.stack.AmbientState;
import com.android.systemui.statusbar.notification.stack.NotificationListContainer;
import com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout;
import com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayoutController;
import com.android.systemui.statusbar.notification.stack.NotificationStackSizeCalculator;
import com.android.systemui.statusbar.notification.stack.domain.interactor.SharedNotificationContainerInteractor;
import com.android.systemui.statusbar.phone.CentralSurfaces;
import com.android.systemui.statusbar.phone.ConfigurationControllerImpl;
import com.android.systemui.statusbar.phone.DozeParameters;
import com.android.systemui.statusbar.phone.HeadsUpAppearanceController;
import com.android.systemui.statusbar.phone.HeadsUpTouchHelper;
import com.android.systemui.statusbar.phone.KeyguardBottomAreaView;
import com.android.systemui.statusbar.phone.KeyguardBottomAreaViewController;
import com.android.systemui.statusbar.phone.KeyguardBypassController;
import com.android.systemui.statusbar.phone.KeyguardClockPositionAlgorithm;
import com.android.systemui.statusbar.phone.KeyguardStatusBarView;
import com.android.systemui.statusbar.phone.KeyguardStatusBarViewController;
import com.android.systemui.statusbar.phone.LightBarController;
import com.android.systemui.statusbar.phone.LockscreenGestureLogger;
import com.android.systemui.statusbar.phone.ScreenOffAnimationController;
import com.android.systemui.statusbar.phone.ScrimController;
import com.android.systemui.statusbar.phone.StatusBarKeyguardViewManager;
import com.android.systemui.statusbar.phone.StatusBarTouchableRegionManager;
import com.android.systemui.statusbar.phone.TapAgainViewController;
import com.android.systemui.statusbar.phone.UnlockedScreenOffAnimationController;
import com.android.systemui.statusbar.pipeline.mobile.data.repository.FakeUserSetupRepository;
import com.android.systemui.statusbar.policy.CastController;
import com.android.systemui.statusbar.policy.ConfigurationController;
import com.android.systemui.statusbar.policy.HeadsUpManager;
import com.android.systemui.statusbar.policy.KeyguardQsUserSwitchController;
import com.android.systemui.statusbar.policy.KeyguardStateController;
import com.android.systemui.statusbar.policy.KeyguardUserSwitcherController;
import com.android.systemui.statusbar.policy.KeyguardUserSwitcherView;
import com.android.systemui.statusbar.policy.ResourcesSplitShadeStateController;
import com.android.systemui.statusbar.policy.data.repository.FakeDeviceProvisioningRepository;
import com.android.systemui.statusbar.window.StatusBarWindowStateController;
import com.android.systemui.unfold.SysUIUnfoldComponent;
import com.android.systemui.user.domain.interactor.UserSwitcherInteractor;
import com.android.systemui.util.kotlin.JavaAdapter;
import com.android.systemui.util.time.FakeSystemClock;
import com.android.systemui.util.time.SystemClock;
import com.android.wm.shell.animation.FlingAnimationUtils;

import dagger.Lazy;

import org.junit.After;
import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;

import java.util.List;
import java.util.Optional;

import kotlinx.coroutines.CoroutineDispatcher;
import kotlinx.coroutines.flow.StateFlowKt;
import kotlinx.coroutines.test.TestScope;

public class NotificationPanelViewControllerBaseTest extends SysuiTestCase {

    protected static final int SPLIT_SHADE_FULL_TRANSITION_DISTANCE = 400;
    protected static final int NOTIFICATION_SCRIM_TOP_PADDING_IN_SPLIT_SHADE = 50;
    protected static final int PANEL_WIDTH = 500; // Random value just for the test.

    @Mock protected CentralSurfaces mCentralSurfaces;
    @Mock protected NotificationStackScrollLayout mNotificationStackScrollLayout;
    @Mock protected KeyguardBottomAreaView mKeyguardBottomArea;
    @Mock protected KeyguardBottomAreaViewController mKeyguardBottomAreaViewController;
    @Mock protected ViewPropertyAnimator mViewPropertyAnimator;
    @Mock protected KeyguardBottomAreaView mQsFrame;
    @Mock protected HeadsUpManager mHeadsUpManager;
    @Mock protected NotificationShelfController mNotificationShelfController;
    @Mock protected NotificationGutsManager mGutsManager;
    @Mock protected KeyguardStatusBarView mKeyguardStatusBar;
    @Mock protected KeyguardUserSwitcherView mUserSwitcherView;
    @Mock protected ViewStub mUserSwitcherStubView;
    @Mock protected HeadsUpTouchHelper.Callback mHeadsUpCallback;
    @Mock protected KeyguardUpdateMonitor mUpdateMonitor;
    @Mock protected KeyguardBypassController mKeyguardBypassController;
    @Mock protected DozeParameters mDozeParameters;
    @Mock protected ScreenOffAnimationController mScreenOffAnimationController;
    @Mock protected NotificationPanelView mView;
    @Mock protected LayoutInflater mLayoutInflater;
    @Mock protected FeatureFlags mFeatureFlags;
    @Mock protected DynamicPrivacyController mDynamicPrivacyController;
    @Mock protected StatusBarTouchableRegionManager mStatusBarTouchableRegionManager;
    @Mock protected KeyguardStateController mKeyguardStateController;
    @Mock protected DozeLog mDozeLog;
    private final ShadeLogger mShadeLog = new ShadeLogger(logcatLogBuffer());
    @Mock protected CommandQueue mCommandQueue;
    @Mock protected VibratorHelper mVibratorHelper;
    @Mock protected LatencyTracker mLatencyTracker;
    @Mock protected PowerManager mPowerManager;
    @Mock protected AccessibilityManager mAccessibilityManager;
    @Mock protected MetricsLogger mMetricsLogger;
    @Mock protected Resources mResources;
    @Mock protected Configuration mConfiguration;
    @Mock protected KeyguardClockSwitch mKeyguardClockSwitch;
    @Mock protected MediaHierarchyManager mMediaHierarchyManager;
    @Mock protected ConversationNotificationManager mConversationNotificationManager;
    @Mock protected StatusBarKeyguardViewManager mStatusBarKeyguardViewManager;
    @Mock protected KeyguardStatusViewComponent.Factory mKeyguardStatusViewComponentFactory;
    @Mock protected KeyguardQsUserSwitchComponent.Factory mKeyguardQsUserSwitchComponentFactory;
    @Mock protected KeyguardQsUserSwitchComponent mKeyguardQsUserSwitchComponent;
    @Mock protected KeyguardQsUserSwitchController mKeyguardQsUserSwitchController;
    @Mock protected KeyguardUserSwitcherComponent.Factory mKeyguardUserSwitcherComponentFactory;
    @Mock protected KeyguardUserSwitcherComponent mKeyguardUserSwitcherComponent;
    @Mock protected KeyguardUserSwitcherController mKeyguardUserSwitcherController;
    @Mock protected KeyguardStatusViewComponent mKeyguardStatusViewComponent;
    @Mock protected KeyguardStatusBarViewComponent.Factory mKeyguardStatusBarViewComponentFactory;
    @Mock protected KeyguardStatusBarViewComponent mKeyguardStatusBarViewComponent;
    @Mock protected KeyguardClockSwitchController mKeyguardClockSwitchController;
    @Mock protected KeyguardStatusBarViewController mKeyguardStatusBarViewController;
    @Mock protected LightBarController mLightBarController;
    @Mock protected NotificationStackScrollLayoutController
            mNotificationStackScrollLayoutController;
    @Mock protected NotificationShadeDepthController mNotificationShadeDepthController;
    @Mock protected LockscreenShadeTransitionController mLockscreenShadeTransitionController;
    @Mock protected AuthController mAuthController;
    @Mock protected ScrimController mScrimController;
    @Mock protected MediaDataManager mMediaDataManager;
    @Mock protected AmbientState mAmbientState;
    @Mock protected UserManager mUserManager;
    @Mock protected UiEventLogger mUiEventLogger;
    @Mock protected LockIconViewController mLockIconViewController;
    @Mock protected KeyguardViewConfigurator mKeyguardViewConfigurator;
    @Mock protected KeyguardMediaController mKeyguardMediaController;
    @Mock protected NavigationModeController mNavigationModeController;
    @Mock protected NavigationBarController mNavigationBarController;
    @Mock protected QuickSettingsController mQsController;
    @Mock protected ShadeHeaderController mShadeHeaderController;
    @Mock protected ContentResolver mContentResolver;
    @Mock protected TapAgainViewController mTapAgainViewController;
    @Mock protected KeyguardIndicationController mKeyguardIndicationController;
    @Mock protected FragmentService mFragmentService;
    @Mock protected FragmentHostManager mFragmentHostManager;
    @Mock protected IStatusBarService mStatusBarService;
    @Mock protected NotificationRemoteInputManager mNotificationRemoteInputManager;
    @Mock protected RecordingController mRecordingController;
    @Mock protected LockscreenGestureLogger mLockscreenGestureLogger;
    @Mock protected DumpManager mDumpManager;
    @Mock protected InteractionJankMonitor mInteractionJankMonitor;
    @Mock protected NotificationsQSContainerController mNotificationsQSContainerController;
    @Mock protected QsFrameTranslateController mQsFrameTranslateController;
    @Mock protected StatusBarWindowStateController mStatusBarWindowStateController;
    @Mock protected KeyguardUnlockAnimationController mKeyguardUnlockAnimationController;
    @Mock protected NotificationShadeWindowController mNotificationShadeWindowController;
    @Mock protected SysUiState mSysUiState;
    @Mock protected NotificationListContainer mNotificationListContainer;
    @Mock protected NotificationStackSizeCalculator mNotificationStackSizeCalculator;
    @Mock protected UnlockedScreenOffAnimationController mUnlockedScreenOffAnimationController;
    @Mock protected ShadeTransitionController mShadeTransitionController;
    @Mock protected QS mQs;
    @Mock protected QSFragmentLegacy mQSFragment;
    @Mock protected ViewGroup mQsHeader;
    @Mock protected ViewParent mViewParent;
    @Mock protected ViewTreeObserver mViewTreeObserver;
    @Mock protected KeyguardBottomAreaViewModel mKeyguardBottomAreaViewModel;
    @Mock protected DreamingToLockscreenTransitionViewModel
            mDreamingToLockscreenTransitionViewModel;
    @Mock protected OccludedToLockscreenTransitionViewModel
            mOccludedToLockscreenTransitionViewModel;
    @Mock protected LockscreenToDreamingTransitionViewModel
            mLockscreenToDreamingTransitionViewModel;
    @Mock protected LockscreenToOccludedTransitionViewModel
            mLockscreenToOccludedTransitionViewModel;
    @Mock protected GoneToDreamingTransitionViewModel mGoneToDreamingTransitionViewModel;
    @Mock protected GoneToDreamingLockscreenHostedTransitionViewModel
            mGoneToDreamingLockscreenHostedTransitionViewModel;
    @Mock protected PrimaryBouncerToGoneTransitionViewModel
            mPrimaryBouncerToGoneTransitionViewModel;
    @Mock protected KeyguardTransitionInteractor mKeyguardTransitionInteractor;
    @Mock protected KeyguardLongPressViewModel mKeyuardLongPressViewModel;
    @Mock protected AlternateBouncerInteractor mAlternateBouncerInteractor;
    @Mock protected MotionEvent mDownMotionEvent;
    @Mock protected CoroutineDispatcher mMainDispatcher;
    @Mock protected KeyguardSliceViewController mKeyguardSliceViewController;
    private final KeyguardLogger mKeyguardLogger = new KeyguardLogger(logcatLogBuffer());
    @Mock protected KeyguardStatusView mKeyguardStatusView;
    @Captor
    protected ArgumentCaptor<NotificationStackScrollLayout.OnEmptySpaceClickListener>
            mEmptySpaceClickListenerCaptor;
    @Mock protected ActivityStarter mActivityStarter;
    @Mock protected KeyguardFaceAuthInteractor mKeyguardFaceAuthInteractor;
    @Mock private JavaAdapter mJavaAdapter;
    @Mock private CastController mCastController;
    @Mock private SharedNotificationContainerInteractor mSharedNotificationContainerInteractor;
    @Mock private KeyguardClockPositionAlgorithm mKeyguardClockPositionAlgorithm;

    protected final int mMaxUdfpsBurnInOffsetY = 5;
    protected KeyguardBottomAreaInteractor mKeyguardBottomAreaInteractor;
    protected FakeKeyguardRepository mFakeKeyguardRepository;
    protected KeyguardInteractor mKeyguardInteractor;
    protected SceneTestUtils mUtils = new SceneTestUtils(this);
    protected TestScope mTestScope = mUtils.getTestScope();
    protected ShadeInteractor mShadeInteractor;
    protected PowerInteractor mPowerInteractor;
    protected NotificationPanelViewController.TouchHandler mTouchHandler;
    protected ConfigurationController mConfigurationController;
    protected SysuiStatusBarStateController mStatusBarStateController;
    protected NotificationPanelViewController mNotificationPanelViewController;
    protected View.AccessibilityDelegate mAccessibilityDelegate;
    protected NotificationsQuickSettingsContainer mNotificationContainerParent;
    protected List<View.OnAttachStateChangeListener> mOnAttachStateChangeListeners;
    protected Handler mMainHandler;
    protected View.OnLayoutChangeListener mLayoutChangeListener;
    protected KeyguardStatusViewController mKeyguardStatusViewController;
    protected ShadeRepository mShadeRepository;

    protected final FalsingManagerFake mFalsingManager = new FalsingManagerFake();
    protected final Optional<SysUIUnfoldComponent> mSysUIUnfoldComponent = Optional.empty();
    protected final DisplayMetrics mDisplayMetrics = new DisplayMetrics();
    protected final ShadeExpansionStateManager mShadeExpansionStateManager =
            new ShadeExpansionStateManager();

    protected QuickSettingsController mQuickSettingsController;
    @Mock protected Lazy<NotificationPanelViewController> mNotificationPanelViewControllerLazy;

    protected FragmentHostManager.FragmentListener mFragmentListener;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        mMainDispatcher = getMainDispatcher();
        KeyguardInteractorFactory.WithDependencies keyguardInteractorDeps =
                KeyguardInteractorFactory.create();
        mFakeKeyguardRepository = keyguardInteractorDeps.getRepository();
        mKeyguardBottomAreaInteractor = new KeyguardBottomAreaInteractor(mFakeKeyguardRepository);
        mKeyguardInteractor = keyguardInteractorDeps.getKeyguardInteractor();
        mShadeRepository = new FakeShadeRepository();
        mPowerInteractor = keyguardInteractorDeps.getPowerInteractor();
        when(mKeyguardTransitionInteractor.isInTransitionToStateWhere(any())).thenReturn(
                StateFlowKt.MutableStateFlow(false));
        mShadeInteractor = new ShadeInteractor(
                mTestScope.getBackgroundScope(),
                new FakeDeviceProvisioningRepository(),
                new FakeDisableFlagsRepository(),
                mDozeParameters,
                new FakeSceneContainerFlags(),
                mUtils::sceneInteractor,
                mFakeKeyguardRepository,
                mKeyguardTransitionInteractor,
                mPowerInteractor,
                new FakeUserSetupRepository(),
                mock(UserSwitcherInteractor.class),
                new SharedNotificationContainerInteractor(
                        new FakeConfigurationRepository(),
                        mContext,
                        new ResourcesSplitShadeStateController()
                ),
                mShadeRepository
        );

        SystemClock systemClock = new FakeSystemClock();
        mStatusBarStateController = new StatusBarStateControllerImpl(mUiEventLogger,
                mInteractionJankMonitor, mJavaAdapter, () -> mShadeInteractor);

        KeyguardStatusView keyguardStatusView = new KeyguardStatusView(mContext);
        keyguardStatusView.setId(R.id.keyguard_status_view);
        mKeyguardStatusViewController = spy(new KeyguardStatusViewController(
                mKeyguardStatusView,
                mKeyguardSliceViewController,
                mKeyguardClockSwitchController,
                mKeyguardStateController,
                mUpdateMonitor,
                mConfigurationController,
                mDozeParameters,
                mScreenOffAnimationController,
                mKeyguardLogger,
                mFeatureFlags,
                mInteractionJankMonitor,
                mKeyguardInteractor,
                mKeyguardTransitionInteractor,
                mDumpManager,
                mPowerInteractor));

        when(mAuthController.isUdfpsEnrolled(anyInt())).thenReturn(false);
        when(mHeadsUpCallback.getContext()).thenReturn(mContext);
        when(mView.getResources()).thenReturn(mResources);
        when(mView.getWidth()).thenReturn(PANEL_WIDTH);
        when(mResources.getConfiguration()).thenReturn(mConfiguration);
        mConfiguration.orientation = ORIENTATION_PORTRAIT;
        when(mResources.getDisplayMetrics()).thenReturn(mDisplayMetrics);
        mDisplayMetrics.density = 100;
        when(mResources.getBoolean(R.bool.config_enableNotificationShadeDrag)).thenReturn(true);
        when(mResources.getDimensionPixelSize(R.dimen.udfps_burn_in_offset_y))
                .thenReturn(mMaxUdfpsBurnInOffsetY);
        when(mResources.getDimensionPixelSize(R.dimen.notifications_top_padding_split_shade))
                .thenReturn(NOTIFICATION_SCRIM_TOP_PADDING_IN_SPLIT_SHADE);
        when(mResources.getDimensionPixelSize(R.dimen.notification_panel_margin_horizontal))
                .thenReturn(10);
        when(mResources.getDimensionPixelSize(R.dimen.split_shade_full_transition_distance))
                .thenReturn(SPLIT_SHADE_FULL_TRANSITION_DISTANCE);
        when(mView.getContext()).thenReturn(getContext());
        when(mView.findViewById(R.id.keyguard_header)).thenReturn(mKeyguardStatusBar);
        when(mView.findViewById(R.id.keyguard_user_switcher_view)).thenReturn(mUserSwitcherView);
        when(mView.findViewById(R.id.keyguard_user_switcher_stub)).thenReturn(
                mUserSwitcherStubView);
        when(mView.findViewById(R.id.keyguard_clock_container)).thenReturn(mKeyguardClockSwitch);
        when(mView.findViewById(R.id.notification_stack_scroller))
                .thenReturn(mNotificationStackScrollLayout);
        when(mNotificationStackScrollLayoutController.getHeight()).thenReturn(1000);
        when(mNotificationStackScrollLayoutController.getHeadsUpCallback())
                .thenReturn(mHeadsUpCallback);
        when(mKeyguardBottomAreaViewController.getView()).thenReturn(mKeyguardBottomArea);
        when(mView.findViewById(R.id.keyguard_bottom_area)).thenReturn(mKeyguardBottomArea);
        when(mKeyguardBottomArea.animate()).thenReturn(mViewPropertyAnimator);
        when(mView.animate()).thenReturn(mViewPropertyAnimator);
        when(mKeyguardStatusView.animate()).thenReturn(mViewPropertyAnimator);
        when(mViewPropertyAnimator.translationX(anyFloat())).thenReturn(mViewPropertyAnimator);
        when(mViewPropertyAnimator.alpha(anyFloat())).thenReturn(mViewPropertyAnimator);
        when(mViewPropertyAnimator.setDuration(anyLong())).thenReturn(mViewPropertyAnimator);
        when(mViewPropertyAnimator.setStartDelay(anyLong())).thenReturn(mViewPropertyAnimator);
        when(mViewPropertyAnimator.setInterpolator(any())).thenReturn(mViewPropertyAnimator);
        when(mViewPropertyAnimator.setListener(any())).thenReturn(mViewPropertyAnimator);
        when(mViewPropertyAnimator.setUpdateListener(any())).thenReturn(mViewPropertyAnimator);
        when(mViewPropertyAnimator.withEndAction(any())).thenReturn(mViewPropertyAnimator);
        when(mView.findViewById(R.id.qs_frame)).thenReturn(mQsFrame);
        when(mView.findViewById(R.id.keyguard_status_view))
                .thenReturn(mock(KeyguardStatusView.class));
        View rootView = mock(View.class);
        when(mView.getRootView()).thenReturn(rootView);
        when(rootView.findViewById(R.id.keyguard_status_view))
                .thenReturn(mock(KeyguardStatusView.class));
        mNotificationContainerParent = new NotificationsQuickSettingsContainer(getContext(), null);
        mNotificationContainerParent.addView(keyguardStatusView);
        mNotificationContainerParent.onFinishInflate();
        when(mView.findViewById(R.id.notification_container_parent))
                .thenReturn(mNotificationContainerParent);
        when(mFragmentService.getFragmentHostManager(mView)).thenReturn(mFragmentHostManager);
        FlingAnimationUtils.Builder flingAnimationUtilsBuilder = new FlingAnimationUtils.Builder(
                mDisplayMetrics);
        when(mKeyguardQsUserSwitchComponentFactory.build(any()))
                .thenReturn(mKeyguardQsUserSwitchComponent);
        when(mKeyguardQsUserSwitchComponent.getKeyguardQsUserSwitchController())
                .thenReturn(mKeyguardQsUserSwitchController);
        when(mKeyguardUserSwitcherComponentFactory.build(any()))
                .thenReturn(mKeyguardUserSwitcherComponent);
        when(mKeyguardUserSwitcherComponent.getKeyguardUserSwitcherController())
                .thenReturn(mKeyguardUserSwitcherController);
        when(mScreenOffAnimationController.shouldAnimateClockChange()).thenReturn(true);
        when(mQs.getView()).thenReturn(mView);
        when(mQSFragment.getView()).thenReturn(mView);
        doAnswer(invocation -> {
            mFragmentListener = invocation.getArgument(1);
            return null;
        }).when(mFragmentHostManager).addTagListener(eq(QS.TAG), any());
        doAnswer((Answer<Void>) invocation -> {
            mTouchHandler = invocation.getArgument(0);
            return null;
        }).when(mView).setOnTouchListener(any(NotificationPanelViewController.TouchHandler.class));

        // Dreaming->Lockscreen
        when(mKeyguardTransitionInteractor.getDreamingToLockscreenTransition())
                .thenReturn(emptyFlow());
        when(mDreamingToLockscreenTransitionViewModel.getLockscreenAlpha())
                .thenReturn(emptyFlow());
        when(mDreamingToLockscreenTransitionViewModel.lockscreenTranslationY(anyInt()))
                .thenReturn(emptyFlow());

        // Occluded->Lockscreen
        when(mKeyguardTransitionInteractor.getOccludedToLockscreenTransition())
                .thenReturn(emptyFlow());
        when(mOccludedToLockscreenTransitionViewModel.getLockscreenAlpha())
                .thenReturn(emptyFlow());
        when(mOccludedToLockscreenTransitionViewModel.lockscreenTranslationY(anyInt()))
                .thenReturn(emptyFlow());

        // Lockscreen->Dreaming
        when(mKeyguardTransitionInteractor.getLockscreenToDreamingTransition())
                .thenReturn(emptyFlow());
        when(mLockscreenToDreamingTransitionViewModel.getLockscreenAlpha())
                .thenReturn(emptyFlow());
        when(mLockscreenToDreamingTransitionViewModel.lockscreenTranslationY(anyInt()))
                .thenReturn(emptyFlow());

        // Gone->Dreaming
        when(mKeyguardTransitionInteractor.getGoneToDreamingTransition())
                .thenReturn(emptyFlow());
        when(mGoneToDreamingTransitionViewModel.getLockscreenAlpha())
                .thenReturn(emptyFlow());
        when(mGoneToDreamingTransitionViewModel.lockscreenTranslationY(anyInt()))
                .thenReturn(emptyFlow());

        // Gone->Dreaming lockscreen hosted
        when(mKeyguardTransitionInteractor.getGoneToDreamingLockscreenHostedTransition())
                .thenReturn(emptyFlow());
        when(mGoneToDreamingLockscreenHostedTransitionViewModel.getLockscreenAlpha())
                .thenReturn(emptyFlow());

        // Dreaming lockscreen hosted->Lockscreen
        when(mKeyguardTransitionInteractor.getDreamingLockscreenHostedToLockscreenTransition())
                .thenReturn(emptyFlow());

        // Lockscreen->Dreaming lockscreen hosted
        when(mKeyguardTransitionInteractor.getLockscreenToDreamingLockscreenHostedTransition())
                .thenReturn(emptyFlow());

        // Lockscreen->Occluded
        when(mKeyguardTransitionInteractor.getLockscreenToOccludedTransition())
                .thenReturn(emptyFlow());
        when(mLockscreenToOccludedTransitionViewModel.getLockscreenAlpha())
                .thenReturn(emptyFlow());
        when(mLockscreenToOccludedTransitionViewModel.lockscreenTranslationY(anyInt()))
                .thenReturn(emptyFlow());

        // Primary Bouncer->Gone
        when(mPrimaryBouncerToGoneTransitionViewModel.getLockscreenAlpha())
                .thenReturn(emptyFlow());

        NotificationWakeUpCoordinator coordinator =
                new NotificationWakeUpCoordinator(
                        mDumpManager,
                        mock(HeadsUpManager.class),
                        new StatusBarStateControllerImpl(new UiEventLoggerFake(),
                                mInteractionJankMonitor,
                                mJavaAdapter, () -> mShadeInteractor),
                        mKeyguardBypassController,
                        mDozeParameters,
                        mScreenOffAnimationController,
                        new NotificationWakeUpCoordinatorLogger(logcatLogBuffer()));
        mConfigurationController = new ConfigurationControllerImpl(mContext);
        PulseExpansionHandler expansionHandler = new PulseExpansionHandler(
                mContext,
                coordinator,
                mKeyguardBypassController,
                mHeadsUpManager,
                mConfigurationController,
                mStatusBarStateController,
                mFalsingManager,
                mShadeInteractor,
                mLockscreenShadeTransitionController,
                mDumpManager);
        when(mKeyguardStatusViewComponentFactory.build(any(), any()))
                .thenReturn(mKeyguardStatusViewComponent);
        when(mKeyguardStatusViewComponent.getKeyguardClockSwitchController())
                .thenReturn(mKeyguardClockSwitchController);
        when(mKeyguardStatusViewComponent.getKeyguardStatusViewController())
                .thenReturn(mKeyguardStatusViewController);
        when(mKeyguardStatusBarViewComponentFactory.build(any(), any()))
                .thenReturn(mKeyguardStatusBarViewComponent);
        when(mKeyguardStatusBarViewComponent.getKeyguardStatusBarViewController())
                .thenReturn(mKeyguardStatusBarViewController);
        when(mLayoutInflater.inflate(eq(R.layout.keyguard_status_view), any(), anyBoolean()))
                .thenReturn(keyguardStatusView);
        when(mLayoutInflater.inflate(eq(R.layout.keyguard_user_switcher), any(), anyBoolean()))
                .thenReturn(mUserSwitcherView);
        when(mLayoutInflater.inflate(eq(R.layout.keyguard_bottom_area), any(), anyBoolean()))
                .thenReturn(mKeyguardBottomArea);
        when(mNotificationRemoteInputManager.isRemoteInputActive())
                .thenReturn(false);
        when(mInteractionJankMonitor.begin(any(), anyInt()))
                .thenReturn(true);
        when(mInteractionJankMonitor.end(anyInt()))
                .thenReturn(true);
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(mNotificationShadeWindowController).batchApplyWindowLayoutParams(any());
        doAnswer(invocation -> {
            mLayoutChangeListener = invocation.getArgument(0);
            return null;
        }).when(mView).addOnLayoutChangeListener(any());

        when(mView.getViewTreeObserver()).thenReturn(mViewTreeObserver);
        when(mView.getParent()).thenReturn(mViewParent);
        when(mQs.getHeader()).thenReturn(mQsHeader);
        when(mDownMotionEvent.getAction()).thenReturn(MotionEvent.ACTION_DOWN);
        when(mSysUiState.setFlag(anyInt(), anyBoolean())).thenReturn(mSysUiState);

        mMainHandler = new Handler(Looper.getMainLooper());

        when(mView.requireViewById(R.id.keyguard_long_press))
                .thenReturn(mock(LongPressHandlingView.class));

        mNotificationPanelViewController = new NotificationPanelViewController(
                mView,
                mMainHandler,
                mLayoutInflater,
                mFeatureFlags,
                coordinator, expansionHandler, mDynamicPrivacyController, mKeyguardBypassController,
                mFalsingManager, new FalsingCollectorFake(),
                mKeyguardStateController,
                mStatusBarStateController,
                mStatusBarWindowStateController,
                mNotificationShadeWindowController,
                mDozeLog, mDozeParameters, mCommandQueue, mVibratorHelper,
                mLatencyTracker, mPowerManager, mAccessibilityManager, 0, mUpdateMonitor,
                mMetricsLogger,
                mShadeLog,
                mConfigurationController,
                () -> flingAnimationUtilsBuilder, mStatusBarTouchableRegionManager,
                mConversationNotificationManager, mMediaHierarchyManager,
                mStatusBarKeyguardViewManager,
                mGutsManager,
                mNotificationsQSContainerController,
                mNotificationStackScrollLayoutController,
                mKeyguardStatusViewComponentFactory,
                mKeyguardQsUserSwitchComponentFactory,
                mKeyguardUserSwitcherComponentFactory,
                mKeyguardStatusBarViewComponentFactory,
                mLockscreenShadeTransitionController,
                mAuthController,
                mScrimController,
                mUserManager,
                mMediaDataManager,
                mNotificationShadeDepthController,
                mAmbientState,
                mLockIconViewController,
                mKeyguardMediaController,
                mTapAgainViewController,
                mNavigationModeController,
                mNavigationBarController,
                mQsController,
                mFragmentService,
                mStatusBarService,
                mContentResolver,
                mShadeHeaderController,
                mScreenOffAnimationController,
                mLockscreenGestureLogger,
                mShadeExpansionStateManager,
                mShadeRepository,
                mSysUIUnfoldComponent,
                mSysUiState,
                () -> mKeyguardBottomAreaViewController,
                mKeyguardUnlockAnimationController,
                mKeyguardIndicationController,
                mNotificationListContainer,
                mNotificationStackSizeCalculator,
                mUnlockedScreenOffAnimationController,
                mShadeTransitionController,
                systemClock,
                mKeyguardBottomAreaViewModel,
                mKeyguardBottomAreaInteractor,
                mAlternateBouncerInteractor,
                mDreamingToLockscreenTransitionViewModel,
                mOccludedToLockscreenTransitionViewModel,
                mLockscreenToDreamingTransitionViewModel,
                mGoneToDreamingTransitionViewModel,
                mGoneToDreamingLockscreenHostedTransitionViewModel,
                mLockscreenToOccludedTransitionViewModel,
                mPrimaryBouncerToGoneTransitionViewModel,
                mMainDispatcher,
                mKeyguardTransitionInteractor,
                mDumpManager,
                mKeyuardLongPressViewModel,
                mKeyguardInteractor,
                mActivityStarter,
                mSharedNotificationContainerInteractor,
                mKeyguardViewConfigurator,
                mKeyguardFaceAuthInteractor,
                new ResourcesSplitShadeStateController(),
                mPowerInteractor,
                mKeyguardClockPositionAlgorithm);
        mNotificationPanelViewController.initDependencies(
                mCentralSurfaces,
                null,
                () -> {},
                mNotificationShelfController,
                mHeadsUpManager);
        mNotificationPanelViewController.setTrackingStartedListener(() -> {});
        mNotificationPanelViewController.setOpenCloseListener(
                new OpenCloseListener() {
                    @Override
                    public void onClosingFinished() {}

                    @Override
                    public void onOpenStarted() {}
                });
        ArgumentCaptor<View.OnAttachStateChangeListener> onAttachStateChangeListenerArgumentCaptor =
                ArgumentCaptor.forClass(View.OnAttachStateChangeListener.class);
        verify(mView, atLeast(1)).addOnAttachStateChangeListener(
                onAttachStateChangeListenerArgumentCaptor.capture());
        mOnAttachStateChangeListeners = onAttachStateChangeListenerArgumentCaptor.getAllValues();

        ArgumentCaptor<View.AccessibilityDelegate> accessibilityDelegateArgumentCaptor =
                ArgumentCaptor.forClass(View.AccessibilityDelegate.class);
        verify(mView).setAccessibilityDelegate(accessibilityDelegateArgumentCaptor.capture());
        mAccessibilityDelegate = accessibilityDelegateArgumentCaptor.getValue();
        mNotificationPanelViewController.getStatusBarStateController()
                .addCallback(mNotificationPanelViewController.getStatusBarStateListener());
        mNotificationPanelViewController.getShadeHeadsUpTracker()
                .setHeadsUpAppearanceController(mock(HeadsUpAppearanceController.class));
        verify(mNotificationStackScrollLayoutController)
                .setOnEmptySpaceClickListener(mEmptySpaceClickListenerCaptor.capture());
        verify(mKeyguardStatusViewController).displayClock(LARGE, /* animate */ true);
        reset(mKeyguardStatusViewController);

        when(mNotificationPanelViewControllerLazy.get())
                .thenReturn(mNotificationPanelViewController);
        mQuickSettingsController = new QuickSettingsController(
                mNotificationPanelViewControllerLazy,
                mView,
                mQsFrameTranslateController,
                mShadeTransitionController,
                expansionHandler,
                mNotificationRemoteInputManager,
                mShadeExpansionStateManager,
                mStatusBarKeyguardViewManager,
                mLightBarController,
                mNotificationStackScrollLayoutController,
                mLockscreenShadeTransitionController,
                mNotificationShadeDepthController,
                mShadeHeaderController,
                mStatusBarTouchableRegionManager,
                mKeyguardStateController,
                mKeyguardBypassController,
                mUpdateMonitor,
                mScrimController,
                mMediaDataManager,
                mMediaHierarchyManager,
                mAmbientState,
                mRecordingController,
                mFalsingManager,
                mAccessibilityManager,
                mLockscreenGestureLogger,
                mMetricsLogger,
                mFeatureFlags,
                mInteractionJankMonitor,
                mShadeLog,
                mDumpManager,
                mKeyguardFaceAuthInteractor,
                mShadeRepository,
                mShadeInteractor,
                mJavaAdapter,
                mCastController,
                new ResourcesSplitShadeStateController()
        );
    }

    @After
    public void tearDown() {
        if (mNotificationPanelViewController != null) {
            mNotificationPanelViewController.mBottomAreaShadeAlphaAnimator.cancel();
            mNotificationPanelViewController.cancelHeightAnimator();
        }
        if (mMainHandler != null) {
            mMainHandler.removeCallbacksAndMessages(null);
        }
    }

    protected void setBottomPadding(int stackBottom, int lockIconPadding, int indicationPadding,
            int ambientPadding) {

        when(mNotificationStackScrollLayoutController.getTop()).thenReturn(0);
        when(mNotificationStackScrollLayoutController.getHeight()).thenReturn(stackBottom);
        when(mNotificationStackScrollLayoutController.getBottom()).thenReturn(stackBottom);
        when(mLockIconViewController.getTop()).thenReturn((float) (stackBottom - lockIconPadding));

        when(mResources.getDimensionPixelSize(R.dimen.keyguard_indication_bottom_padding))
                .thenReturn(indicationPadding);
        mNotificationPanelViewController.loadDimens();

        mNotificationPanelViewController.setAmbientIndicationTop(
                /* ambientIndicationTop= */ stackBottom - ambientPadding,
                /* ambientTextVisible= */ true);
    }

    protected void triggerPositionClockAndNotifications() {
        mNotificationPanelViewController.onQsSetExpansionHeightCalled(false);
    }

    protected FalsingManager.FalsingTapListener getFalsingTapListener() {
        for (View.OnAttachStateChangeListener listener : mOnAttachStateChangeListeners) {
            listener.onViewAttachedToWindow(mView);
        }
        assertThat(mFalsingManager.getTapListeners().size()).isEqualTo(1);
        return mFalsingManager.getTapListeners().get(0);
    }

    protected void givenViewAttached() {
        for (View.OnAttachStateChangeListener listener : mOnAttachStateChangeListeners) {
            listener.onViewAttachedToWindow(mView);
        }
    }

    protected ConstraintSet.Layout getConstraintSetLayout(@IdRes int id) {
        ConstraintSet constraintSet = new ConstraintSet();
        constraintSet.clone(mNotificationContainerParent);
        return constraintSet.getConstraint(id).layout;
    }

    protected void enableSplitShade(boolean enabled) {
        when(mResources.getBoolean(R.bool.config_use_split_notification_shade)).thenReturn(enabled);
        mNotificationPanelViewController.updateResources();
    }

    protected void updateMultiUserSetting(boolean enabled) {
        when(mResources.getBoolean(R.bool.qs_show_user_switcher_for_single_user)).thenReturn(false);
        when(mUserManager.isUserSwitcherEnabled(false)).thenReturn(enabled);
        final ArgumentCaptor<ContentObserver> observerCaptor =
                ArgumentCaptor.forClass(ContentObserver.class);
        verify(mContentResolver)
                .registerContentObserver(any(), anyBoolean(), observerCaptor.capture());
        observerCaptor.getValue().onChange(/* selfChange */ false);
    }

    protected void updateSmallestScreenWidth(int smallestScreenWidthDp) {
        Configuration configuration = new Configuration();
        configuration.smallestScreenWidthDp = smallestScreenWidthDp;
        mConfigurationController.onConfigurationChanged(configuration);
    }

    protected void onTouchEvent(MotionEvent ev) {
        mTouchHandler.onTouch(mView, ev);
    }

    protected void setDozing(boolean dozing, boolean dozingAlwaysOn) {
        when(mDozeParameters.getAlwaysOn()).thenReturn(dozingAlwaysOn);
        mNotificationPanelViewController.setDozing(
                /* dozing= */ dozing,
                /* animate= */ false
        );
    }

    protected void assertKeyguardStatusViewCentered() {
        mNotificationPanelViewController.updateResources();
        assertThat(getConstraintSetLayout(R.id.keyguard_status_view).endToEnd).isAnyOf(
                ConstraintSet.PARENT_ID, ConstraintSet.UNSET);
    }

    protected void assertKeyguardStatusViewNotCentered() {
        mNotificationPanelViewController.updateResources();
        assertThat(getConstraintSetLayout(R.id.keyguard_status_view).endToEnd).isEqualTo(
                R.id.qs_edge_guideline);
    }

    protected void setIsFullWidth(boolean fullWidth) {
        float nsslWidth = fullWidth ? PANEL_WIDTH : PANEL_WIDTH / 2f;
        when(mNotificationStackScrollLayoutController.getWidth()).thenReturn(nsslWidth);
        triggerLayoutChange();
    }

    protected void triggerLayoutChange() {
        mLayoutChangeListener.onLayoutChange(
                mView,
                /* left= */ 0,
                /* top= */ 0,
                /* right= */ 0,
                /* bottom= */ 0,
                /* oldLeft= */ 0,
                /* oldTop= */ 0,
                /* oldRight= */ 0,
                /* oldBottom= */ 0
        );
    }

    protected CoroutineDispatcher getMainDispatcher() {
        return mMainDispatcher;
    }
}

/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.systemui.tv;

import com.android.systemui.dagger.DefaultComponentBinder;
import com.android.systemui.dagger.DependencyBinder;
import com.android.systemui.dagger.DependencyProvider;
import com.android.systemui.dagger.SysUIComponent;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dagger.SystemServicesModule;
import com.android.systemui.dagger.SystemUIBinder;
import com.android.systemui.dagger.SystemUIModule;

import dagger.Subcomponent;

/**
 * Dagger Subcomponent for Core SysUI.
 */
@SysUISingleton
@Subcomponent(modules = {
        DefaultComponentBinder.class,
        DependencyProvider.class,
        DependencyBinder.class,
        SystemServicesModule.class,
        SystemUIBinder.class,
        SystemUIModule.class,
        TvSystemUIModule.class,
        TvSystemUIBinder.class})
public interface TvSysUIComponent extends SysUIComponent {

    /**
     * Builder for a SysUIComponent.
     */
    @Subcomponent.Builder
    interface Builder extends SysUIComponent.Builder {
        TvSysUIComponent build();
    }
}

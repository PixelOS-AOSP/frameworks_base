/*
 * SPDX-FileCopyrightText: 2025 Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.statusbar.pipeline.netspeed.ui.viewmodel

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.statusbar.pipeline.netspeed.domain.interactor.NetworkSpeedInteractor
import com.android.systemui.statusbar.pipeline.netspeed.ui.model.NetworkSpeedIcon
import com.android.systemui.statusbar.pipeline.netspeed.ui.model.NetworkSpeedUnit
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

interface NetworkSpeedViewModel {
    val icon: StateFlow<NetworkSpeedIcon>
}

@SysUISingleton
class NetworkSpeedViewModelImpl
@Inject
constructor(val interactor: NetworkSpeedInteractor, @Application val scope: CoroutineScope) :
    NetworkSpeedViewModel {

    override val icon =
        interactor.isEnabled
            .flatMapLatest { isEnabled ->
                if (isEnabled) {
                    interactor.speedBytes.map { speedBytes ->
                        val unit = NetworkSpeedUnit.fromBytes(speedBytes)
                        val bytes = speedBytes.toFloat() / unit.bytes
                        NetworkSpeedIcon.Enabled(bytes, unit)
                    }
                } else {
                    flowOf(NetworkSpeedIcon.Disabled)
                }
            }
            .distinctUntilChanged()
            .stateIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(),
                initialValue = NetworkSpeedIcon.Disabled,
            )

    companion object {
        private const val TAG = "NetworkSpeedViewModel"
        private const val KB = 1024
        private const val MB = KB * 1024
        private const val GB = MB * 1024
    }
}

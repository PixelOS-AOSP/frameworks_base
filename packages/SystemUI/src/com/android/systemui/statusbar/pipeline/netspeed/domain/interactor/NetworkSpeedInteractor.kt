/*
 * SPDX-FileCopyrightText: 2025 Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.statusbar.pipeline.netspeed.domain.interactor

import android.util.Log
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.power.data.repository.PowerRepository
import com.android.systemui.statusbar.pipeline.netspeed.data.repository.NetworkSpeedRepository
import com.android.systemui.statusbar.pipeline.shared.data.repository.ConnectivityRepository
import com.android.systemui.util.kotlin.throttle
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

interface NetworkSpeedInteractor {
    val isEnabled: StateFlow<Boolean>
    val speedBytes: StateFlow<Long>
}

@SysUISingleton
class NetworkSpeedInteractorImpl
@Inject
constructor(
    repository: NetworkSpeedRepository,
    connectivityRepository: ConnectivityRepository,
    powerRepository: PowerRepository,
    @Application scope: CoroutineScope,
) : NetworkSpeedInteractor {

    override val isEnabled =
        combine(
                repository.isSettingEnabled,
                connectivityRepository.defaultConnections,
                powerRepository.isInteractive,
            ) { isSettingEnabled, defaultConnections, isInteractive ->
                val hasConnection = defaultConnections.run { wifi.isDefault || mobile.isDefault }
                dlog(
                    "isSettingEnabled=$isSettingEnabled hasConnection=$hasConnection " +
                        "isInteractive=$isInteractive"
                )
                isSettingEnabled && isInteractive && hasConnection
            }
            .distinctUntilChanged()
            .stateIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(),
                initialValue = false,
            )

    override val speedBytes =
        flow {
                coroutineScope {
                    var prevBytes = 0L
                    var firstRun = true

                    repository.totalBytes.collect { totalBytes ->
                        val delta = totalBytes - prevBytes
                        prevBytes = totalBytes
                        dlog("speed delta=$delta")

                        if (firstRun) {
                            firstRun = false
                            emit(0L)
                        } else {
                            emit(delta)
                        }
                    }
                }
            }
            .throttle(1500L) // refresh icon only every 1.5 sec
            .stateIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(),
                initialValue = 0L
            )

    companion object {
        private const val TAG = "NetworkSpeedInteractor"
        private val DEBUG = Log.isLoggable(TAG, Log.DEBUG)

        private inline fun dlog(msg: String) {
            if (DEBUG) Log.d(TAG, msg)
        }
    }
}

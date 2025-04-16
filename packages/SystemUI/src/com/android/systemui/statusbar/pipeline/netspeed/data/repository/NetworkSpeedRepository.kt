/*
 * SPDX-FileCopyrightText: 2025 Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.statusbar.pipeline.netspeed.data.repository

import android.net.TrafficStats
import android.util.Log
import com.android.systemui.common.coroutine.ConflatedCallbackFlow.conflatedCallbackFlow
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.tuner.TunerService
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

interface NetworkSpeedRepository {
    val isSettingEnabled: Flow<Boolean>
    val totalBytes: Flow<Long>
}

@SysUISingleton
class NetworkSpeedRepositoryImpl
@Inject
constructor(
    tunerService: TunerService,
    @Application scope: CoroutineScope,
) : NetworkSpeedRepository {

    override val isSettingEnabled =
        conflatedCallbackFlow {
                val callback =
                    object : TunerService.Tunable {
                        override fun onTuningChanged(key: String, newValue: String?) {
                            if (key != KEY_NETWORK_SPEED) return
                            val enabled = TunerService.parseIntegerSwitch(newValue, false)
                            dlog("onTuningChanged: enabled=$enabled")
                            trySend(enabled)
                        }
                    }

                tunerService.addTunable(callback, KEY_NETWORK_SPEED)

                awaitClose { tunerService.removeTunable(callback) }
            }
            .distinctUntilChanged()

    override val totalBytes = flow {
        while (true) {
            val total = TrafficStats.getTotalRxBytes() + TrafficStats.getTotalTxBytes()
            dlog("totalBytes=$total")
            emit(total)
            delay(1000L)
        }
    }

    companion object {
        private const val TAG = "NetworkSpeedRepository"
        private val DEBUG = Log.isLoggable(TAG, Log.DEBUG)
        private const val KEY_NETWORK_SPEED = "status_bar_network_speed"

        private inline fun dlog(msg: String) {
            if (DEBUG) Log.d(TAG, msg)
        }
    }
}

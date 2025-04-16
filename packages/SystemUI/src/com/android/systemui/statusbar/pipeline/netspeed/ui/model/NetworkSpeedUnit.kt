/*
 * SPDX-FileCopyrightText: 2025 Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.statusbar.pipeline.netspeed.ui.model

sealed class NetworkSpeedUnit(val label: String, val bytes: Int) {
    data object Kbps : NetworkSpeedUnit("KB/s", KB)

    data object Mbps : NetworkSpeedUnit("MB/s", MB)

    data object Gbps : NetworkSpeedUnit("GB/s", GB)

    companion object {
        private const val KB = 1024
        private const val MB = KB * 1024
        private const val GB = MB * 1024

        fun fromBytes(bytes: Long) =
            when {
                bytes < MB -> Kbps
                bytes < GB -> Mbps
                else -> Gbps
            }
    }
}

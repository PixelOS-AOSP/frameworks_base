/*
 * SPDX-FileCopyrightText: 2025 Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.statusbar.pipeline.netspeed.ui.model

import android.icu.text.DecimalFormat

sealed interface NetworkSpeedIcon {

    data object Disabled : NetworkSpeedIcon

    data class Enabled(val speed: Float, val unit: NetworkSpeedUnit) : NetworkSpeedIcon {

        fun formatSpeed() =
            DecimalFormat(
                    when {
                        speed < 10f -> "#.##"
                        speed < 100f -> "##.#"
                        else -> "###"
                    }
                )
                .format(speed)
    }
}

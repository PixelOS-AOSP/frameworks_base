/*
 * SPDX-FileCopyrightText: 2025 Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.statusbar.pipeline.netspeed.ui.binder

import android.annotation.ColorInt
import android.content.res.ColorStateList
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.android.systemui.lifecycle.repeatWhenAttached
import com.android.systemui.res.R
import com.android.systemui.statusbar.pipeline.netspeed.ui.model.NetworkSpeedIcon
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

const val TAG = "NetworkSpeedViewBinder"

object NetworkSpeedViewBinder {
    @JvmStatic
    fun bind(view: ViewGroup, icon: Flow<NetworkSpeedIcon>, @ColorInt iconTint: Flow<Int>) =
        view.run {
            val containerView = requireViewById<LinearLayout>(R.id.network_speed)
            val speedTextView = requireViewById<TextView>(R.id.network_speed_text)
            val unitTextView = requireViewById<TextView>(R.id.network_speed_unit_text)

            isVisible = false

            // Use a reasonable fixed width to prevent statusbar items shifting on each refresh.
            updateLayoutParams<LinearLayout.LayoutParams> {
                width = speedTextView.paint.measureText("00.00").roundToInt()
            }

            repeatWhenAttached {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    // Log.d(TAG, "Lifecycle.State.STARTED")
                    icon
                        .onEach { icon ->
                            when (icon) {
                                is NetworkSpeedIcon.Enabled -> {
                                    isVisible = true
                                    speedTextView.text = icon.formatSpeed()
                                    unitTextView.text = icon.unit.label
                                }
                                is NetworkSpeedIcon.Disabled -> {
                                    isVisible = false
                                }
                            }
                        }
                        .launchIn(this)

                    iconTint
                        .onEach { tint ->
                            val tintList = ColorStateList.valueOf(tint)
                            speedTextView.setTextColor(tintList)
                            unitTextView.setTextColor(tintList)
                        }
                        .launchIn(this)
                }
            }
        }
}

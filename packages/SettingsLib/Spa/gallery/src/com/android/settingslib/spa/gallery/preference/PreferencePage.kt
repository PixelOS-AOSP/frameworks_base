/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.settingslib.spa.gallery.preference

import android.os.Bundle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DisabledByDefault
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import com.android.settingslib.spa.framework.common.SettingsEntry
import com.android.settingslib.spa.framework.common.SettingsEntryBuilder
import com.android.settingslib.spa.framework.common.SettingsPage
import com.android.settingslib.spa.framework.common.SettingsPageProvider
import com.android.settingslib.spa.framework.compose.navigator
import com.android.settingslib.spa.framework.compose.toState
import com.android.settingslib.spa.framework.theme.SettingsTheme
import com.android.settingslib.spa.widget.preference.Preference
import com.android.settingslib.spa.widget.preference.PreferenceModel
import com.android.settingslib.spa.widget.scaffold.RegularScaffold
import com.android.settingslib.spa.widget.ui.SettingsIcon
import kotlinx.coroutines.delay

private const val TITLE = "Sample Preference"

object PreferencePageProvider : SettingsPageProvider {
    override val name = "Preference"

    override fun buildEntry(arguments: Bundle?): List<SettingsEntry> {
        val owner = SettingsPage.create(name)
        val entryList = mutableListOf<SettingsEntry>()
        entryList.add(
            SettingsEntryBuilder.create("Preference", owner)
                .setIsAllowSearch(true)
                .setUiLayoutFn {
                    Preference(object : PreferenceModel {
                        override val title = "Preference"
                    })
                }.build()
        )
        entryList.add(
            SettingsEntryBuilder.create("Preference with summary", owner)
                .setIsAllowSearch(true)
                .setUiLayoutFn {
                    Preference(object : PreferenceModel {
                        override val title = "Preference"
                        override val summary = "With summary".toState()
                    })
                }.build()
        )
        entryList.add(
            SettingsEntryBuilder.create("Preference with async summary", owner)
                .setIsAllowSearch(true)
                .setUiLayoutFn {
                    Preference(object : PreferenceModel {
                        override val title = "Preference"
                        override val summary = produceState(initialValue = " ") {
                            delay(1000L)
                            value = "Async summary"
                        }
                    })
                }.build()
        )
        entryList.add(
            SettingsEntryBuilder.create("Click me", owner)
                .setIsAllowSearch(true)
                .setUiLayoutFn {
                    var count by rememberSaveable { mutableStateOf(0) }
                    Preference(object : PreferenceModel {
                        override val title = "Click me"
                        override val summary = derivedStateOf { count.toString() }
                        override val onClick: (() -> Unit) = { count++ }
                        override val icon = @Composable {
                            SettingsIcon(imageVector = Icons.Outlined.TouchApp)
                        }
                    })
                }.build()
        )
        entryList.add(
            SettingsEntryBuilder.create("Ticker", owner)
                .setIsAllowSearch(true)
                .setUiLayoutFn {
                    var ticks by rememberSaveable { mutableStateOf(0) }
                    LaunchedEffect(ticks) {
                        delay(1000L)
                        ticks++
                    }
                    Preference(object : PreferenceModel {
                        override val title = "Ticker"
                        override val summary = derivedStateOf { ticks.toString() }
                    })
                }.build()
        )
        entryList.add(
            SettingsEntryBuilder.create("Disabled", owner)
                .setIsAllowSearch(true)
                .setUiLayoutFn {
                    Preference(object : PreferenceModel {
                        override val title = "Disabled"
                        override val summary = "Disabled".toState()
                        override val enabled = false.toState()
                        override val icon = @Composable {
                            SettingsIcon(imageVector = Icons.Outlined.DisabledByDefault)
                        }
                    })
                }.build()
        )

        return entryList
    }

    fun buildInjectEntry(): SettingsEntryBuilder {
        return SettingsEntryBuilder.createInject(owner = SettingsPage.create(name))
            .setIsAllowSearch(true)
            .setUiLayoutFn {
                Preference(object : PreferenceModel {
                    override val title = TITLE
                    override val onClick = navigator(name)
                })
            }
    }

    @Composable
    override fun Page(arguments: Bundle?) {
        RegularScaffold(title = TITLE) {
            for (entry in buildEntry(arguments)) {
                entry.UiLayout()
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PreferencePagePreview() {
    SettingsTheme {
        PreferencePageProvider.Page(null)
    }
}

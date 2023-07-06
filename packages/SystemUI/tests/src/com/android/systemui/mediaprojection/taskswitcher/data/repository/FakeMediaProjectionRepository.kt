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

package com.android.systemui.mediaprojection.taskswitcher.data.repository

import android.app.TaskInfo
import com.android.systemui.mediaprojection.taskswitcher.data.model.MediaProjectionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeMediaProjectionRepository : MediaProjectionRepository {

    private val state = MutableStateFlow<MediaProjectionState>(MediaProjectionState.NotProjecting)

    fun switchProjectedTask(newTask: TaskInfo) {
        state.value = MediaProjectionState.SingleTask(newTask)
    }

    override val mediaProjectionState: Flow<MediaProjectionState> = state.asStateFlow()

    fun projectEntireScreen() {
        state.value = MediaProjectionState.EntireScreen
    }

    fun stopProjecting() {
        state.value = MediaProjectionState.NotProjecting
    }
}

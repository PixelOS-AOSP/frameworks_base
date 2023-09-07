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
 *
 */

package com.android.systemui.keyguard.domain.interactor

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.keyguard.data.repository.KeyguardBlueprintRepository
import javax.inject.Inject

@SysUISingleton
class KeyguardBlueprintInteractor
@Inject
constructor(private val keyguardBlueprintRepository: KeyguardBlueprintRepository) {
    val blueprint = keyguardBlueprintRepository.blueprint

    /**
     * Transitions to a blueprint.
     *
     * @param blueprintId
     * @return whether the transition has succeeded.
     */
    fun transitionToBlueprint(blueprintId: String): Boolean {
        return keyguardBlueprintRepository.applyBlueprint(blueprintId)
    }

    /**
     * Transitions to a blueprint.
     *
     * @param blueprintId
     * @return whether the transition has succeeded.
     */
    fun transitionToBlueprint(blueprintId: Int): Boolean {
        return keyguardBlueprintRepository.applyBlueprint(blueprintId)
    }

    /** Re-emits the blueprint value to the collectors. */
    fun refreshBlueprint() {
        keyguardBlueprintRepository.refreshBlueprint()
    }
}

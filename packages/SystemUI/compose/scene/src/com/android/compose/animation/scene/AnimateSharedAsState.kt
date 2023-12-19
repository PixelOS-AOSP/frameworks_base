/*
 * Copyright 2023 The Android Open Source Project
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

package com.android.compose.animation.scene

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.lerp
import com.android.compose.ui.util.lerp

/**
 * Animate a scene Int value.
 *
 * @see SceneScope.animateSceneValueAsState
 */
@Composable
fun SceneScope.animateSceneIntAsState(
    value: Int,
    key: ValueKey,
    canOverflow: Boolean = true,
): State<Int> {
    return animateSceneValueAsState(value, key, ::lerp, canOverflow)
}

/**
 * Animate a shared element Int value.
 *
 * @see ElementScope.animateElementValueAsState
 */
@Composable
fun ElementScope<*>.animateElementIntAsState(
    value: Int,
    key: ValueKey,
    canOverflow: Boolean = true,
): State<Int> {
    return animateElementValueAsState(value, key, ::lerp, canOverflow)
}

/**
 * Animate a scene Float value.
 *
 * @see SceneScope.animateSceneValueAsState
 */
@Composable
fun SceneScope.animateSceneFloatAsState(
    value: Float,
    key: ValueKey,
    canOverflow: Boolean = true,
): State<Float> {
    return animateSceneValueAsState(value, key, ::lerp, canOverflow)
}

/**
 * Animate a shared element Float value.
 *
 * @see ElementScope.animateElementValueAsState
 */
@Composable
fun ElementScope<*>.animateElementFloatAsState(
    value: Float,
    key: ValueKey,
    canOverflow: Boolean = true,
): State<Float> {
    return animateElementValueAsState(value, key, ::lerp, canOverflow)
}

/**
 * Animate a scene Dp value.
 *
 * @see SceneScope.animateSceneValueAsState
 */
@Composable
fun SceneScope.animateSceneDpAsState(
    value: Dp,
    key: ValueKey,
    canOverflow: Boolean = true,
): State<Dp> {
    return animateSceneValueAsState(value, key, ::lerp, canOverflow)
}

/**
 * Animate a shared element Dp value.
 *
 * @see ElementScope.animateElementValueAsState
 */
@Composable
fun ElementScope<*>.animateElementDpAsState(
    value: Dp,
    key: ValueKey,
    canOverflow: Boolean = true,
): State<Dp> {
    return animateElementValueAsState(value, key, ::lerp, canOverflow)
}

/**
 * Animate a scene Color value.
 *
 * @see SceneScope.animateSceneValueAsState
 */
@Composable
fun SceneScope.animateSceneColorAsState(
    value: Color,
    key: ValueKey,
): State<Color> {
    return animateSceneValueAsState(value, key, ::lerp, canOverflow = false)
}

/**
 * Animate a shared element Color value.
 *
 * @see ElementScope.animateElementValueAsState
 */
@Composable
fun ElementScope<*>.animateElementColorAsState(
    value: Color,
    key: ValueKey,
): State<Color> {
    return animateElementValueAsState(value, key, ::lerp, canOverflow = false)
}

@Composable
internal fun <T> animateSharedValueAsState(
    layoutImpl: SceneTransitionLayoutImpl,
    scene: Scene,
    element: Element?,
    key: ValueKey,
    value: T,
    lerp: (T, T, Float) -> T,
    canOverflow: Boolean,
): State<T> {
    val sharedValue =
        Snapshot.withoutReadObservation {
            val sharedValues =
                element?.sceneValues?.getValue(scene.key)?.sharedValues ?: scene.sharedValues
            sharedValues.getOrPut(key) { Element.SharedValue(key, value) } as Element.SharedValue<T>
        }

    if (value != sharedValue.value) {
        sharedValue.value = value
    }

    return remember(layoutImpl, element, sharedValue, lerp, canOverflow) {
        derivedStateOf { computeValue(layoutImpl, element, sharedValue, lerp, canOverflow) }
    }
}

private fun <T> computeValue(
    layoutImpl: SceneTransitionLayoutImpl,
    element: Element?,
    sharedValue: Element.SharedValue<T>,
    lerp: (T, T, Float) -> T,
    canOverflow: Boolean,
): T {
    val transition = layoutImpl.state.currentTransition
    if (transition == null || !layoutImpl.isTransitionReady(transition)) {
        return sharedValue.value
    }

    fun sceneValue(scene: SceneKey): Element.SharedValue<T>? {
        val sharedValues =
            if (element == null) {
                layoutImpl.scene(scene).sharedValues
            } else {
                element.sceneValues[scene]?.sharedValues
            }
                ?: return null
        val value = sharedValues[sharedValue.key] ?: return null
        return value as Element.SharedValue<T>
    }

    val fromValue = sceneValue(transition.fromScene)
    val toValue = sceneValue(transition.toScene)
    return if (fromValue != null && toValue != null) {
        val progress =
            if (canOverflow) transition.progress else transition.progress.coerceIn(0f, 1f)
        lerp(fromValue.value, toValue.value, progress)
    } else if (fromValue != null) {
        fromValue.value
    } else if (toValue != null) {
        toValue.value
    } else {
        sharedValue.value
    }
}

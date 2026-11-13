package com.retrocam.capture

import com.retrocam.filmengine.FilmCameraEffect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * "Is there a bright highlight in the preview right now" (drives FlareMode.AUTO).
 *
 * TODO(merge): the contract has `FilmCameraEffect.highlight: StateFlow<Highlight?>`, which this
 * worktree's :filmengine stub lacks. After merging replace the body with:
 *     return highlight.map { it != null }.stateIn(scope, SharingStarted.Eagerly, false)
 * (imports kotlinx.coroutines.flow.map / stateIn / SharingStarted). Until then it is always false.
 */
@Suppress("UnusedReceiverParameter", "unused")
fun FilmCameraEffect.hasHighlight(scope: CoroutineScope): StateFlow<Boolean> = MutableStateFlow(false)

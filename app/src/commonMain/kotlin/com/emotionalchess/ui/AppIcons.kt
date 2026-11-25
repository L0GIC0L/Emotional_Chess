/*
 * Copyright 2024 The Android Open Source Project
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

package com.emotionalchess.ui

import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The five Material icons the app uses beyond the core set, copied from
 * material-icons-extended so the apps don't carry the whole 37 MB library.
 */
object AppIcons {
    val Undo: ImageVector by lazy {
        materialIcon(name = "AutoMirrored.Filled.Undo", autoMirror = true) {
            materialPath {
                moveTo(12.5f, 8.0f)
                curveToRelative(-2.65f, 0.0f, -5.05f, 0.99f, -6.9f, 2.6f)
                lineTo(2.0f, 7.0f)
                verticalLineToRelative(9.0f)
                horizontalLineToRelative(9.0f)
                lineToRelative(-3.62f, -3.62f)
                curveToRelative(1.39f, -1.16f, 3.16f, -1.88f, 5.12f, -1.88f)
                curveToRelative(3.54f, 0.0f, 6.55f, 2.31f, 7.6f, 5.5f)
                lineToRelative(2.37f, -0.78f)
                curveTo(21.08f, 11.03f, 17.15f, 8.0f, 12.5f, 8.0f)
                close()
            }
        }
    }

    val MenuBook: ImageVector by lazy {
        materialIcon(name = "AutoMirrored.Filled.MenuBook", autoMirror = true) {
            materialPath {
                moveTo(21.0f, 5.0f)
                curveToRelative(-1.11f, -0.35f, -2.33f, -0.5f, -3.5f, -0.5f)
                curveToRelative(-1.95f, 0.0f, -4.05f, 0.4f, -5.5f, 1.5f)
                curveToRelative(-1.45f, -1.1f, -3.55f, -1.5f, -5.5f, -1.5f)
                reflectiveCurveTo(2.45f, 4.9f, 1.0f, 6.0f)
                verticalLineToRelative(14.65f)
                curveToRelative(0.0f, 0.25f, 0.25f, 0.5f, 0.5f, 0.5f)
                curveToRelative(0.1f, 0.0f, 0.15f, -0.05f, 0.25f, -0.05f)
                curveTo(3.1f, 20.45f, 5.05f, 20.0f, 6.5f, 20.0f)
                curveToRelative(1.95f, 0.0f, 4.05f, 0.4f, 5.5f, 1.5f)
                curveToRelative(1.35f, -0.85f, 3.8f, -1.5f, 5.5f, -1.5f)
                curveToRelative(1.65f, 0.0f, 3.35f, 0.3f, 4.75f, 1.05f)
                curveToRelative(0.1f, 0.05f, 0.15f, 0.05f, 0.25f, 0.05f)
                curveToRelative(0.25f, 0.0f, 0.5f, -0.25f, 0.5f, -0.5f)
                verticalLineTo(6.0f)
                curveTo(22.4f, 5.55f, 21.75f, 5.25f, 21.0f, 5.0f)
                close()
                moveTo(21.0f, 18.5f)
                curveToRelative(-1.1f, -0.35f, -2.3f, -0.5f, -3.5f, -0.5f)
                curveToRelative(-1.7f, 0.0f, -4.15f, 0.65f, -5.5f, 1.5f)
                verticalLineTo(8.0f)
                curveToRelative(1.35f, -0.85f, 3.8f, -1.5f, 5.5f, -1.5f)
                curveToRelative(1.2f, 0.0f, 2.4f, 0.15f, 3.5f, 0.5f)
                verticalLineTo(18.5f)
                close()
            }
            materialPath {
                moveTo(17.5f, 10.5f)
                curveToRelative(0.88f, 0.0f, 1.73f, 0.09f, 2.5f, 0.26f)
                verticalLineTo(9.24f)
                curveTo(19.21f, 9.09f, 18.36f, 9.0f, 17.5f, 9.0f)
                curveToRelative(-1.7f, 0.0f, -3.24f, 0.29f, -4.5f, 0.83f)
                verticalLineToRelative(1.66f)
                curveTo(14.13f, 10.85f, 15.7f, 10.5f, 17.5f, 10.5f)
                close()
            }
            materialPath {
                moveTo(13.0f, 12.49f)
                verticalLineToRelative(1.66f)
                curveToRelative(1.13f, -0.64f, 2.7f, -0.99f, 4.5f, -0.99f)
                curveToRelative(0.88f, 0.0f, 1.73f, 0.09f, 2.5f, 0.26f)
                verticalLineTo(11.9f)
                curveToRelative(-0.79f, -0.15f, -1.64f, -0.24f, -2.5f, -0.24f)
                curveTo(15.8f, 11.66f, 14.26f, 11.96f, 13.0f, 12.49f)
                close()
            }
            materialPath {
                moveTo(17.5f, 14.33f)
                curveToRelative(-1.7f, 0.0f, -3.24f, 0.29f, -4.5f, 0.83f)
                verticalLineToRelative(1.66f)
                curveToRelative(1.13f, -0.64f, 2.7f, -0.99f, 4.5f, -0.99f)
                curveToRelative(0.88f, 0.0f, 1.73f, 0.09f, 2.5f, 0.26f)
                verticalLineToRelative(-1.52f)
                curveTo(19.21f, 14.41f, 18.36f, 14.33f, 17.5f, 14.33f)
                close()
            }
        }
    }

    val SwapVert: ImageVector by lazy {
        materialIcon(name = "Filled.SwapVert") {
            materialPath {
                moveTo(16.0f, 17.01f)
                verticalLineTo(10.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(7.01f)
                horizontalLineToRelative(-3.0f)
                lineTo(15.0f, 21.0f)
                lineToRelative(4.0f, -3.99f)
                horizontalLineToRelative(-3.0f)
                close()
                moveTo(9.0f, 3.0f)
                lineTo(5.0f, 6.99f)
                horizontalLineToRelative(3.0f)
                verticalLineTo(14.0f)
                horizontalLineToRelative(2.0f)
                verticalLineTo(6.99f)
                horizontalLineToRelative(3.0f)
                lineTo(9.0f, 3.0f)
                close()
            }
        }
    }

    val Flag: ImageVector by lazy {
        materialIcon(name = "Filled.Flag") {
            materialPath {
                moveTo(14.4f, 6.0f)
                lineTo(14.0f, 4.0f)
                horizontalLineTo(5.0f)
                verticalLineToRelative(17.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(-7.0f)
                horizontalLineToRelative(5.6f)
                lineToRelative(0.4f, 2.0f)
                horizontalLineToRelative(7.0f)
                verticalLineTo(6.0f)
                close()
            }
        }
    }

    val Lightbulb: ImageVector by lazy {
        materialIcon(name = "Filled.Lightbulb") {
            materialPath {
                moveTo(9.0f, 21.0f)
                curveToRelative(0.0f, 0.5f, 0.4f, 1.0f, 1.0f, 1.0f)
                horizontalLineToRelative(4.0f)
                curveToRelative(0.6f, 0.0f, 1.0f, -0.5f, 1.0f, -1.0f)
                verticalLineToRelative(-1.0f)
                lineTo(9.0f, 20.0f)
                verticalLineToRelative(1.0f)
                close()
                moveTo(12.0f, 2.0f)
                curveTo(8.1f, 2.0f, 5.0f, 5.1f, 5.0f, 9.0f)
                curveToRelative(0.0f, 2.4f, 1.2f, 4.5f, 3.0f, 5.7f)
                lineTo(8.0f, 17.0f)
                curveToRelative(0.0f, 0.5f, 0.4f, 1.0f, 1.0f, 1.0f)
                horizontalLineToRelative(6.0f)
                curveToRelative(0.6f, 0.0f, 1.0f, -0.5f, 1.0f, -1.0f)
                verticalLineToRelative(-2.3f)
                curveToRelative(1.8f, -1.3f, 3.0f, -3.4f, 3.0f, -5.7f)
                curveToRelative(0.0f, -3.9f, -3.1f, -7.0f, -7.0f, -7.0f)
                close()
            }
        }
    }
}

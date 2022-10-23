/*
 * Copyright © Wynntils 2022.
 * This file is released under AGPLv3. See LICENSE for full license details.
 */
package com.wynntils.utils;

import com.wynntils.mc.utils.McUtils;
import org.lwjgl.glfw.GLFW;

public final class KeyboardUtils {
    public static boolean isKeyDown(int keyCode) {
        return GLFW.glfwGetKey(McUtils.mc().getWindow().getWindow(), keyCode) == 1;
    }

    public static boolean isShiftDown() {
        return isKeyDown(GLFW.GLFW_KEY_RIGHT_SHIFT) || isKeyDown(GLFW.GLFW_KEY_LEFT_SHIFT);
    }
}

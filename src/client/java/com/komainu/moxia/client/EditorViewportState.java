package com.komainu.moxia.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;

public final class EditorViewportState {
    private static volatile boolean active;
    private static volatile int framebufferX;
    private static volatile int framebufferWidth;
    private static volatile int framebufferHeight;

    private EditorViewportState() {
    }

    public static void update(MinecraftClient client, int leftScaledWidth, int rightScaledWidth) {
        Window window = client.getWindow();
        double scale = window.getScaleFactor();
        int fullWidth = window.getFramebufferWidth();
        int leftPixels = Math.max(0, (int) Math.round(leftScaledWidth * scale));
        int rightPixels = Math.max(0, (int) Math.round(rightScaledWidth * scale));
        framebufferX = Math.min(leftPixels, Math.max(0, fullWidth - 1));
        framebufferWidth = Math.max(1, fullWidth - leftPixels - rightPixels);
        framebufferHeight = Math.max(1, window.getFramebufferHeight());
        active = true;
    }

    public static void clear() {
        active = false;
    }

    public static boolean active() {
        return active;
    }

    public static int x() {
        return framebufferX;
    }

    public static int width() {
        return framebufferWidth;
    }

    public static int height() {
        return framebufferHeight;
    }
}

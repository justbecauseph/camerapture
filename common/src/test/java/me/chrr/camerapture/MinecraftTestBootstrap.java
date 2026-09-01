package me.chrr.camerapture;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

public class MinecraftTestBootstrap {
    private static boolean initialized = false;

    public static synchronized void init() {
        if (!initialized) {
            SharedConstants.tryDetectVersion();
            Bootstrap.bootStrap();
            initialized = true;
        }
    }
}

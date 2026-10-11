package com.impendingdoom.core;

import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * Thread-aware view of which events are active. The server copy is authoritative; the client copy is
 * synced through {@link com.impendingdoom.network.DoomStatePayload}. Code that may run on either side
 * (such as the biome mixin) uses {@link #contextualActive(String)}.
 */
public final class ActiveEvents {
    private static volatile String serverMinor = "";
    private static volatile String serverMajor = "";
    private static volatile String clientMinor = "";
    private static volatile String clientMajor = "";

    private ActiveEvents() {}

    public static void setServer(String minor, String major) {
        serverMinor = minor == null ? "" : minor;
        serverMajor = major == null ? "" : major;
    }

    public static void setClient(String minor, String major) {
        clientMinor = minor == null ? "" : minor;
        clientMajor = major == null ? "" : major;
    }

    public static boolean serverActive(String id) {
        return id.equals(serverMinor) || id.equals(serverMajor);
    }

    public static boolean clientActive(String id) {
        return id.equals(clientMinor) || id.equals(clientMajor);
    }

    public static String clientMinor() {
        return clientMinor;
    }

    public static String clientMajor() {
        return clientMajor;
    }

    public static boolean contextualActive(String id) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null && server.isSameThread()) return serverActive(id);
        // Only the client render thread sees client state; world generation workers are never affected.
        if ("Render thread".equals(Thread.currentThread().getName())) return clientActive(id);
        return false;
    }
}

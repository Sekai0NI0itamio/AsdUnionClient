/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/SkidderMC/FDPClient/
 */
package net.asd.union.handler.payload;

import net.asd.union.handler.sessiontabs.SessionRuntimeScope;
import net.asd.union.handler.sessiontabs.TabSimulationThread;

public class ClientBrandRetriever {

    public static String getClientModName() {
        if (Thread.currentThread() instanceof TabSimulationThread) {
            return "vanilla";
        }

        if (SessionRuntimeScope.INSTANCE.isDetachedContextActive()) {
            return "vanilla";
        }

        return ClientFixes.getClientModName();
    }
}

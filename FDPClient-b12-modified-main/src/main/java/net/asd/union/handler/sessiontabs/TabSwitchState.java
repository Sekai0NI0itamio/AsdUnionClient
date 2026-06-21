package net.asd.union.handler.sessiontabs;

/**
 * Holder for deferred state clearing during tab switches.
 * When a tab switch nulls mc state mid-tick, runTick continues and NPEs.
 * Instead of nulling synchronously, we defer it to the next tick's HEAD
 * via this Runnable, which is executed by MixinMinecraft's runTick guard.
 */
public class TabSwitchState {
    public static volatile Runnable pendingClear = null;
}

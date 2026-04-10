package net.asd.union.handler.network;

import java.io.Closeable;
import java.io.InputStream;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import kotlin.Metadata;
import kotlin.Pair;
import kotlin.Result;
import kotlin.ResultKt;
import kotlin.TuplesKt;
import kotlin.Unit;
import kotlin.Result.Companion;
import kotlin.collections.CollectionsKt;
import kotlin.enums.EnumEntries;
import kotlin.enums.EnumEntriesKt;
import kotlin.io.CloseableKt;
import kotlin.jvm.functions.Function1;
import kotlin.jvm.internal.Intrinsics;
import kotlin.jvm.internal.SourceDebugExtension;
import kotlin.sequences.Sequence;
import kotlin.sequences.SequencesKt;
import kotlin.text.Charsets;
import kotlin.text.MatchResult;
import kotlin.text.Regex;
import kotlin.text.StringsKt;
import net.asd.union.event.EventHook;
import net.asd.union.event.EventManager;
import net.asd.union.event.Listenable;
import net.asd.union.event.UpdateEvent;
import net.asd.union.event.EventHook.Blocking;
import net.asd.union.file.FileConfig;
import net.asd.union.file.FileManager;
import net.asd.union.utils.client.ClientUtils;
import net.asd.union.utils.client.MinecraftInstance;
import net.asd.union.utils.client.MinecraftInstance.DefaultImpls;
import net.asd.union.utils.timing.MSTimer;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(
   mv = {2, 0, 0},
   k = 1,
   xi = 48,
   d1 = {"\u0000t\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000b\n\u0002\b\b\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0017\n\u0002\u0010\t\n\u0002\b\u0006\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\b\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u000f\b\u00c6\u0002\u0018\u00002\u00020\u00012\u00020\u0002:\u0005]^_`aB\t\b\u0002\u00a2\u0006\u0004\b\u0003\u0010\u0004J\u0006\u0010>\u001a\u00020\bJ\u0006\u0010?\u001a\u00020@J\u0006\u0010A\u001a\u00020\u0015J\u0012\u0010B\u001a\u0004\u0018\u0001042\b\u0010C\u001a\u0004\u0018\u000104J\u0006\u0010D\u001a\u00020\u0015J\u0006\u0010E\u001a\u00020\u0006J\u0006\u0010F\u001a\u00020:J\u0006\u0010G\u001a\u00020:J\b\u0010H\u001a\u00020IH\u0002J\u0010\u0010J\u001a\u00020:2\u0006\u0010K\u001a\u00020\u0015H\u0002J\b\u0010L\u001a\u00020MH\u0002J\u0018\u0010N\u001a\u00020\b2\u0006\u0010O\u001a\u00020P2\u0006\u0010Q\u001a\u00020\u0015H\u0002J\"\u0010R\u001a\u00020S2\u0006\u0010T\u001a\u00020\u00152\u0006\u0010U\u001a\u00020\u00062\b\u0010V\u001a\u0004\u0018\u000104H\u0002J\b\u0010W\u001a\u00020\u0015H\u0002J\u0010\u0010X\u001a\u00020\u00152\u0006\u0010Y\u001a\u00020\u0015H\u0002J\b\u0010Z\u001a\u00020:H\u0002J\u0010\u0010[\u001a\u00020:2\u0006\u0010\\\u001a\u00020\u0015H\u0002R\u000e\u0010\u0005\u001a\u00020\u0006X\u0086T\u00a2\u0006\u0002\n\u0000R$\u0010\t\u001a\u00020\b2\u0006\u0010\u0007\u001a\u00020\b@FX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\n\u0010\u000b\"\u0004\b\f\u0010\rR\u001a\u0010\u000e\u001a\u00020\bX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u000f\u0010\u000b\"\u0004\b\u0010\u0010\rR\u001e\u0010\u0012\u001a\u00020\u00112\u0006\u0010\u0007\u001a\u00020\u0011@BX\u0086\u000e\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0013\u0010\u0014R\u001e\u0010\u0016\u001a\u00020\u00152\u0006\u0010\u0007\u001a\u00020\u0015@BX\u0086\u000e\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0017\u0010\u0018R\u001e\u0010\u0019\u001a\u00020\u00152\u0006\u0010\u0007\u001a\u00020\u0015@BX\u0086\u000e\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001a\u0010\u0018R\u001e\u0010\u001b\u001a\u00020\b2\u0006\u0010\u0007\u001a\u00020\b@BX\u0086\u000e\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001c\u0010\u000bR\u001e\u0010\u001d\u001a\u00020\u00152\u0006\u0010\u0007\u001a\u00020\u0015@BX\u0086\u000e\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001e\u0010\u0018R\u001e\u0010\u001f\u001a\u00020\b2\u0006\u0010\u0007\u001a\u00020\b@BX\u0086\u000e\u00a2\u0006\b\n\u0000\u001a\u0004\b \u0010\u000bR\u001e\u0010!\u001a\u00020\u00152\u0006\u0010\u0007\u001a\u00020\u0015@BX\u0086\u000e\u00a2\u0006\b\n\u0000\u001a\u0004\b\"\u0010\u0018R\u001e\u0010#\u001a\u00020\u00152\u0006\u0010\u0007\u001a\u00020\u0015@BX\u0086\u000e\u00a2\u0006\b\n\u0000\u001a\u0004\b$\u0010\u0018R\u001e\u0010%\u001a\u00020\b2\u0006\u0010\u0007\u001a\u00020\b@BX\u0086\u000e\u00a2\u0006\b\n\u0000\u001a\u0004\b&\u0010\u000bR\u001e\u0010\'\u001a\u00020\u00152\u0006\u0010\u0007\u001a\u00020\u0015@BX\u0086\u000e\u00a2\u0006\b\n\u0000\u001a\u0004\b(\u0010\u0018R\u000e\u0010)\u001a\u00020\u0006X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010*\u001a\u00020\u0006X\u0082D\u00a2\u0006\u0002\n\u0000R\u001e\u0010+\u001a\u00020\b2\u0006\u0010\u0007\u001a\u00020\b@BX\u0086\u000e\u00a2\u0006\b\n\u0000\u001a\u0004\b,\u0010\u000bR\u001e\u0010.\u001a\u00020-2\u0006\u0010\u0007\u001a\u00020-@BX\u0086\u000e\u00a2\u0006\b\n\u0000\u001a\u0004\b/\u00100R\u001e\u00101\u001a\u00020\u00152\u0006\u0010\u0007\u001a\u00020\u0015@BX\u0086\u000e\u00a2\u0006\b\n\u0000\u001a\u0004\b2\u0010\u0018R\u0010\u00103\u001a\u0004\u0018\u000104X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u00105\u001a\u000206X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u00107\u001a\u00020-X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u00108\u001a\u00020-X\u0082D\u00a2\u0006\u0002\n\u0000R\u0013\u00109\u001a\u00020:\u00a2\u0006\n\n\u0002\u0010=\u001a\u0004\b;\u0010<\u00a8\u0006b"},
   d2 = {"Lnet/asd/union/handler/network/ConnectToRouter;", "Lnet/asd/union/utils/client/MinecraftInstance;", "Lnet/asd/union/event/Listenable;", "<init>", "()V", "TUNNEL_PORT", "", "value", "", "enabled", "getEnabled", "()Z", "setEnabled", "(Z)V", "debugEnabled", "getDebugEnabled", "setDebugEnabled", "Lnet/asd/union/handler/network/ConnectToRouter$Status;", "status", "getStatus", "()Lnet/asd/union/handler/network/ConnectToRouter$Status;", "", "lastError", "getLastError", "()Ljava/lang/String;", "lastLocalIp", "getLastLocalIp", "vpnDetected", "getVpnDetected", "selectedInterface", "getSelectedInterface", "tunnelAvailable", "getTunnelAvailable", "tunnelInterface", "getTunnelInterface", "tunnelIp", "getTunnelIp", "wasAutoDisabled", "getWasAutoDisabled", "autoDisableReason", "getAutoDisableReason", "consecutiveFailures", "AUTO_DISABLE_THRESHOLD", "lastTcpOk", "getLastTcpOk", "", "lastTcpMs", "getLastTcpMs", "()J", "lastTcpError", "getLastTcpError", "preferredAddress", "Ljava/net/InetAddress;", "refreshTimer", "Lnet/asd/union/utils/timing/MSTimer;", "lastUltraFastRefreshAt", "ultraRefreshDebounceMs", "onUpdate", "", "getOnUpdate", "()Lkotlin/Unit;", "Lkotlin/Unit;", "isTunnelMode", "ultraFastRefreshServerPing", "Lnet/asd/union/handler/network/ConnectToRouter$UltraFastRefreshResult;", "getActivePingIp", "getPreferredLocalAddressFor", "remoteAddress", "getStatusLine", "getStatusColor", "refreshStatus", "sendRefreshPacket", "checkTunnel", "Lnet/asd/union/handler/network/ConnectToRouter$TunnelCheckResult;", "onDetectionFailed", "reason", "findPreferredAddress", "Lnet/asd/union/handler/network/ConnectToRouter$AddressResult;", "isVpnInterface", "intf", "Ljava/net/NetworkInterface;", "nameLower", "tcpProbe", "Lnet/asd/union/handler/network/ConnectToRouter$ProbeResult;", "host", "port", "localAddress", "detectSystemOutboundIp", "normalizeIp", "ip", "dumpInterfaces", "logDebug", "message", "Status", "UltraFastRefreshResult", "TunnelCheckResult", "AddressResult", "ProbeResult", "AsdUnion"}
)
@SourceDebugExtension({"SMAP\nConnectToRouter.kt\nKotlin\n*S Kotlin\n*F\n+ 1 ConnectToRouter.kt\nnet/asd/union/handler/network/ConnectToRouter\n+ 2 fake.kt\nkotlin/jvm/internal/FakeKt\n+ 3 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n+ 4 Listenable.kt\nnet/asd/union/event/ListenableKt\n*L\n1#1,515:1\n1#2:516\n295#3,2:517\n1755#3,3:519\n295#3,2:522\n22#4,7:524\n*S KotlinDebug\n*F\n+ 1 ConnectToRouter.kt\nnet/asd/union/handler/network/ConnectToRouter\n*L\n410#1:517,2\n440#1:519,3\n479#1:522,2\n133#1:524,7\n*E\n"})
public final class ConnectToRouter implements MinecraftInstance, Listenable {
   @NotNull
   public static final ConnectToRouter INSTANCE = new ConnectToRouter();
   public static final int TUNNEL_PORT = 25560;
   private static boolean enabled;
   private static boolean debugEnabled = true;
   @NotNull
   private static ConnectToRouter.Status status = ConnectToRouter.Status.OFF;
   @NotNull
   private static String lastError = "";
   @NotNull
   private static String lastLocalIp = "";
   private static boolean vpnDetected;
   @NotNull
   private static String selectedInterface = "";
   private static boolean tunnelAvailable;
   @NotNull
   private static String tunnelInterface = "";
   @NotNull
   private static String tunnelIp = "";
   private static boolean wasAutoDisabled;
   @NotNull
   private static String autoDisableReason = "";
   private static int consecutiveFailures;
   private static final int AUTO_DISABLE_THRESHOLD = 3;
   private static boolean lastTcpOk;
   private static long lastTcpMs = -1L;
   @NotNull
   private static String lastTcpError = "";
   @Nullable
   private static InetAddress preferredAddress;
   @NotNull
   private static final MSTimer refreshTimer = new MSTimer();
   private static long lastUltraFastRefreshAt;
   private static final long ultraRefreshDebounceMs = 150L;
   @NotNull
   private static final Unit onUpdate = Unit.INSTANCE;

   public final boolean getEnabled() {
      return enabled;
   }

   public final void setEnabled(boolean value) {
      if(enabled != value) {
         enabled = value;
         if(value) {
            consecutiveFailures = 0;
            wasAutoDisabled = false;
            autoDisableReason = "";
            this.sendRefreshPacket();
            this.refreshStatus();
         } else {
            status = ConnectToRouter.Status.OFF;
            lastError = "";
            lastLocalIp = "";
            vpnDetected = false;
            selectedInterface = "";
            preferredAddress = null;
            tunnelAvailable = false;
            tunnelInterface = "";
            tunnelIp = "";
            refreshTimer.zero();
         }

         ConnectToRouter var2 = this;

         ConnectToRouter $this$_set_enabled__u24lambda_u240;
         try {
            Companion var9 = Result.Companion;
            $this$_set_enabled__u24lambda_u240 = (ConnectToRouter)var2;
            int $i$a$-runCatching-ConnectToRouter$enabled$1 = 0;
            FileManager.saveConfig$default(FileManager.INSTANCE, (FileConfig)FileManager.INSTANCE.getValuesConfig(), false, 2, (Object)null);
            $this$_set_enabled__u24lambda_u240 = (ConnectToRouter)Result.constructor-impl(Unit.INSTANCE);
         } catch (Throwable var6) {
            Companion var10000 = Result.Companion;
            $this$_set_enabled__u24lambda_u240 = (ConnectToRouter)Result.constructor-impl(ResultKt.createFailure(var6));
         }

         Throwable var10 = Result.exceptionOrNull-impl($this$_set_enabled__u24lambda_u240);
         if(var10 != null) {
            Throwable var8 = var10;
            int $i$a$-onFailure-ConnectToRouter$enabled$2 = 0;
            INSTANCE.logDebug("Failed to persist router state: " + var8.getMessage());
         }

      }
   }

   public final boolean getDebugEnabled() {
      return debugEnabled;
   }

   public final void setDebugEnabled(boolean value) {
      debugEnabled = value;
   }

   @NotNull
   public final ConnectToRouter.Status getStatus() {
      return status;
   }

   @NotNull
   public final String getLastError() {
      return lastError;
   }

   @NotNull
   public final String getLastLocalIp() {
      return lastLocalIp;
   }

   public final boolean getVpnDetected() {
      return vpnDetected;
   }

   @NotNull
   public final String getSelectedInterface() {
      return selectedInterface;
   }

   public final boolean getTunnelAvailable() {
      return tunnelAvailable;
   }

   @NotNull
   public final String getTunnelInterface() {
      return tunnelInterface;
   }

   @NotNull
   public final String getTunnelIp() {
      return tunnelIp;
   }

   public final boolean getWasAutoDisabled() {
      return wasAutoDisabled;
   }

   @NotNull
   public final String getAutoDisableReason() {
      return autoDisableReason;
   }

   public final boolean getLastTcpOk() {
      return lastTcpOk;
   }

   public final long getLastTcpMs() {
      return lastTcpMs;
   }

   @NotNull
   public final String getLastTcpError() {
      return lastTcpError;
   }

   @NotNull
   public final Unit getOnUpdate() {
      return onUpdate;
   }

   public final boolean isTunnelMode() {
      return enabled && status == ConnectToRouter.Status.TUNNEL;
   }

   @NotNull
   public final synchronized ConnectToRouter.UltraFastRefreshResult ultraFastRefreshServerPing() {
      long now = System.currentTimeMillis();
      if(now - lastUltraFastRefreshAt < ultraRefreshDebounceMs) {
         String currentIp = this.normalizeIp(this.getActivePingIp());
         this.logDebug("Ultra Fast Refresh skipped (debounced)");
         return new ConnectToRouter.UltraFastRefreshResult(currentIp, currentIp, false);
      } else {
         lastUltraFastRefreshAt = now;
         String previousIp = this.normalizeIp(this.getActivePingIp());
         this.sendRefreshPacket();
         this.refreshStatus();
         String currentIp = this.normalizeIp(this.getActivePingIp());
         boolean changed = !StringsKt.isBlank((CharSequence)currentIp) && !StringsKt.equals(previousIp, currentIp, true);
         if(changed) {
            this.logDebug("Ultra Fast Refresh switched ping IP: " + previousIp + " -> " + currentIp);
         } else {
            ConnectToRouter var10000 = this;
            StringBuilder var10001 = (new StringBuilder()).append("Ultra Fast Refresh ping IP unchanged: ");
            CharSequence var6 = (CharSequence)currentIp;
            Object var10002;
            if(var6.length() == 0) {
               StringBuilder var9 = var10001;
               int $i$a$-ifEmpty-ConnectToRouter$ultraFastRefreshServerPing$1 = 0;
               String var10 = "unknown";
               var10000 = this;
               var10001 = var9;
               var10002 = var10;
            } else {
               var10002 = var6;
            }

            var10000.logDebug(var10001.append((String)var10002).toString());
         }

         return new ConnectToRouter.UltraFastRefreshResult(previousIp, currentIp, changed);
      }
   }

   @NotNull
   public final String getActivePingIp() {
      String fromTunnel = this.normalizeIp(tunnelIp);
      if(tunnelAvailable && !StringsKt.isBlank((CharSequence)fromTunnel)) {
         return fromTunnel;
      } else {
         String fromInterface = this.normalizeIp(lastLocalIp);
         return status == ConnectToRouter.Status.CONNECTED && !StringsKt.isBlank((CharSequence)fromInterface)?fromInterface:this.normalizeIp(this.detectSystemOutboundIp());
      }
   }

   @Nullable
   public final InetAddress getPreferredLocalAddressFor(@Nullable InetAddress remoteAddress) {
      if(enabled && status == ConnectToRouter.Status.CONNECTED) {
         if(remoteAddress == null) {
            return null;
         } else if(!remoteAddress.isLoopbackAddress() && !remoteAddress.isLinkLocalAddress() && !remoteAddress.isMulticastAddress() && !remoteAddress.isSiteLocalAddress()) {
            InetAddress var10000 = preferredAddress;
            if(preferredAddress == null) {
               return null;
            } else {
               InetAddress local = var10000;
               if(local instanceof Inet4Address != (remoteAddress instanceof Inet4Address)) {
                  return null;
               } else {
                  NetworkInterface var4 = NetworkInterface.getByInetAddress(local);
                  if(var4 == null) {
                     return null;
                  } else {
                     NetworkInterface localIf = var4;
                     return localIf.isUp() && !localIf.isLoopback()?(!lastTcpOk?null:local):null;
                  }
               }
            }
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   @NotNull
   public final String getStatusLine() {
      String var10000;
      if(!enabled && wasAutoDisabled) {
         var10000 = "Auto-disabled: " + autoDisableReason;
      } else if(!enabled) {
         var10000 = "Off";
      } else if(status == ConnectToRouter.Status.TUNNEL) {
         StringBuilder var9 = (new StringBuilder()).append("Tunnel active: ").append(tunnelInterface).append(" (");
         CharSequence var1 = (CharSequence)tunnelIp;
         Object var10001;
         if(var1.length() == 0) {
            StringBuilder var3 = var9;
            int $i$a$-ifEmpty-ConnectToRouter$getStatusLine$1 = 0;
            var10001 = "?";
            var9 = var3;
         } else {
            var10001 = var1;
         }

         var10000 = var9.append((String)var10001).append(')').toString();
      } else if(status == ConnectToRouter.Status.CONNECTED) {
         StringBuilder var10 = (new StringBuilder()).append("Interface bind: ");
         CharSequence var4 = (CharSequence)lastLocalIp;
         Object var12;
         if(var4.length() == 0) {
            StringBuilder var8 = var10;
            int $i$a$-ifEmpty-ConnectToRouter$getStatusLine$2 = 0;
            var12 = "?";
            var10 = var8;
         } else {
            var12 = var4;
         }

         var10000 = var10.append((String)var12).toString();
      } else if(status == ConnectToRouter.Status.FAILED) {
         CharSequence var5 = (CharSequence)lastError;
         if(var5.length() == 0) {
            int $i$a$-ifEmpty-ConnectToRouter$getStatusLine$3 = 0;
            var10000 = "Failed";
         } else {
            var10000 = var5;
         }

         var10000 = (String)var10000;
      } else {
         var10000 = "Detecting...";
      }

      return var10000;
   }

   public final int getStatusColor() {
      return !enabled && wasAutoDisabled?16746496:(!enabled?11184810:(status == ConnectToRouter.Status.TUNNEL?5635925:(status == ConnectToRouter.Status.CONNECTED?11206485:(status == ConnectToRouter.Status.FAILED?16733525:16777045))));
   }

   public final void refreshStatus() {
      this.logDebug("refresh start");
      status = ConnectToRouter.Status.DETECTING;
      refreshTimer.reset();
      ConnectToRouter.TunnelCheckResult tunnel = this.checkTunnel();
      tunnelAvailable = tunnel.getAvailable();
      tunnelInterface = tunnel.getIface();
      tunnelIp = tunnel.getIp();
      if(tunnel.getAvailable()) {
         status = ConnectToRouter.Status.TUNNEL;
         lastError = "";
         consecutiveFailures = 0;
         this.logDebug("Tunnel available on port 25560 \u2014 " + tunnel.getIface() + " (" + tunnel.getIp() + ')');
      } else {
         this.logDebug("Tunnel not running on port 25560");
         if(debugEnabled) {
            this.dumpInterfaces();
         }

         ConnectToRouter.AddressResult addr = this.findPreferredAddress();
         if(addr.getError() != null) {
            this.onDetectionFailed(addr.getError());
         } else {
            preferredAddress = addr.getAddress();
            InetAddress var10000 = addr.getAddress();
            String var4 = var10000 != null?var10000.getHostAddress():null;
            if(var4 == null) {
               var4 = "";
            }

            lastLocalIp = var4;
            ConnectToRouter.ProbeResult tcp = this.tcpProbe("bing.com", 443, addr.getAddress());
            lastTcpOk = tcp.getOk();
            lastTcpMs = tcp.getTimeMs();
            var4 = tcp.getError();
            if(var4 == null) {
               var4 = "";
            }

            lastTcpError = var4;
            if(tcp.getOk()) {
               status = ConnectToRouter.Status.CONNECTED;
               lastError = "";
               consecutiveFailures = 0;
               this.logDebug("Interface binding OK \u2013 " + selectedInterface + " (" + lastLocalIp + ") tcp=" + tcp.getTimeMs() + "ms");
            } else {
               this.onDetectionFailed("No route via " + selectedInterface);
            }

         }
      }
   }

   public final void sendRefreshPacket() {
      ConnectToRouter var1 = this;

      ConnectToRouter $this$sendRefreshPacket_u24lambda_u248;
      try {
         Companion var24 = Result.Companion;
         $this$sendRefreshPacket_u24lambda_u248 = (ConnectToRouter)var1;
         int $i$a$-runCatching-ConnectToRouter$sendRefreshPacket$1 = 0;
         Closeable $i$a$-onFailure-ConnectToRouter$sendRefreshPacket$2 = (Closeable)(new Socket());
         Throwable var5 = null;

         try {
            Socket sock = (Socket)$i$a$-onFailure-ConnectToRouter$sendRefreshPacket$2;
            int $i$a$-use-ConnectToRouter$sendRefreshPacket$1$1 = 0;
            sock.connect((SocketAddress)(new InetSocketAddress("127.0.0.1", 25560)), 1000);
            sock.setSoTimeout(2000);
            sock.getOutputStream().write(1);
            sock.getOutputStream().flush();
            InputStream inp = sock.getInputStream();
            int len = inp.read();
            if(len > 0) {
               byte[] data = new byte[len];

               int read;
               int n;
               for(read = 0; read < len; read += n) {
                  n = inp.read(data, read, len - read);
                  if(n <= 0) {
                     break;
                  }
               }

               String json = new String(data, 0, read, Charsets.UTF_8);
               $this$sendRefreshPacket_u24lambda_u248.logDebug("Tunnel refresh response: " + json);
            }

            Unit var22 = Unit.INSTANCE;
         } catch (Throwable var16) {
            var5 = var16;
            throw var16;
         } finally {
            CloseableKt.closeFinally($i$a$-onFailure-ConnectToRouter$sendRefreshPacket$2, var5);
         }

         $this$sendRefreshPacket_u24lambda_u248 = (ConnectToRouter)Result.constructor-impl(Unit.INSTANCE);
      } catch (Throwable var18) {
         Companion var10000 = Result.Companion;
         $this$sendRefreshPacket_u24lambda_u248 = (ConnectToRouter)Result.constructor-impl(ResultKt.createFailure(var18));
      }

      Throwable var25 = Result.exceptionOrNull-impl($this$sendRefreshPacket_u24lambda_u248);
      if(var25 != null) {
         Throwable var20 = var25;
         int $i$a$-onFailure-ConnectToRouter$sendRefreshPacket$2 = 0;
         INSTANCE.logDebug("Tunnel refresh failed (tunnel may not be running): " + var20.getMessage());
      }

   }

   private final ConnectToRouter.TunnelCheckResult checkTunnel() {
      ConnectToRouter var1 = this;

      ConnectToRouter $this$checkTunnel_u24lambda_u2411;
      try {
         Companion var26 = Result.Companion;
         $this$checkTunnel_u24lambda_u2411 = (ConnectToRouter)var1;
         int $i$a$-runCatching-ConnectToRouter$checkTunnel$1 = 0;
         Closeable var4 = (Closeable)(new Socket());
         Throwable var5 = null;

         ConnectToRouter.TunnelCheckResult var24;
         try {
            Socket sock = (Socket)var4;
            int $i$a$-use-ConnectToRouter$checkTunnel$1$1 = 0;
            sock.connect((SocketAddress)(new InetSocketAddress("127.0.0.1", 25560)), 500);
            sock.setSoTimeout(500);
            sock.getOutputStream().write(0);
            sock.getOutputStream().flush();
            InputStream inp = sock.getInputStream();
            int len = inp.read();
            ConnectToRouter.TunnelCheckResult var33;
            if(len <= 0) {
               var33 = new ConnectToRouter.TunnelCheckResult(false, "", "");
            } else {
               byte[] data = new byte[len];

               int read;
               int n;
               for(read = 0; read < len; read += n) {
                  n = inp.read(data, read, len - read);
                  if(n <= 0) {
                     break;
                  }
               }

               label170: {
                  json = new String(data, 0, read, Charsets.UTF_8);
                  $this$checkTunnel_u24lambda_u2411.logDebug("Tunnel health response: " + json);
                  MatchResult var27 = Regex.find$default(new Regex("\"interface\":\"([^\"]+)\""), (CharSequence)json, 0, 2, (Object)null);
                  if(var27 != null) {
                     List var28 = var27.getGroupValues();
                     if(var28 != null) {
                        var29 = (String)var28.get(1);
                        if(var29 != null) {
                           break label170;
                        }
                     }
                  }

                  var29 = "";
               }

               String ifaceVal;
               label262: {
                  ifaceVal = var29;
                  MatchResult var30 = Regex.find$default(new Regex("\"ip\":\"([^\"]+)\""), (CharSequence)json, 0, 2, (Object)null);
                  if(var30 != null) {
                     List var31 = var30.getGroupValues();
                     if(var31 != null) {
                        var32 = (String)var31.get(1);
                        if(var32 != null) {
                           break label262;
                        }
                     }
                  }

                  var32 = "";
               }

               String ipVal = var32;
               var33 = new ConnectToRouter.TunnelCheckResult(true, ifaceVal, ipVal);
            }

            var24 = var33;
         } catch (Throwable var18) {
            var5 = var18;
            throw var18;
         } finally {
            CloseableKt.closeFinally(var4, var5);
         }

         $this$checkTunnel_u24lambda_u2411 = (ConnectToRouter)Result.constructor-impl(var24);
      } catch (Throwable var20) {
         Companion var10000 = Result.Companion;
         $this$checkTunnel_u24lambda_u2411 = (ConnectToRouter)Result.constructor-impl(ResultKt.createFailure(var20));
      }

      Throwable var34 = Result.exceptionOrNull-impl($this$checkTunnel_u24lambda_u2411);
      if(var34 == null) {
         var34 = $this$checkTunnel_u24lambda_u2411;
      } else {
         Throwable it = var34;
         int $i$a$-getOrElse-ConnectToRouter$checkTunnel$2 = 0;
         INSTANCE.logDebug("Tunnel check failed: " + it.getMessage());
         var34 = new ConnectToRouter.TunnelCheckResult(false, "", "");
      }

      return (ConnectToRouter.TunnelCheckResult)var34;
   }

   private final void onDetectionFailed(String reason) {
      status = ConnectToRouter.Status.FAILED;
      lastError = reason;
      int var2 = consecutiveFailures;
      consecutiveFailures = var2 + 1;
      this.logDebug("Detection failed (" + consecutiveFailures + '/' + AUTO_DISABLE_THRESHOLD + "): " + reason);
      if(consecutiveFailures >= AUTO_DISABLE_THRESHOLD) {
         this.logDebug("Auto-disabling after " + consecutiveFailures + " consecutive failures");
         wasAutoDisabled = true;
         autoDisableReason = reason;
         this.setEnabled(false);
      }

   }

   private final ConnectToRouter.AddressResult findPreferredAddress() {
      vpnDetected = false;
      selectedInterface = "";
      Enumeration var10000 = NetworkInterface.getNetworkInterfaces();
      if(var10000 == null) {
         return new ConnectToRouter.AddressResult((InetAddress)null, "No network interfaces");
      } else {
         Enumeration interfaces = var10000;
         ArrayList list = Collections.list(interfaces);
         if(list.isEmpty()) {
            return new ConnectToRouter.AddressResult((InetAddress)null, "No network interfaces");
         } else {
            List candidates = (List)(new ArrayList());
            Iterator var18 = list.iterator();
            Intrinsics.checkNotNullExpressionValue(var18, "iterator(...)");
            Iterator chosen = var18;

            while(chosen.hasNext()) {
               NetworkInterface intf = (NetworkInterface)chosen.next();
               if(intf.isUp() && !intf.isLoopback()) {
                  String var19 = intf.getDisplayName();
                  if(var19 == null) {
                     var19 = intf.getName();
                  }

                  String isVpn = var19;
                  Intrinsics.checkNotNull(isVpn);
                  Locale var20 = Locale.ENGLISH;
                  Intrinsics.checkNotNullExpressionValue(Locale.ENGLISH, "ENGLISH");
                  String var21 = isVpn.toLowerCase(var20);
                  Intrinsics.checkNotNullExpressionValue(var21, "toLowerCase(...)");
                  String nameLower = var21;
                  Intrinsics.checkNotNull(intf);
                  boolean isVpn = this.isVpnInterface(intf, nameLower);
                  if(isVpn) {
                     vpnDetected = true;
                  }

                  ArrayList var22 = Collections.list(intf.getInetAddresses());
                  Intrinsics.checkNotNullExpressionValue(var22, "list(...)");
                  Iterable $this$firstOrNull$iv = (Iterable)var22;
                  int $i$f$firstOrNull = 0;
                  Iterator var11 = $this$firstOrNull$iv.iterator();

                  while(true) {
                     if(!var11.hasNext()) {
                        var22 = null;
                        break;
                     }

                     Object element$iv = var11.next();
                     InetAddress it = (InetAddress)element$iv;
                     int $i$a$-firstOrNull-ConnectToRouter$findPreferredAddress$ipv4$1 = 0;
                     if(it instanceof Inet4Address && !((Inet4Address)it).isLoopbackAddress()) {
                        var22 = (ArrayList)element$iv;
                        break;
                     }
                  }

                  InetAddress ipv4 = (InetAddress)var22;
                  if(ipv4 != null && !isVpn) {
                     candidates.add(TuplesKt.to(intf, ipv4));
                  }
               }
            }

            if(!((Collection)candidates).isEmpty()) {
               Pair chosen = (Pair)CollectionsKt.first(candidates);
               String var26 = ((NetworkInterface)chosen.getFirst()).getDisplayName();
               if(var26 == null) {
                  var26 = ((NetworkInterface)chosen.getFirst()).getName();
                  Intrinsics.checkNotNullExpressionValue(var26, "getName(...)");
               }

               selectedInterface = var26;
               return new ConnectToRouter.AddressResult((InetAddress)chosen.getSecond(), (String)null);
            } else if(vpnDetected) {
               return new ConnectToRouter.AddressResult((InetAddress)null, "No non-VPN interface");
            } else {
               Intrinsics.checkNotNull(list);
               Pair fallback = (Pair)SequencesKt.firstOrNull(SequencesKt.flatMap(SequencesKt.filter(CollectionsKt.asSequence((Iterable)list), ConnectToRouter::findPreferredAddress$lambda$14), ConnectToRouter::findPreferredAddress$lambda$17));
               ConnectToRouter.AddressResult var25;
               if(fallback != null) {
                  String var24 = ((NetworkInterface)fallback.getFirst()).getDisplayName();
                  if(var24 == null) {
                     var24 = ((NetworkInterface)fallback.getFirst()).getName();
                     Intrinsics.checkNotNullExpressionValue(var24, "getName(...)");
                  }

                  selectedInterface = var24;
                  var25 = new ConnectToRouter.AddressResult((InetAddress)fallback.getSecond(), (String)null);
               } else {
                  var25 = new ConnectToRouter.AddressResult((InetAddress)null, "No usable address");
               }

               return var25;
            }
         }
      }
   }

   private final boolean isVpnInterface(NetworkInterface intf, String nameLower) {
      if(intf.isPointToPoint()) {
         return true;
      } else {
         String[] $this$any$iv = new String[]{"tun", "tap", "ppp", "wg", "utun", "ipsec", "vpn", "wireguard", "tailscale", "zerotier"};
         Iterable var9 = (Iterable)CollectionsKt.listOf($this$any$iv);
         int $i$f$any = 0;
         boolean var10000;
         if(var9 instanceof Collection && ((Collection)var9).isEmpty()) {
            var10000 = false;
         } else {
            Iterator var5 = var9.iterator();

            while(true) {
               if(!var5.hasNext()) {
                  var10000 = false;
                  break;
               }

               Object element$iv = var5.next();
               String it = (String)element$iv;
               int $i$a$-any-ConnectToRouter$isVpnInterface$1 = 0;
               if(StringsKt.contains$default((CharSequence)nameLower, (CharSequence)it, false, 2, (Object)null)) {
                  var10000 = true;
                  break;
               }
            }
         }

         return var10000;
      }
   }

   private final ConnectToRouter.ProbeResult tcpProbe(String host, int port, InetAddress localAddress) {
      long start = System.currentTimeMillis();
      Socket socket = new Socket();

      try {
         if(localAddress != null) {
            socket.bind(new InetSocketAddress(localAddress, 0));
         }

         socket.connect(new InetSocketAddress(host, port), 1500);
         return new ConnectToRouter.ProbeResult(true, System.currentTimeMillis() - start, "");
      } catch (Throwable var9) {
         String message = var9.getMessage();
         if(message == null) {
            message = var9.getClass().getSimpleName();
         }

         return new ConnectToRouter.ProbeResult(false, System.currentTimeMillis() - start, message);
      } finally {
         try {
            socket.close();
         } catch (Exception var8) {
         }
      }
   }

   private final String detectSystemOutboundIp() {
      ConnectToRouter interfaces = this;

      ConnectToRouter $this$detectSystemOutboundIp_u24lambda_u2423;
      try {
         Companion var30 = Result.Companion;
         $this$detectSystemOutboundIp_u24lambda_u2423 = (ConnectToRouter)interfaces;
         int $i$a$-runCatching-ConnectToRouter$detectSystemOutboundIp$outbound$1 = 0;
         Closeable addr = (Closeable)(new DatagramSocket());
         Throwable $this$firstOrNull$iv = null;

         String $i$f$firstOrNull;
         try {
            DatagramSocket socket = (DatagramSocket)addr;
            int $i$a$-use-ConnectToRouter$detectSystemOutboundIp$outbound$1$1 = 0;
            socket.connect((SocketAddress)(new InetSocketAddress("8.8.8.8", 53)));
            InetAddress local = socket.getLocalAddress();
            $i$f$firstOrNull = local instanceof Inet4Address && !((Inet4Address)local).isLoopbackAddress()?((Inet4Address)local).getHostAddress():"";
         } catch (Throwable var15) {
            $this$firstOrNull$iv = var15;
            throw var15;
         } finally {
            CloseableKt.closeFinally(addr, $this$firstOrNull$iv);
         }

         $this$detectSystemOutboundIp_u24lambda_u2423 = (ConnectToRouter)Result.constructor-impl($i$f$firstOrNull);
      } catch (Throwable var17) {
         Companion var10000 = Result.Companion;
         $this$detectSystemOutboundIp_u24lambda_u2423 = (ConnectToRouter)Result.constructor-impl(ResultKt.createFailure(var17));
      }

      interfaces = $this$detectSystemOutboundIp_u24lambda_u2423;
      String var21 = "";
      String outbound = (String)(Result.isFailure-impl(interfaces)?var21:interfaces);
      Intrinsics.checkNotNull(outbound);
      if(!StringsKt.isBlank((CharSequence)this.normalizeIp(outbound))) {
         return outbound;
      } else {
         Enumeration var31 = NetworkInterface.getNetworkInterfaces();
         if(var31 == null) {
            return "";
         } else {
            Enumeration interfaces = var31;
            Iterator var32 = Collections.list(interfaces).iterator();
            Intrinsics.checkNotNullExpressionValue(var32, "iterator(...)");
            Iterator var22 = var32;

            InetAddress addr;
            while(true) {
               if(!var22.hasNext()) {
                  return "";
               }

               NetworkInterface intf = (NetworkInterface)var22.next();
               if(intf.isUp() && !intf.isLoopback()) {
                  ArrayList var33 = Collections.list(intf.getInetAddresses());
                  Intrinsics.checkNotNullExpressionValue(var33, "list(...)");
                  Iterable var25 = (Iterable)var33;
                  int $i$f$firstOrNull = 0;
                  Iterator var28 = var25.iterator();

                  while(true) {
                     if(!var28.hasNext()) {
                        var33 = null;
                        break;
                     }

                     Object element$iv = var28.next();
                     InetAddress it = (InetAddress)element$iv;
                     int $i$a$-firstOrNull-ConnectToRouter$detectSystemOutboundIp$addr$1 = 0;
                     if(it instanceof Inet4Address && !((Inet4Address)it).isLoopbackAddress()) {
                        var33 = (ArrayList)element$iv;
                        break;
                     }
                  }

                  addr = (InetAddress)var33;
                  if(addr != null) {
                     break;
                  }
               }
            }

            String var35 = addr.getHostAddress();
            Intrinsics.checkNotNullExpressionValue(var35, "getHostAddress(...)");
            return var35;
         }
      }
   }

   private final String normalizeIp(String ip) {
      String trimmed = StringsKt.trim((CharSequence)ip).toString();
      return ((CharSequence)trimmed).length() == 0?"":(Intrinsics.areEqual(trimmed, "0.0.0.0")?"":trimmed);
   }

   private final void dumpInterfaces() {
      Enumeration var10000 = NetworkInterface.getNetworkInterfaces();
      if(var10000 != null) {
         Enumeration ifs = var10000;
         Iterator var8 = Collections.list(ifs).iterator();
         Intrinsics.checkNotNullExpressionValue(var8, "iterator(...)");
         Iterator var2 = var8;

         while(var2.hasNext()) {
            NetworkInterface intf = (NetworkInterface)var2.next();
            String var9 = intf.getDisplayName();
            if(var9 == null) {
               var9 = intf.getName();
            }

            String name = var9;
            List flags = (List)(new ArrayList());
            if(intf.isUp()) {
               flags.add("up");
            }

            if(intf.isLoopback()) {
               flags.add("lo");
            }

            if(intf.isPointToPoint()) {
               flags.add("p2p");
            }

            this.logDebug("  iface " + name + " (" + intf.getName() + ") " + CollectionsKt.joinToString$default((Iterable)flags, (CharSequence)",", (CharSequence)null, (CharSequence)null, 0, (CharSequence)null, (Function1)null, 62, (Object)null) + " mtu=" + intf.getMTU());
            Iterator var10 = Collections.list(intf.getInetAddresses()).iterator();
            Intrinsics.checkNotNullExpressionValue(var10, "iterator(...)");
            Iterator var6 = var10;

            while(var6.hasNext()) {
               InetAddress a = (InetAddress)var6.next();
               this.logDebug("    " + (a instanceof Inet4Address?"v4":"v6") + ": " + a.getHostAddress());
            }
         }

      }
   }

   private final void logDebug(String message) {
      if(debugEnabled) {
         ClientUtils.INSTANCE.getLOGGER().info("[ConnectToRouter] " + message);
      }
   }

   @NotNull
   public Minecraft getMc() {
      return DefaultImpls.getMc(this);
   }

   public boolean handleEvents() {
      return net.asd.union.event.Listenable.DefaultImpls.handleEvents(this);
   }

   public void unregister() {
      net.asd.union.event.Listenable.DefaultImpls.unregister(this);
   }

   @NotNull
   public Listenable[] getSubListeners() {
      return net.asd.union.event.Listenable.DefaultImpls.getSubListeners(this);
   }

   @Nullable
   public Listenable getParent() {
      return net.asd.union.event.Listenable.DefaultImpls.getParent(this);
   }

   private static final Unit onUpdate$lambda$2(UpdateEvent it) {
      Intrinsics.checkNotNullParameter(it, "it");
      ConnectToRouter var10000 = INSTANCE;
      return !enabled?Unit.INSTANCE:Unit.INSTANCE;
   }

   private static final boolean findPreferredAddress$lambda$14(NetworkInterface it) {
      return it.isUp() && !it.isLoopback();
   }

   private static final boolean findPreferredAddress$lambda$17$lambda$15(InetAddress it) {
      return it instanceof Inet4Address && !((Inet4Address)it).isLoopbackAddress();
   }

   private static final Pair findPreferredAddress$lambda$17$lambda$16(NetworkInterface $i, InetAddress it) {
      return TuplesKt.to($i, it);
   }

   private static final Sequence findPreferredAddress$lambda$17(NetworkInterface i) {
      ArrayList var10000 = Collections.list(i.getInetAddresses());
      Intrinsics.checkNotNullExpressionValue(var10000, "list(...)");
      return SequencesKt.map(SequencesKt.filter(CollectionsKt.asSequence((Iterable)var10000), ConnectToRouter::findPreferredAddress$lambda$17$lambda$15), ConnectToRouter::findPreferredAddress$lambda$17$lambda$16);
   }

   static {
      Listenable $this$handler_u24default$iv = (Listenable)INSTANCE;
      Function1 action$iv = ConnectToRouter::onUpdate$lambda$2;
      boolean always$iv = false;
      byte priority$iv = 0;
      int $i$f$handler = 0;
      EventManager.INSTANCE.registerEventHook(UpdateEvent.class, (EventHook)(new Blocking($this$handler_u24default$iv, always$iv, priority$iv, action$iv)));
   }

   @Metadata(
      mv = {2, 0, 0},
      k = 1,
      xi = 48,
      d1 = {"\u0000(\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\n\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0002\b\u0082\b\u0018\u00002\u00020\u0001B\u001b\u0012\b\u0010\u0002\u001a\u0004\u0018\u00010\u0003\u0012\b\u0010\u0004\u001a\u0004\u0018\u00010\u0005\u00a2\u0006\u0004\b\u0006\u0010\u0007J\u000b\u0010\f\u001a\u0004\u0018\u00010\u0003H\u00c6\u0003J\u000b\u0010\r\u001a\u0004\u0018\u00010\u0005H\u00c6\u0003J!\u0010\u000e\u001a\u00020\u00002\n\b\u0002\u0010\u0002\u001a\u0004\u0018\u00010\u00032\n\b\u0002\u0010\u0004\u001a\u0004\u0018\u00010\u0005H\u00c6\u0001J\u0013\u0010\u000f\u001a\u00020\u00102\b\u0010\u0011\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u0012\u001a\u00020\u0013H\u00d6\u0001J\t\u0010\u0014\u001a\u00020\u0005H\u00d6\u0001R\u0013\u0010\u0002\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\tR\u0013\u0010\u0004\u001a\u0004\u0018\u00010\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b\u00a8\u0006\u0015"},
      d2 = {"Lnet/asd/union/handler/network/ConnectToRouter$AddressResult;", "", "address", "Ljava/net/InetAddress;", "error", "", "<init>", "(Ljava/net/InetAddress;Ljava/lang/String;)V", "getAddress", "()Ljava/net/InetAddress;", "getError", "()Ljava/lang/String;", "component1", "component2", "copy", "equals", "", "other", "hashCode", "", "toString", "AsdUnion"}
   )
   private static final class AddressResult {
      @Nullable
      private final InetAddress address;
      @Nullable
      private final String error;

      public AddressResult(@Nullable InetAddress address, @Nullable String error) {
         this.address = address;
         this.error = error;
      }

      @Nullable
      public final InetAddress getAddress() {
         return this.address;
      }

      @Nullable
      public final String getError() {
         return this.error;
      }

      @Nullable
      public final InetAddress component1() {
         return this.address;
      }

      @Nullable
      public final String component2() {
         return this.error;
      }

      @NotNull
      public final ConnectToRouter.AddressResult copy(@Nullable InetAddress address, @Nullable String error) {
         return new ConnectToRouter.AddressResult(address, error);
      }

      // $FF: synthetic method
      public static ConnectToRouter.AddressResult copy$default(ConnectToRouter.AddressResult var0, InetAddress var1, String var2, int var3, Object var4) {
         if((var3 & 1) != 0) {
            var1 = var0.address;
         }

         if((var3 & 2) != 0) {
            var2 = var0.error;
         }

         return var0.copy(var1, var2);
      }

      @NotNull
      public String toString() {
         return "AddressResult(address=" + this.address + ", error=" + this.error + ')';
      }

      public int hashCode() {
         int result = this.address == null?0:this.address.hashCode();
         result = result * 31 + (this.error == null?0:this.error.hashCode());
         return result;
      }

      public boolean equals(@Nullable Object other) {
         if(this == other) {
            return true;
         } else if(!(other instanceof ConnectToRouter.AddressResult)) {
            return false;
         } else {
            ConnectToRouter.AddressResult var2 = (ConnectToRouter.AddressResult)other;
            return !Intrinsics.areEqual(this.address, var2.address)?false:Intrinsics.areEqual(this.error, var2.error);
         }
      }
   }

   @Metadata(
      mv = {2, 0, 0},
      k = 1,
      xi = 48,
      d1 = {"\u0000&\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\t\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u000f\n\u0002\u0010\b\n\u0002\b\u0002\b\u0082\b\u0018\u00002\u00020\u0001B!\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\b\u0010\u0006\u001a\u0004\u0018\u00010\u0007\u00a2\u0006\u0004\b\b\u0010\tJ\t\u0010\u0010\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u0011\u001a\u00020\u0005H\u00c6\u0003J\u000b\u0010\u0012\u001a\u0004\u0018\u00010\u0007H\u00c6\u0003J)\u0010\u0013\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00052\n\b\u0002\u0010\u0006\u001a\u0004\u0018\u00010\u0007H\u00c6\u0001J\u0013\u0010\u0014\u001a\u00020\u00032\b\u0010\u0015\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u0016\u001a\u00020\u0017H\u00d6\u0001J\t\u0010\u0018\u001a\u00020\u0007H\u00d6\u0001R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000bR\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\f\u0010\rR\u0013\u0010\u0006\u001a\u0004\u0018\u00010\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000e\u0010\u000f\u00a8\u0006\u0019"},
      d2 = {"Lnet/asd/union/handler/network/ConnectToRouter$ProbeResult;", "", "ok", "", "timeMs", "", "error", "", "<init>", "(ZJLjava/lang/String;)V", "getOk", "()Z", "getTimeMs", "()J", "getError", "()Ljava/lang/String;", "component1", "component2", "component3", "copy", "equals", "other", "hashCode", "", "toString", "AsdUnion"}
   )
   private static final class ProbeResult {
      private final boolean ok;
      private final long timeMs;
      @Nullable
      private final String error;

      public ProbeResult(boolean ok, long timeMs, @Nullable String error) {
         this.ok = ok;
         this.timeMs = timeMs;
         this.error = error;
      }

      public final boolean getOk() {
         return this.ok;
      }

      public final long getTimeMs() {
         return this.timeMs;
      }

      @Nullable
      public final String getError() {
         return this.error;
      }

      public final boolean component1() {
         return this.ok;
      }

      public final long component2() {
         return this.timeMs;
      }

      @Nullable
      public final String component3() {
         return this.error;
      }

      @NotNull
      public final ConnectToRouter.ProbeResult copy(boolean ok, long timeMs, @Nullable String error) {
         return new ConnectToRouter.ProbeResult(ok, timeMs, error);
      }

      // $FF: synthetic method
      public static ConnectToRouter.ProbeResult copy$default(ConnectToRouter.ProbeResult var0, boolean var1, long var2, String var4, int var5, Object var6) {
         if((var5 & 1) != 0) {
            var1 = var0.ok;
         }

         if((var5 & 2) != 0) {
            var2 = var0.timeMs;
         }

         if((var5 & 4) != 0) {
            var4 = var0.error;
         }

         return var0.copy(var1, var2, var4);
      }

      @NotNull
      public String toString() {
         return "ProbeResult(ok=" + this.ok + ", timeMs=" + this.timeMs + ", error=" + this.error + ')';
      }

      public int hashCode() {
         int result = Boolean.hashCode(this.ok);
         result = result * 31 + Long.hashCode(this.timeMs);
         result = result * 31 + (this.error == null?0:this.error.hashCode());
         return result;
      }

      public boolean equals(@Nullable Object other) {
         if(this == other) {
            return true;
         } else if(!(other instanceof ConnectToRouter.ProbeResult)) {
            return false;
         } else {
            ConnectToRouter.ProbeResult var2 = (ConnectToRouter.ProbeResult)other;
            return this.ok != var2.ok?false:(this.timeMs != var2.timeMs?false:Intrinsics.areEqual(this.error, var2.error));
         }
      }
   }

   @Metadata(
      mv = {2, 0, 0},
      k = 1,
      xi = 48,
      d1 = {"\u0000\f\n\u0002\u0018\u0002\n\u0002\u0010\u0010\n\u0002\b\b\b\u0086\u0081\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00000\u0001B\t\b\u0002\u00a2\u0006\u0004\b\u0002\u0010\u0003j\u0002\b\u0004j\u0002\b\u0005j\u0002\b\u0006j\u0002\b\u0007j\u0002\b\b\u00a8\u0006\t"},
      d2 = {"Lnet/asd/union/handler/network/ConnectToRouter$Status;", "", "<init>", "(Ljava/lang/String;I)V", "OFF", "DETECTING", "TUNNEL", "CONNECTED", "FAILED", "AsdUnion"}
   )
   public static enum Status {
      OFF,
      DETECTING,
      TUNNEL,
      CONNECTED,
      FAILED;

      // $FF: synthetic field
      private static final EnumEntries $ENTRIES = EnumEntriesKt.enumEntries((Enum[])$VALUES);

      @NotNull
      public static EnumEntries<ConnectToRouter.Status> getEntries() {
         return $ENTRIES;
      }

      // $FF: synthetic method
      private static final ConnectToRouter.Status[] $values() {
         ConnectToRouter.Status[] var0 = new ConnectToRouter.Status[]{OFF, DETECTING, TUNNEL, CONNECTED, FAILED};
         return var0;
      }
   }

   @Metadata(
      mv = {2, 0, 0},
      k = 1,
      xi = 48,
      d1 = {"\u0000 \n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u000f\n\u0002\u0010\b\n\u0002\b\u0002\b\u0082\b\u0018\u00002\u00020\u0001B\u001f\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0005\u00a2\u0006\u0004\b\u0007\u0010\bJ\t\u0010\u000e\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u000f\u001a\u00020\u0005H\u00c6\u0003J\t\u0010\u0010\u001a\u00020\u0005H\u00c6\u0003J\'\u0010\u0011\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00052\b\b\u0002\u0010\u0006\u001a\u00020\u0005H\u00c6\u0001J\u0013\u0010\u0012\u001a\u00020\u00032\b\u0010\u0013\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u0014\u001a\u00020\u0015H\u00d6\u0001J\t\u0010\u0016\u001a\u00020\u0005H\u00d6\u0001R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\t\u0010\nR\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\fR\u0011\u0010\u0006\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\f\u00a8\u0006\u0017"},
      d2 = {"Lnet/asd/union/handler/network/ConnectToRouter$TunnelCheckResult;", "", "available", "", "iface", "", "ip", "<init>", "(ZLjava/lang/String;Ljava/lang/String;)V", "getAvailable", "()Z", "getIface", "()Ljava/lang/String;", "getIp", "component1", "component2", "component3", "copy", "equals", "other", "hashCode", "", "toString", "AsdUnion"}
   )
   private static final class TunnelCheckResult {
      private final boolean available;
      @NotNull
      private final String iface;
      @NotNull
      private final String ip;

      public TunnelCheckResult(boolean available, @NotNull String iface, @NotNull String ip) {
         Intrinsics.checkNotNullParameter(iface, "iface");
         Intrinsics.checkNotNullParameter(ip, "ip");
         super();
         this.available = available;
         this.iface = iface;
         this.ip = ip;
      }

      public final boolean getAvailable() {
         return this.available;
      }

      @NotNull
      public final String getIface() {
         return this.iface;
      }

      @NotNull
      public final String getIp() {
         return this.ip;
      }

      public final boolean component1() {
         return this.available;
      }

      @NotNull
      public final String component2() {
         return this.iface;
      }

      @NotNull
      public final String component3() {
         return this.ip;
      }

      @NotNull
      public final ConnectToRouter.TunnelCheckResult copy(boolean available, @NotNull String iface, @NotNull String ip) {
         Intrinsics.checkNotNullParameter(iface, "iface");
         Intrinsics.checkNotNullParameter(ip, "ip");
         return new ConnectToRouter.TunnelCheckResult(available, iface, ip);
      }

      // $FF: synthetic method
      public static ConnectToRouter.TunnelCheckResult copy$default(ConnectToRouter.TunnelCheckResult var0, boolean var1, String var2, String var3, int var4, Object var5) {
         if((var4 & 1) != 0) {
            var1 = var0.available;
         }

         if((var4 & 2) != 0) {
            var2 = var0.iface;
         }

         if((var4 & 4) != 0) {
            var3 = var0.ip;
         }

         return var0.copy(var1, var2, var3);
      }

      @NotNull
      public String toString() {
         return "TunnelCheckResult(available=" + this.available + ", iface=" + this.iface + ", ip=" + this.ip + ')';
      }

      public int hashCode() {
         int result = Boolean.hashCode(this.available);
         result = result * 31 + this.iface.hashCode();
         result = result * 31 + this.ip.hashCode();
         return result;
      }

      public boolean equals(@Nullable Object other) {
         if(this == other) {
            return true;
         } else if(!(other instanceof ConnectToRouter.TunnelCheckResult)) {
            return false;
         } else {
            ConnectToRouter.TunnelCheckResult var2 = (ConnectToRouter.TunnelCheckResult)other;
            return this.available != var2.available?false:(!Intrinsics.areEqual(this.iface, var2.iface)?false:Intrinsics.areEqual(this.ip, var2.ip));
         }
      }
   }

   @Metadata(
      mv = {2, 0, 0},
      k = 1,
      xi = 48,
      d1 = {"\u0000\"\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\u000e\n\u0002\u0010\b\n\u0002\b\u0002\b\u0086\b\u0018\u00002\u00020\u0001B\u001f\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0003\u0012\u0006\u0010\u0005\u001a\u00020\u0006\u00a2\u0006\u0004\b\u0007\u0010\bJ\t\u0010\u000e\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u000f\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u0010\u001a\u00020\u0006H\u00c6\u0003J\'\u0010\u0011\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00032\b\b\u0002\u0010\u0005\u001a\u00020\u0006H\u00c6\u0001J\u0013\u0010\u0012\u001a\u00020\u00062\b\u0010\u0013\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u0014\u001a\u00020\u0015H\u00d6\u0001J\t\u0010\u0016\u001a\u00020\u0003H\u00d6\u0001R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\t\u0010\nR\u0011\u0010\u0004\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\nR\u0011\u0010\u0005\u001a\u00020\u0006\u00a2\u0006\b\n\u0000\u001a\u0004\b\f\u0010\r\u00a8\u0006\u0017"},
      d2 = {"Lnet/asd/union/handler/network/ConnectToRouter$UltraFastRefreshResult;", "", "previousIp", "", "currentIp", "changed", "", "<init>", "(Ljava/lang/String;Ljava/lang/String;Z)V", "getPreviousIp", "()Ljava/lang/String;", "getCurrentIp", "getChanged", "()Z", "component1", "component2", "component3", "copy", "equals", "other", "hashCode", "", "toString", "AsdUnion"}
   )
   public static final class UltraFastRefreshResult {
      @NotNull
      private final String previousIp;
      @NotNull
      private final String currentIp;
      private final boolean changed;

      public UltraFastRefreshResult(@NotNull String previousIp, @NotNull String currentIp, boolean changed) {
         Intrinsics.checkNotNullParameter(previousIp, "previousIp");
         Intrinsics.checkNotNullParameter(currentIp, "currentIp");
         super();
         this.previousIp = previousIp;
         this.currentIp = currentIp;
         this.changed = changed;
      }

      @NotNull
      public final String getPreviousIp() {
         return this.previousIp;
      }

      @NotNull
      public final String getCurrentIp() {
         return this.currentIp;
      }

      public final boolean getChanged() {
         return this.changed;
      }

      @NotNull
      public final String component1() {
         return this.previousIp;
      }

      @NotNull
      public final String component2() {
         return this.currentIp;
      }

      public final boolean component3() {
         return this.changed;
      }

      @NotNull
      public final ConnectToRouter.UltraFastRefreshResult copy(@NotNull String previousIp, @NotNull String currentIp, boolean changed) {
         Intrinsics.checkNotNullParameter(previousIp, "previousIp");
         Intrinsics.checkNotNullParameter(currentIp, "currentIp");
         return new ConnectToRouter.UltraFastRefreshResult(previousIp, currentIp, changed);
      }

      // $FF: synthetic method
      public static ConnectToRouter.UltraFastRefreshResult copy$default(ConnectToRouter.UltraFastRefreshResult var0, String var1, String var2, boolean var3, int var4, Object var5) {
         if((var4 & 1) != 0) {
            var1 = var0.previousIp;
         }

         if((var4 & 2) != 0) {
            var2 = var0.currentIp;
         }

         if((var4 & 4) != 0) {
            var3 = var0.changed;
         }

         return var0.copy(var1, var2, var3);
      }

      @NotNull
      public String toString() {
         return "UltraFastRefreshResult(previousIp=" + this.previousIp + ", currentIp=" + this.currentIp + ", changed=" + this.changed + ')';
      }

      public int hashCode() {
         int result = this.previousIp.hashCode();
         result = result * 31 + this.currentIp.hashCode();
         result = result * 31 + Boolean.hashCode(this.changed);
         return result;
      }

      public boolean equals(@Nullable Object other) {
         if(this == other) {
            return true;
         } else if(!(other instanceof ConnectToRouter.UltraFastRefreshResult)) {
            return false;
         } else {
            ConnectToRouter.UltraFastRefreshResult var2 = (ConnectToRouter.UltraFastRefreshResult)other;
            return !Intrinsics.areEqual(this.previousIp, var2.previousIp)?false:(!Intrinsics.areEqual(this.currentIp, var2.currentIp)?false:this.changed == var2.changed);
         }
      }
   }
}

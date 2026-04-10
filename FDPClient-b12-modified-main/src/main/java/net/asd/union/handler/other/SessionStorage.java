package net.asd.union.handler.other;

import java.util.UUID;
import kotlin.Metadata;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import kotlin.jvm.internal.Intrinsics;
import kotlin.jvm.internal.SourceDebugExtension;
import kotlin.text.Charsets;
import kotlin.text.StringsKt;
import net.asd.union.event.EventHook;
import net.asd.union.event.EventManager;
import net.asd.union.event.Listenable;
import net.asd.union.event.SessionUpdateEvent;
import net.asd.union.event.EventHook.Blocking;
import net.asd.union.event.Listenable.DefaultImpls;
import net.asd.union.file.FileConfig;
import net.asd.union.file.FileManager;
import net.asd.union.utils.client.MinecraftInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Session;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(
   mv = {2, 0, 0},
   k = 1,
   xi = 48,
   d1 = {"\u0000&\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0005\n\u0002\u0010\u0002\n\u0002\b\u0006\n\u0002\u0010\u000b\n\u0000\b\u00c6\u0002\u0018\u00002\u00020\u00012\u00020\u0002B\t\b\u0002\u00a2\u0006\u0004\b\u0003\u0010\u0004J\u0006\u0010\u000b\u001a\u00020\fJ\u0006\u0010\r\u001a\u00020\u0006J\b\u0010\u0012\u001a\u00020\u0013H\u0016R\u001a\u0010\u0005\u001a\u00020\u0006X\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0007\u0010\b\"\u0004\b\t\u0010\nR\u0013\u0010\u000e\u001a\u00020\f\u00a2\u0006\n\n\u0002\u0010\u0011\u001a\u0004\b\u000f\u0010\u0010\u00a8\u0006\u0014"},
   d2 = {"Lnet/asd/union/handler/other/SessionStorage;", "Lnet/asd/union/event/Listenable;", "Lnet/asd/union/utils/client/MinecraftInstance;", "<init>", "()V", "lastUsername", "", "getLastUsername", "()Ljava/lang/String;", "setLastUsername", "(Ljava/lang/String;)V", "applySavedUsername", "", "getUsernameForSave", "onSessionUpdate", "getOnSessionUpdate", "()Lkotlin/Unit;", "Lkotlin/Unit;", "handleEvents", "", "AsdUnion"}
)
@SourceDebugExtension({"SMAP\nSessionStorage.kt\nKotlin\n*S Kotlin\n*F\n+ 1 SessionStorage.kt\nnet/asd/union/handler/other/SessionStorage\n+ 2 Listenable.kt\nnet/asd/union/event/ListenableKt\n*L\n1#1,47:1\n22#2,7:48\n*S KotlinDebug\n*F\n+ 1 SessionStorage.kt\nnet/asd/union/handler/other/SessionStorage\n*L\n37#1:48,7\n*E\n"})
public final class SessionStorage implements Listenable, MinecraftInstance {
   @NotNull
   public static final SessionStorage INSTANCE = new SessionStorage();
   @NotNull
   private static String lastUsername = "";
   @NotNull
   private static final Unit onSessionUpdate = Unit.INSTANCE;

   @NotNull
   public final String getLastUsername() {
      return lastUsername;
   }

   public final void setLastUsername(@NotNull String value) {
      Intrinsics.checkNotNullParameter(value, "value");
      lastUsername = value;
   }

   public final void applySavedUsername() {
      String username = StringsKt.trim((CharSequence)lastUsername).toString();
      if(((CharSequence)username).length() != 0) {
         String var6;
         label34: {
            Session var10000 = this.getMc().field_71449_j;
            if(var10000 != null) {
               String var5 = var10000.func_111285_a();
               if(var5 != null) {
                  var6 = StringsKt.trim((CharSequence)var5).toString();
                  break label34;
               }
            }

            var6 = null;
         }

         if(var6 == null) {
            var6 = "";
         }

         String currentUsername = var6;
         if(!Intrinsics.areEqual(currentUsername, username)) {
            String var4 = "OfflinePlayer:" + username;
            byte[] var7 = var4.getBytes(Charsets.UTF_8);
            Intrinsics.checkNotNullExpressionValue(var7, "getBytes(...)");
            UUID uuid = UUID.nameUUIDFromBytes(var7);
            this.getMc().field_71449_j = new Session(username, uuid.toString(), "-", "legacy");
         }
      }
   }

   @NotNull
   public final String getUsernameForSave() {
      String var3;
      label0: {
         Session var10000 = this.getMc().field_71449_j;
         if(var10000 != null) {
            String var2 = var10000.func_111285_a();
            if(var2 != null) {
               var3 = StringsKt.trim((CharSequence)var2).toString();
               break label0;
            }
         }

         var3 = null;
      }

      if(var3 == null) {
         var3 = "";
      }

      String currentUsername = var3;
      if(((CharSequence)currentUsername).length() > 0) {
         lastUsername = currentUsername;
         return currentUsername;
      } else {
         return lastUsername;
      }
   }

   @NotNull
   public final Unit getOnSessionUpdate() {
      return onSessionUpdate;
   }

   public boolean handleEvents() {
      return true;
   }

   public void unregister() {
      DefaultImpls.unregister(this);
   }

   @NotNull
   public Listenable[] getSubListeners() {
      return DefaultImpls.getSubListeners(this);
   }

   @Nullable
   public Listenable getParent() {
      return DefaultImpls.getParent(this);
   }

   @NotNull
   public Minecraft getMc() {
      return net.asd.union.utils.client.MinecraftInstance.DefaultImpls.getMc(this);
   }

   private static final Unit onSessionUpdate$lambda$0(SessionUpdateEvent it) {
      String var3;
      label0: {
         Intrinsics.checkNotNullParameter(it, "it");
         Session var10000 = INSTANCE.getMc().field_71449_j;
         if(var10000 != null) {
            String var2 = var10000.func_111285_a();
            if(var2 != null) {
               var3 = StringsKt.trim((CharSequence)var2).toString();
               break label0;
            }
         }

         var3 = null;
      }

      if(var3 == null) {
         var3 = "";
      }

      String username = var3;
      if(((CharSequence)username).length() == 0) {
         return Unit.INSTANCE;
      } else {
         SessionStorage var4 = INSTANCE;
         lastUsername = username;
         FileManager.saveConfig$default(FileManager.INSTANCE, (FileConfig)FileManager.INSTANCE.getValuesConfig(), false, 2, (Object)null);
         return Unit.INSTANCE;
      }
   }

   static {
      Listenable $this$handler_u24default$iv = (Listenable)INSTANCE;
      Function1 action$iv = SessionStorage::onSessionUpdate$lambda$0;
      boolean always$iv = false;
      byte priority$iv = 0;
      int $i$f$handler = 0;
      EventManager.INSTANCE.registerEventHook(SessionUpdateEvent.class, (EventHook)(new Blocking($this$handler_u24default$iv, always$iv, priority$iv, action$iv)));
   }
}

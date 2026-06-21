package net.minecraft.client;

import com.google.common.collect.Maps;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public class KeyMapping implements Comparable<KeyMapping>, net.minecraftforge.client.extensions.IForgeKeyMapping {
    private static final Map<String, KeyMapping> ALL = Maps.newHashMap();
    private static final net.minecraftforge.client.settings.KeyMappingLookup MAP = new net.minecraftforge.client.settings.KeyMappingLookup();
    private final String name;
    private final InputConstants.Key defaultKey;
    private final KeyMapping.Category category;
    protected InputConstants.Key key;
    boolean isDown;
    private int clickCount;
    private final int order;

    public static void click(final InputConstants.Key key) {
        forAllKeyMappings(key, keyMapping -> keyMapping.clickCount++);
    }

    public static void set(final InputConstants.Key key, final boolean state) {
        forAllKeyMappings(key, keyMapping -> keyMapping.setDown(state));
    }

    private static void forAllKeyMappings(final InputConstants.Key key, final Consumer<KeyMapping> operation) {
        List<KeyMapping> keyMappings = MAP.getAll(key);
        if (keyMappings != null && !keyMappings.isEmpty()) {
            for (KeyMapping keyMapping : keyMappings) {
                operation.accept(keyMapping);
            }
        }
    }

    public static void setAll() {
        Window window = Minecraft.getInstance().getWindow();

        for (KeyMapping keyMapping : ALL.values()) {
            if (keyMapping.shouldSetOnIngameFocus()) {
                keyMapping.setDown(InputConstants.isKeyDown(window, keyMapping.key.getValue()));
            }
        }
    }

    public static void releaseAll() {
        for (KeyMapping keyMapping : ALL.values()) {
            keyMapping.release();
        }
    }

    public static void restoreToggleStatesOnScreenClosed() {
        for (KeyMapping keyMapping : ALL.values()) {
            if (keyMapping instanceof ToggleKeyMapping toggleKeyMapping && toggleKeyMapping.shouldRestoreStateOnScreenClosed()) {
                toggleKeyMapping.setDown(true);
            }
        }
    }

    public static void resetToggleKeys() {
        for (KeyMapping keyMapping : ALL.values()) {
            if (keyMapping instanceof ToggleKeyMapping toggleKeyMapping) {
                toggleKeyMapping.reset();
            }
        }
    }

    public static void resetMapping() {
        MAP.clear();

        for (KeyMapping keyMapping : ALL.values()) {
            keyMapping.registerMapping(keyMapping.key);
        }
    }

    public KeyMapping(final String name, final int keysym, final KeyMapping.Category category) {
        this(name, InputConstants.Type.KEYSYM, keysym, category);
    }

    public KeyMapping(final String name, final InputConstants.Type type, final int value, final KeyMapping.Category category) {
        this(name, type, value, category, 0);
    }

    public KeyMapping(final String name, final InputConstants.Type type, final int value, final KeyMapping.Category category, final int order) {
        this.name = name;
        this.key = type.getOrCreate(value);
        this.defaultKey = this.key;
        this.category = category;
        this.order = order;
        ALL.put(name, this);
        this.registerMapping(this.key);
    }

    public boolean isDown() {
        return this.isDown && isConflictContextAndModifierActive();
    }

    public KeyMapping.Category getCategory() {
        return this.category;
    }

    public boolean consumeClick() {
        if (this.clickCount == 0) {
            return false;
        } else {
            this.clickCount--;
            return true;
        }
    }

    protected void release() {
        this.clickCount = 0;
        this.setDown(false);
    }

    protected boolean shouldSetOnIngameFocus() {
        return this.key.getType() == InputConstants.Type.KEYSYM && this.key.getValue() != InputConstants.UNKNOWN.getValue();
    }

    public String getName() {
        return this.name;
    }

    public InputConstants.Key getDefaultKey() {
        return this.defaultKey;
    }

    public void setKey(final InputConstants.Key key) {
        this.key = key;
    }

    public int compareTo(final KeyMapping o) {
        if (this.category == o.category) {
            return this.order == o.order ? I18n.get(this.name).compareTo(I18n.get(o.name)) : Integer.compare(this.order, o.order);
        } else {
            return compareSort(this.category, o.category);
        }
    }

    private static int compareSort(Category c1, Category c2) {
        int o1 = KeyMapping.Category.SORT_ORDER.indexOf(c1);
        int o2 = KeyMapping.Category.SORT_ORDER.indexOf(c2);
        if (o1 == -1 && o2 != -1) return 1;
        if (o1 != -1 && o2 == -1) return -1;
        if (o1 == -1 && o2 == -1) return I18n.get(c1.id().toLanguageKey("key.category")).compareTo(I18n.get(c1.id().toLanguageKey("key.category")));
        return  o1 - o2;
    }

    public static Supplier<Component> createNameSupplier(final String key) {
        KeyMapping map = ALL.get(key);
        return map == null ? () -> Component.translatable(key) : map::getTranslatedKeyMessage;
    }

    public boolean same(final KeyMapping that) {
        if (getKeyConflictContext().conflicts(that.getKeyConflictContext()) || that.getKeyConflictContext().conflicts(getKeyConflictContext())) {
            var keyModifier = getKeyModifier();
            var otherKeyModifier = that.getKeyModifier();
            if (keyModifier.matches(that.getKey()) || otherKeyModifier.matches(getKey())) {
               return true;
            } else if (getKey().equals(that.getKey())) {
               // IN_GAME key contexts have a conflict when at least one modifier is NONE.
               // For example: If you hold shift to crouch, you can still press E to open your inventory. This means that a Shift+E hotkey is in conflict with E.
               // GUI and other key contexts do not have this limitation.
               return keyModifier == otherKeyModifier ||
                  (getKeyConflictContext().conflicts(net.minecraftforge.client.settings.KeyConflictContext.IN_GAME) &&
                  (keyModifier == net.minecraftforge.client.settings.KeyModifier.NONE || otherKeyModifier == net.minecraftforge.client.settings.KeyModifier.NONE));
            }
         }
        return this.key.equals(that.key);
    }

    public boolean isUnbound() {
        return this.key.equals(InputConstants.UNKNOWN);
    }

    public boolean matches(final KeyEvent event) {
        return event.key() == InputConstants.UNKNOWN.getValue()
            ? this.key.getType() == InputConstants.Type.SCANCODE && this.key.getValue() == event.scancode()
            : this.key.getType() == InputConstants.Type.KEYSYM && this.key.getValue() == event.key();
    }

    public boolean matchesMouse(final MouseButtonEvent event) {
        return this.key.getType() == InputConstants.Type.MOUSE && this.key.getValue() == event.button();
    }

    public Component getTranslatedKeyMessage() {
        return getKeyModifier().getCombinedName(key, () -> {
        return this.key.getDisplayName();
        });
    }

    public boolean isDefault() {
        return this.key.equals(this.defaultKey) && getKeyModifier() == getDefaultKeyModifier();
    }

    public String saveString() {
        return this.key.getName();
    }

    public void setDown(final boolean down) {
        this.isDown = down;
    }

    private void registerMapping(final InputConstants.Key key) {
        MAP.put(key, this);
    }

    public static @Nullable KeyMapping get(final String name) {
        return ALL.get(name);
    }

    private net.minecraftforge.client.settings.KeyModifier keyModifierDefault = net.minecraftforge.client.settings.KeyModifier.NONE;
    private net.minecraftforge.client.settings.KeyModifier keyModifier = net.minecraftforge.client.settings.KeyModifier.NONE;
    private net.minecraftforge.client.settings.IKeyConflictContext keyConflictContext = net.minecraftforge.client.settings.KeyConflictContext.UNIVERSAL;

    /**
     * Convenience constructor for creating KeyBindings with keyConflictContext set.
     */
    public KeyMapping(String description, net.minecraftforge.client.settings.IKeyConflictContext keyConflictContext, final InputConstants.Type inputType, final int keyCode, Category category, int order) {
        this(description, keyConflictContext, inputType.getOrCreate(keyCode), category, order);
    }

    /**
     * Convenience constructor for creating KeyBindings with keyConflictContext set.
     */
    public KeyMapping(String description, net.minecraftforge.client.settings.IKeyConflictContext keyConflictContext, InputConstants.Key keyCode, Category category, int order) {
        this(description, keyConflictContext, net.minecraftforge.client.settings.KeyModifier.NONE, keyCode, category, order);
    }

    /**
     * Convenience constructor for creating KeyBindings with keyConflictContext and keyModifier set.
     */
    public KeyMapping(String description, net.minecraftforge.client.settings.IKeyConflictContext keyConflictContext, net.minecraftforge.client.settings.KeyModifier keyModifier, final InputConstants.Type inputType, final int keyCode, Category category, int order) {
        this(description, keyConflictContext, keyModifier, inputType.getOrCreate(keyCode), category, order);
    }

    /**
     * Convenience constructor for creating KeyBindings with keyConflictContext and keyModifier set.
     */
    public KeyMapping(String description, net.minecraftforge.client.settings.IKeyConflictContext keyConflictContext, net.minecraftforge.client.settings.KeyModifier keyModifier, InputConstants.Key keyCode, Category category, int order) {
       this.name = description;
       this.key = keyCode;
       this.defaultKey = keyCode;
       this.category = category;
       this.keyConflictContext = keyConflictContext;
       this.keyModifier = keyModifier;
       this.keyModifierDefault = keyModifier;
       this.order = order;
       if (this.keyModifier.matches(keyCode))
          this.keyModifier = net.minecraftforge.client.settings.KeyModifier.NONE;
       ALL.put(description, this);
       MAP.put(keyCode, this);
    }

    @Override
    public InputConstants.Key getKey() {
        return this.key;
    }

    @Override
    public void setKeyConflictContext(net.minecraftforge.client.settings.IKeyConflictContext keyConflictContext) {
        this.keyConflictContext = keyConflictContext;
    }

    @Override
    public net.minecraftforge.client.settings.IKeyConflictContext getKeyConflictContext() {
        return keyConflictContext;
    }

    @Override
    public net.minecraftforge.client.settings.KeyModifier getDefaultKeyModifier() {
        return keyModifierDefault;
    }

    @Override
    public net.minecraftforge.client.settings.KeyModifier getKeyModifier() {
        return keyModifier;
    }

    @Override
    public void setKeyModifierAndCode(@org.jetbrains.annotations.Nullable net.minecraftforge.client.settings.KeyModifier keyModifier, InputConstants.Key keyCode) {
        MAP.remove(this);

        if (keyModifier == null)
            keyModifier = net.minecraftforge.client.settings.KeyModifier.getModifier(this.key);
        if (keyModifier == null || keyCode == InputConstants.UNKNOWN || net.minecraftforge.client.settings.KeyModifier.isKeyCodeModifier(keyCode))
            keyModifier = net.minecraftforge.client.settings.KeyModifier.NONE;

        this.key = keyCode;
        this.keyModifier = keyModifier;

        MAP.put(keyCode, this);
    }

    @OnlyIn(Dist.CLIENT)
    public record Category(Identifier id) {
        private static final List<KeyMapping.Category> SORT_ORDER = new ArrayList<>();
        public static final KeyMapping.Category MOVEMENT = register("movement");
        public static final KeyMapping.Category MISC = register("misc");
        public static final KeyMapping.Category MULTIPLAYER = register("multiplayer");
        public static final KeyMapping.Category GAMEPLAY = register("gameplay");
        public static final KeyMapping.Category INVENTORY = register("inventory");
        public static final KeyMapping.Category CREATIVE = register("creative");
        public static final KeyMapping.Category SPECTATOR = register("spectator");
        public static final KeyMapping.Category DEBUG = register("debug");

        private static KeyMapping.Category register(final String name) {
            return register(Identifier.withDefaultNamespace(name));
        }

        public static KeyMapping.Category register(final Identifier id) {
            KeyMapping.Category category = new KeyMapping.Category(id);
            if (SORT_ORDER.contains(category)) {
                throw new IllegalArgumentException(String.format(Locale.ROOT, "Category '%s' is already registered.", id));
            } else {
                SORT_ORDER.add(category);
                return category;
            }
        }

        public Component label() {
            return Component.translatable(this.id.toLanguageKey("key.category"));
        }
    }
}

package net.minecraft.network.protocol.login.custom;

import net.minecraft.network.FriendlyByteBuf;

public record DiscardedQueryAnswerPayload(
    @javax.annotation.Nullable FriendlyByteBuf data,
    java.util.function.Consumer<FriendlyByteBuf> encoder
) implements CustomQueryAnswerPayload {
    public static final DiscardedQueryAnswerPayload INSTANCE = new DiscardedQueryAnswerPayload();

    public DiscardedQueryAnswerPayload() {
        this(null, buf -> {});
    }

    @Override
    public void write(final FriendlyByteBuf output) {
        if (this.data != null) {
            output.writeBytes(this.data.slice());
        } else {
            encoder.accept(output);
        }
    }
}

package net.asd.union.injection.forge.mixins.network;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.network.PacketBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.nio.charset.StandardCharsets;

@Mixin(PacketBuffer.class)
public abstract class MixinPacketBuffer extends ByteBuf {

    @Shadow
    private ByteBuf buf;

    @Shadow
    public abstract int readVarIntFromBuffer();

    @Shadow
    public abstract void writeVarIntToBuffer(int input);

    private static int utf8MaxBytes(int charCount) {
        return charCount * 3;
    }

    private static int utf8MaxBytes(CharSequence sequence) {
        return sequence.length() * 3;
    }

    private static int writeUtf8(ByteBuf out, CharSequence sequence) {
        int len = sequence.length();
        int bytesWritten = 0;

        for (int i = 0; i < len; i++) {
            char c = sequence.charAt(i);

            if (c < 0x80) {
                out.writeByte(c);
                bytesWritten++;
            } else if (c < 0x800) {
                out.writeByte((c >> 6) | 0xC0);
                out.writeByte((c & 0x3F) | 0x80);
                bytesWritten += 2;
            } else if (Character.isSurrogate(c)) {
                if (Character.isHighSurrogate(c) && i + 1 < len) {
                    char next = sequence.charAt(i + 1);
                    if (Character.isLowSurrogate(next)) {
                        int codePoint = Character.toCodePoint(c, next);
                        out.writeByte((codePoint >> 18) | 0xF0);
                        out.writeByte(((codePoint >> 12) & 0x3F) | 0x80);
                        out.writeByte(((codePoint >> 6) & 0x3F) | 0x80);
                        out.writeByte((codePoint & 0x3F) | 0x80);
                        bytesWritten += 4;
                        i++;
                        continue;
                    }
                }
                out.writeByte(0x3F);
                bytesWritten++;
            } else {
                out.writeByte((c >> 12) | 0xE0);
                out.writeByte(((c >> 6) & 0x3F) | 0x80);
                out.writeByte((c & 0x3F) | 0x80);
                bytesWritten += 3;
            }
        }

        return bytesWritten;
    }

    @Overwrite
    public String readStringFromBuffer(int maxLength) {
        int maxEncodedLength = utf8MaxBytes(maxLength);
        int bufferLength = this.readVarIntFromBuffer();

        if (bufferLength > maxEncodedLength) {
            throw new DecoderException("The received encoded string buffer length is longer than maximum allowed ("
                    + bufferLength + " > " + maxEncodedLength + ")");
        } else if (bufferLength < 0) {
            throw new DecoderException("The received encoded string buffer length is less than zero! Weird string!");
        } else {
            int availableBytes = this.buf.readableBytes();
            if (bufferLength > availableBytes) {
                throw new DecoderException("Not enough bytes in buffer, expected " + bufferLength
                        + ", but got " + availableBytes);
            } else {
                String result = this.buf.toString(this.buf.readerIndex(), bufferLength, StandardCharsets.UTF_8);
                this.buf.readerIndex(this.buf.readerIndex() + bufferLength);
                if (result.length() > maxLength) {
                    throw new DecoderException("The received string length is longer than maximum allowed ("
                            + result.length() + " > " + maxLength + ")");
                } else {
                    return result;
                }
            }
        }
    }

    @Overwrite
    public PacketBuffer writeString(String string) {
        int maxEncodedLength = utf8MaxBytes(string);
        ByteBuf tmp = this.buf.alloc().buffer(maxEncodedLength);

        try {
            int bytesWritten = writeUtf8(tmp, string);
            int maxAllowedEncodedLength = utf8MaxBytes(32767);
            if (bytesWritten > maxAllowedEncodedLength) {
                throw new EncoderException("String too big (was " + bytesWritten
                        + " bytes encoded, max " + maxAllowedEncodedLength + ")");
            }

            this.writeVarIntToBuffer(bytesWritten);
            this.buf.writeBytes(tmp);
        } finally {
            tmp.release();
        }

        return (PacketBuffer) (Object) this;
    }
}

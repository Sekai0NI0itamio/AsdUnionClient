package net.asd.union.handler.network.optimized;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

import javax.crypto.Cipher;
import javax.crypto.ShortBufferException;

public class OptimizedEncryptionEncoder extends MessageToByteEncoder<ByteBuf> {

    private final OptimizedCipherCodec codec;

    public OptimizedEncryptionEncoder(Cipher cipher) {
        this.codec = new OptimizedCipherCodec(cipher);
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, ByteBuf in, ByteBuf out) throws ShortBufferException, Exception {
        this.codec.encipher(in, out);
    }
}

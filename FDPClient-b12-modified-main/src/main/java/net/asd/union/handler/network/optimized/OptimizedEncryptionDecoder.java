package net.asd.union.handler.network.optimized;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;

import javax.crypto.Cipher;
import javax.crypto.ShortBufferException;
import java.util.List;

public class OptimizedEncryptionDecoder extends MessageToMessageDecoder<ByteBuf> {

    private final OptimizedCipherCodec codec;

    public OptimizedEncryptionDecoder(Cipher cipher) {
        this.codec = new OptimizedCipherCodec(cipher);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws ShortBufferException, Exception {
        out.add(this.codec.decipher(ctx, in));
    }
}

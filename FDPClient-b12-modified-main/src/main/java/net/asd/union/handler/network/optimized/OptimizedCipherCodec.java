package net.asd.union.handler.network.optimized;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;

import javax.crypto.Cipher;
import javax.crypto.ShortBufferException;

public class OptimizedCipherCodec {

    private final Cipher cipher;
    private byte[] heapIn = new byte[0];
    private byte[] heapOut = new byte[0];

    public OptimizedCipherCodec(Cipher cipher) {
        this.cipher = cipher;
    }

    private byte[] bufToBytes(ByteBuf in) {
        int readableBytes = in.readableBytes();
        if (this.heapIn.length < readableBytes) {
            this.heapIn = new byte[readableBytes];
        }
        in.readBytes(this.heapIn, 0, readableBytes);
        return this.heapIn;
    }

    public ByteBuf decipher(ChannelHandlerContext ctx, ByteBuf in) throws ShortBufferException {
        int readableBytes = in.readableBytes();
        byte[] heapIn = this.bufToBytes(in);
        ByteBuf out = ctx.alloc().heapBuffer(this.cipher.getOutputSize(readableBytes));
        out.writerIndex(this.cipher.update(heapIn, 0, readableBytes, out.array(), out.arrayOffset()));
        return out;
    }

    public void encipher(ByteBuf in, ByteBuf out) throws ShortBufferException {
        int readableBytes = in.readableBytes();
        byte[] heapIn = this.bufToBytes(in);
        int outputSize = this.cipher.getOutputSize(readableBytes);
        if (this.heapOut.length < outputSize) {
            this.heapOut = new byte[outputSize];
        }
        out.writeBytes(this.heapOut, 0, this.cipher.update(heapIn, 0, readableBytes, this.heapOut));
    }
}

package net.asd.union.handler.network.optimized;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.PacketBuffer;

import java.util.List;
import java.util.zip.Inflater;

public class OptimizedCompressionDecoder extends ByteToMessageDecoder {

    private static final int MAX_COMPRESSED_SIZE = 2097152;

    private final Inflater inflater = new Inflater();
    private byte[] inputBuffer = new byte[8192];
    private byte[] outputBuffer = new byte[65536];
    private int threshold;

    public OptimizedCompressionDecoder(int threshold) {
        this.threshold = threshold;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (in.readableBytes() == 0) {
            return;
        }

        PacketBuffer packetBuffer = new PacketBuffer(in);
        int uncompressedSize = packetBuffer.readVarIntFromBuffer();

        if (uncompressedSize == 0) {
            out.add(packetBuffer.readBytes(packetBuffer.readableBytes()));
            return;
        }

        if (uncompressedSize < this.threshold) {
            throw new DecoderException("Badly compressed packet - size of " + uncompressedSize
                    + " is below server threshold of " + this.threshold);
        }

        if (uncompressedSize > MAX_COMPRESSED_SIZE) {
            throw new DecoderException("Badly compressed packet - size of " + uncompressedSize
                    + " is larger than protocol maximum of " + MAX_COMPRESSED_SIZE);
        }

        int compressedSize = in.readableBytes();

        if (this.inputBuffer.length < compressedSize) {
            this.inputBuffer = new byte[compressedSize];
        }

        in.readBytes(this.inputBuffer, 0, compressedSize);
        this.inflater.setInput(this.inputBuffer, 0, compressedSize);

        if (this.outputBuffer.length < uncompressedSize) {
            this.outputBuffer = new byte[uncompressedSize];
        }

        int resultLength = this.inflater.inflate(this.outputBuffer);
        this.inflater.reset();

        if (resultLength != uncompressedSize) {
            throw new DecoderException("Decompressed size mismatch: expected " + uncompressedSize
                    + " but got " + resultLength);
        }

        out.add(Unpooled.wrappedBuffer(this.outputBuffer, 0, uncompressedSize));
    }

    public void setCompressionThreshold(int threshold) {
        this.threshold = threshold;
    }
}

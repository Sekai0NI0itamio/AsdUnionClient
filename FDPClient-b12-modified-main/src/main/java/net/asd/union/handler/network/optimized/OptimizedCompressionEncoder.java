package net.asd.union.handler.network.optimized;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import net.minecraft.network.PacketBuffer;

import java.util.zip.Deflater;

public class OptimizedCompressionEncoder extends MessageToByteEncoder<ByteBuf> {

    private static final int BUFFER_INITIAL_SIZE = 65536;

    private final Deflater deflater = new Deflater();
    private byte[] inputBuffer = new byte[BUFFER_INITIAL_SIZE];
    private byte[] outputBuffer = new byte[BUFFER_INITIAL_SIZE];
    private int threshold;

    public OptimizedCompressionEncoder(int threshold) {
        this.threshold = threshold;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, ByteBuf in, ByteBuf out) throws Exception {
        int length = in.readableBytes();
        PacketBuffer packetBuffer = new PacketBuffer(out);

        if (length < this.threshold) {
            packetBuffer.writeVarIntToBuffer(0);
            packetBuffer.writeBytes(in);
            return;
        }

        if (this.inputBuffer.length < length) {
            this.inputBuffer = new byte[length];
        }

        in.readBytes(this.inputBuffer, 0, length);

        packetBuffer.writeVarIntToBuffer(length);

        this.deflater.setInput(this.inputBuffer, 0, length);
        this.deflater.finish();

        while (!this.deflater.finished()) {
            int written = this.deflater.deflate(this.outputBuffer);
            if (written > 0) {
                packetBuffer.writeBytes(this.outputBuffer, 0, written);
            }
        }

        this.deflater.reset();
    }

    public void setCompressionThreshold(int threshold) {
        this.threshold = threshold;
    }
}

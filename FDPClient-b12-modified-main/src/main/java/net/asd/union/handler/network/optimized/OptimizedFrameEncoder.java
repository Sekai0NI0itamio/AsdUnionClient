package net.asd.union.handler.network.optimized;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import net.minecraft.network.PacketBuffer;

public class OptimizedFrameEncoder extends MessageToByteEncoder<ByteBuf> {

    @Override
    protected void encode(ChannelHandlerContext ctx, ByteBuf in, ByteBuf out) throws Exception {
        int length = in.readableBytes();
        int varIntSize = PacketBuffer.getVarIntSize(length);

        if (varIntSize > 3) {
            throw new IllegalArgumentException("unable to fit " + length + " into " + 3);
        }

        PacketBuffer packetBuffer = new PacketBuffer(out);
        packetBuffer.ensureWritable(varIntSize + length);
        packetBuffer.writeVarIntToBuffer(length);
        packetBuffer.writeBytes(in, in.readerIndex(), length);
    }
}

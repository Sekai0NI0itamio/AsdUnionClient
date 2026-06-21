package net.asd.union.handler.network.optimized;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.CorruptedFrameException;
import net.minecraft.network.PacketBuffer;

import java.util.List;

public class OptimizedFrameDecoder extends ByteToMessageDecoder {

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        in.markReaderIndex();

        int length = 0;
        int bytes = 0;

        while (true) {
            if (!in.isReadable()) {
                in.resetReaderIndex();
                return;
            }

            byte b = in.readByte();
            length |= (b & 0x7F) << bytes++ * 7;

            if (bytes > 3) {
                throw new CorruptedFrameException("length wider than 21-bit");
            }

            if ((b & 0x80) == 0) {
                break;
            }
        }

        if (in.readableBytes() < length) {
            in.resetReaderIndex();
            return;
        }

        out.add(in.readBytes(length));
    }
}

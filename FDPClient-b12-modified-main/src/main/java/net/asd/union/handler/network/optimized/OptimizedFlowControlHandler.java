package net.asd.union.handler.network.optimized;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelConfig;

public class OptimizedFlowControlHandler extends ChannelInboundHandlerAdapter {

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (ctx.channel().isWritable()) {
            super.channelRead(ctx, msg);
        } else {
            try {
                super.channelRead(ctx, msg);
            } finally {
                ChannelConfig config = ctx.channel().config();
                if (!config.isAutoRead()) {
                    config.setAutoRead(true);
                }
            }
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        if (!ctx.channel().isWritable()) {
            ctx.channel().config().setAutoRead(false);
        }
        ctx.fireChannelReadComplete();
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        if (ctx.channel().isWritable()) {
            ctx.channel().config().setAutoRead(true);
        }
        ctx.fireChannelWritabilityChanged();
    }
}

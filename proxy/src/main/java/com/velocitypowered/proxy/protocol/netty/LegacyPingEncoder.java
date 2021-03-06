package com.velocitypowered.proxy.protocol.netty;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.velocitypowered.proxy.protocol.packet.LegacyPingResponse;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

import java.nio.charset.StandardCharsets;
import java.util.List;

@ChannelHandler.Sharable
public class LegacyPingEncoder extends MessageToByteEncoder<LegacyPingResponse> {
    public static final LegacyPingEncoder INSTANCE = new LegacyPingEncoder();

    private LegacyPingEncoder() {}

    @Override
    protected void encode(ChannelHandlerContext ctx, LegacyPingResponse msg, ByteBuf out) throws Exception {
        out.writeByte(0xff);
        String serializedResponse = serialize(msg);
        out.writeShort(serializedResponse.length());
        out.writeBytes(serializedResponse.getBytes(StandardCharsets.UTF_16BE));
    }

    private String serialize(LegacyPingResponse response) {
        List<String> parts = ImmutableList.of(
                "§1",
                Integer.toString(response.getProtocolVersion()),
                response.getServerVersion(),
                response.getMotd(),
                Integer.toString(response.getPlayersOnline()),
                Integer.toString(response.getPlayersMax())
        );
        return Joiner.on('\0').join(parts);
    }
}

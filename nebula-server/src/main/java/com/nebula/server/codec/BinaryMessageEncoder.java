package com.nebula.server.codec;

import com.nebula.common.protocol.Message;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

/**
 * 二进制消息编码器
 * 格式: [Magic(2)] [Type(1)] [Length(4)] [Body(JSON)]
 */
public class BinaryMessageEncoder extends MessageToByteEncoder<Message> {
    
    private static final short MAGIC_NUMBER = (short) 0xCAFE;
    
    @Override
    protected void encode(ChannelHandlerContext ctx, Message msg, ByteBuf out) throws Exception {
        // 获取消息体
        String body = msg.getBody();
        byte[] bodyBytes = body != null ? body.getBytes("UTF-8") : new byte[0];
        
        // 写入 Magic Number (2 字节)
        out.writeShort(MAGIC_NUMBER);
        
        // 写入消息类型 (1 字节)
        out.writeByte(msg.getMessageType().getValue());
        
        // 写入消息体长度 (4 字节)
        out.writeInt(bodyBytes.length);
        
        // 写入消息体
        out.writeBytes(bodyBytes);
    }
}

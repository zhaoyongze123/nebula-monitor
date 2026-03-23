package com.nebula.server.codec;

import com.nebula.common.protocol.Message;
import com.nebula.common.protocol.MessageType;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

/**
 * 二进制消息解码器
 * 格式: [Magic(2)] [Type(1)] [Length(4)] [Body(JSON)]
 */
public class BinaryMessageDecoder extends ByteToMessageDecoder {
    
    private static final short MAGIC_NUMBER = (short) 0xCAFE;
    private static final int HEADER_SIZE = 7; // Magic(2) + Type(1) + Length(4)
    
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        // 检查是否有足够的字节读取头部
        if (in.readableBytes() < HEADER_SIZE) {
            return;
        }
        
        // 标记读位置
        in.markReaderIndex();
        
        // 读取 Magic Number (2 字节)
        short magic = in.readShort();
        if (magic != MAGIC_NUMBER) {
            ctx.close();
            throw new IllegalStateException("Invalid magic number: 0x" + Integer.toHexString(magic));
        }
        
        // 读取消息类型 (1 字节)
        byte typeValue = in.readByte();
        MessageType messageType = MessageType.fromValue(typeValue);
        
        // 读取消息体长度 (4 字节)
        int bodyLength = in.readInt();
        
        // 检查是否有足够的字节读取完整的消息体
        if (in.readableBytes() < bodyLength) {
            in.resetReaderIndex();
            return;
        }
        
        // 读取消息体
        byte[] bodyBytes = new byte[bodyLength];
        in.readBytes(bodyBytes);
        String body = new String(bodyBytes, "UTF-8");
        
        // 组装消息对象
        Message message = new Message(messageType, body);
        out.add(message);
    }
}

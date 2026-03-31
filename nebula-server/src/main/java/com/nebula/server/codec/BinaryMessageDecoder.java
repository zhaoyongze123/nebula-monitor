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
 * 
 * 注意：当 Magic Number 不匹配时，跳过当前消息让后续解码器处理（向后兼容）
 */
public class BinaryMessageDecoder extends ByteToMessageDecoder {
    
    private static final short MAGIC_NUMBER = (short) 0xCAFE;
    private static final int HEADER_SIZE = 7; // Magic(2) + Type(1) + Length(4)
    
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        // 检查是否有足够的字节读取头部
        if (in.readableBytes() < HEADER_SIZE) {
            System.out.println("🔍 [BinaryMessageDecoder] 可读字节不足: " + in.readableBytes() + " < " + HEADER_SIZE);
            return;
        }
        
        // 标记读位置
        in.markReaderIndex();
        
        // 读取 Magic Number (2 字节) - 但不消费
        short magic = in.getShort(in.readerIndex());
        System.out.println("🔍 [BinaryMessageDecoder] Magic Number: 0x" + Integer.toHexString(magic & 0xFFFF) + 
                         " (期望: 0x" + Integer.toHexString(MAGIC_NUMBER & 0xFFFF) + ")");
        
        if (magic != MAGIC_NUMBER) {
            // 不是我们的协议格式，不处理，让后续解码器（ObjectDecoder）来处理
            // 这里直接返回，不消费任何字节
            // ByteToMessageDecoder 会自动传递给后续处理器
            System.out.println("⏭️  [BinaryMessageDecoder] Magic不匹配，跳过给ObjectDecoder处理");
            return;
        }
        
        // 确认是我们的协议，现在消费 magic
        in.readShort();
        
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
        System.out.println("✅ [BinaryMessageDecoder] 成功解码Message: type=" + messageType + ", bodyLen=" + bodyLength);
    }
}

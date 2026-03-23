package com.nebula.agent.codec;

import com.nebula.common.protocol.Message;
import com.nebula.common.protocol.MessageType;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 改进版二进制消息解码器
 * 
 * 优化点 1：添加包长限制，防御 Integer.MAX_VALUE 攻击
 * 优化点 2：预留反序列化工厂模式的接口（后续性能优化）
 * 优化点 3：明确说明 ByteBuf 生命周期管理
 */
public class BinaryMessageDecoderV2 extends ByteToMessageDecoder {
    
    private static final short MAGIC_NUMBER = (short) 0xCAFE;
    private static final int HEADER_SIZE = 7;            // Magic(2) + Type(1) + Length(4)
    private static final int MAX_BODY_LENGTH = 1024 * 1024;  // 1MB，防止恶意包
    
    /**
     * 【优化点 2】类型映射工厂（为后续性能优化预留）
     */
    private static final ConcurrentHashMap<Byte, Class<?>> TYPE_CLASS_MAP = 
        new ConcurrentHashMap<>();
    
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (in.readableBytes() < HEADER_SIZE) {
            return;
        }
        
        in.markReaderIndex();
        
        // 1. 验证 Magic
        short magic = in.readShort();
        if (magic != MAGIC_NUMBER) {
            ctx.close();
            throw new IllegalStateException("Invalid magic number: 0x" + Integer.toHexString(magic));
        }
        
        // 2. 读取消息类型
        byte typeValue = in.readByte();
        MessageType messageType = MessageType.fromValue(typeValue);
        
        // 3. 读取消息体长度
        int bodyLength = in.readInt();
        
        // 【优化点 1】❌ 防御：拒绝超大包
        if (bodyLength < 0 || bodyLength > MAX_BODY_LENGTH) {
            System.err.println("[BinaryMessageDecoder] ❌ 包长度非法: " + bodyLength + 
                             "（超过 " + MAX_BODY_LENGTH + " 字节）");
            ctx.close();  // 立即关闭连接，防止 OOM
            return;
        }
        
        // 4. 检查是否收到完整的消息体
        if (in.readableBytes() < bodyLength) {
            in.resetReaderIndex();
            return;  // 等待更多数据
        }
        
        // 【重点】ByteBuf 生命周期说明：
        // - 这里的 'in' 是由 ByteToMessageDecoder 自动管理的，我们不需要手工 release
        // - Netty 会在 out 列表中的消息被下一个 Handler 处理后自动释放 ByteBuf
        // - 切记：如果你用了 in.slice() 或 in.duplicate()，必须手工 release！
        
        // 5. 读取消息体
        byte[] bodyBytes = new byte[bodyLength];
        in.readBytes(bodyBytes);
        String body = new String(bodyBytes, "UTF-8");
        
        // 6. 组装消息对象并输出
        Message message = new Message(messageType, body);
        out.add(message);
    }
}

package com.nebula.server;

import com.nebula.common.protocol.ControlCommand;
import com.nebula.common.protocol.AckResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 控制命令操作日志记录器
 * 用于记录 Server 下发的所有控制命令和收到的 ACK 响应
 */
public class ControlCommandLogger {
    
    private static final String LOG_DIR = "logs";
    private static final String CMD_LOG_FILE = "logs/control-commands.log";
    private static final String ACK_LOG_FILE = "logs/ack-responses.log";
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    
    static {
        // 初始化日志目录
        try {
            Files.createDirectories(Paths.get(LOG_DIR));
        } catch (IOException e) {
            System.err.println("❌ 创建日志目录失败: " + e.getMessage());
        }
    }
    
    /**
     * 记录下发的控制命令
     * 
     * @param cmd 控制命令
     * @param reason 触发原因（如 "HEAP_HIGH", "CPU_HIGH", "MANUAL" 等）
     */
    public static void logCommand(ControlCommand cmd, String reason) {
        try {
            String timestamp = LocalDateTime.now().format(formatter);
            String logEntry = String.format(
                "[%s] CMD_ID=%s, ACTION=%s, RATE=%.2f, TARGET_APPS=%s, REASON=%s%n",
                timestamp,
                cmd.getCommandId(),
                cmd.getAction().getName(),
                cmd.getSamplingRate(),
                cmd.getTargetApps() != null && !cmd.getTargetApps().isEmpty() 
                    ? String.join(",", cmd.getTargetApps()) 
                    : "ALL",
                reason
            );
            
            Files.write(
                Paths.get(CMD_LOG_FILE),
                logEntry.getBytes(),
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            );
            
            System.out.println("📝 命令已记录: " + cmd.getCommandId());
        } catch (IOException e) {
            System.err.println("❌ 记录命令日志失败: " + e.getMessage());
        }
    }
    
    /**
     * 记录收到的 ACK 响应
     * 
     * @param ack ACK 响应
     * @param delayMs 下发到确认的延迟时间（毫秒）
     */
    public static void logAckResponse(AckResponse ack, long delayMs) {
        try {
            String timestamp = LocalDateTime.now().format(formatter);
            String logEntry = String.format(
                "[%s] CMD_ID=%s, AGENT_ID=%s, SUCCESS=%s, DELAY_MS=%d, ERROR=%s%n",
                timestamp,
                ack.getCommandId(),
                ack.getAgentId(),
                ack.isSuccess(),
                delayMs,
                ack.getErrorMessage() != null ? ack.getErrorMessage() : "N/A"
            );
            
            Files.write(
                Paths.get(ACK_LOG_FILE),
                logEntry.getBytes(),
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            );
            
            System.out.println("📝 ACK 已记录: " + ack.getCommandId() + " 来自 " + ack.getAgentId());
        } catch (IOException e) {
            System.err.println("❌ 记录 ACK 日志失败: " + e.getMessage());
        }
    }
    
    /**
     * 记录统计信息（每小时输出一次）
     */
    public static void logStatistics(int totalCommands, int successfulAcks, int failedAcks) {
        try {
            String timestamp = LocalDateTime.now().format(formatter);
            String logEntry = String.format(
                "[%s] STAT: TOTAL_CMDS=%d, SUCCESS_ACKS=%d, FAILED_ACKS=%d, SUCCESS_RATE=%.1f%%%n",
                timestamp,
                totalCommands,
                successfulAcks,
                failedAcks,
                successfulAcks > 0 ? (successfulAcks * 100.0 / (successfulAcks + failedAcks)) : 0
            );
            
            Files.write(
                Paths.get(CMD_LOG_FILE),
                logEntry.getBytes(),
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            );
            
            System.out.println("📊 统计已记录");
        } catch (IOException e) {
            System.err.println("❌ 记录统计日志失败: " + e.getMessage());
        }
    }
}

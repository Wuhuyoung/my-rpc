package com.han.rpc.server.tcp;

import com.han.rpc.protocol.ProtocolConstant;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.parsetools.RecordParser;

/**
 * 装饰者模式（使用 recordParser 对原有 handler处理能力进行增强）
 * 解决半包粘包问题
 */
public class TcpBufferHandlerWrapper implements Handler<Buffer> {
    // RecordParser也是一个Handler，可以保证下次读取到特定长度的字节
    private final RecordParser recordParser;

    public TcpBufferHandlerWrapper(Handler<Buffer> bufferHandler) {
        this.recordParser = initRecordParser(bufferHandler);
    }

    private RecordParser initRecordParser(Handler<Buffer> bufferHandler) {
        // 构造parser
        RecordParser parser = RecordParser.newFixed(ProtocolConstant.MESSAGE_HEADER_LENGTH);

        parser.setOutput(new Handler<Buffer>() {
            // 初始化
            int size = -1;
            Buffer resultBuffer = Buffer.buffer();

            @Override
            public void handle(Buffer buffer) {
                if (size == -1) {
                    // 读取消息体长度，设置下一次读取的数据长度
                    size = buffer.getInt(13);
                    parser.fixedSizeMode(size);
                    // 写入消息头
                    resultBuffer.appendBuffer(buffer);
                } else {
                    // 写入消息体
                    resultBuffer.appendBuffer(buffer);
                    // 已拼接为完整buffer，使用handler进行处理(未增强的原始处理逻辑)
                    bufferHandler.handle(resultBuffer);
                    // 重置，下一轮读取
                    size = -1;
                    resultBuffer = Buffer.buffer();
                    parser.fixedSizeMode(ProtocolConstant.MESSAGE_HEADER_LENGTH);
                }
            }
        });

        return parser;
    }

    @Override
    public void handle(Buffer buffer) {
        recordParser.handle(buffer);
    }
}

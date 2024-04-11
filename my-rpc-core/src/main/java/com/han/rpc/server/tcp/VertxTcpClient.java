package com.han.rpc.server.tcp;

import cn.hutool.core.util.IdUtil;
import com.han.rpc.RpcApplication;
import com.han.rpc.model.RpcRequest;
import com.han.rpc.model.RpcResponse;
import com.han.rpc.model.ServiceMetaInfo;
import com.han.rpc.protocol.ProtocolConstant;
import com.han.rpc.protocol.ProtocolMessage;
import com.han.rpc.protocol.ProtocolMessageDecoder;
import com.han.rpc.protocol.ProtocolMessageEncoder;
import com.han.rpc.protocol.ProtocolMessageSerializerEnum;
import com.han.rpc.protocol.ProtocolMessageStatusEnum;
import com.han.rpc.protocol.ProtocolMessageTypeEnum;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetSocket;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class VertxTcpClient {
    public static RpcResponse doRequest(RpcRequest rpcRequest, ServiceMetaInfo serviceMetaInfo, Long timeout, TimeUnit timeUnit) throws ExecutionException, InterruptedException {
        // 发送TCP请求
        Vertx vertx = Vertx.vertx();
        NetClient netClient = vertx.createNetClient();

        CompletableFuture<RpcResponse> responseFuture = new CompletableFuture<>();
        netClient.connect(serviceMetaInfo.getServicePort(), serviceMetaInfo.getServiceHost(),
                result -> {
                    if (!result.succeeded()) {
                        System.err.println("Failed to connect to TCP server");
                        return;
                    }
                    System.out.println("Connected to TCP server");
                    NetSocket socket = result.result();
                    // 发送请求
                    // 1.构造消息
                    ProtocolMessage<RpcRequest> protocolMessage = new ProtocolMessage<>();
                    ProtocolMessage.Header header = new ProtocolMessage.Header();
                    header.setMagic(ProtocolConstant.PROTOCOL_MAGIC);
                    header.setVersion(ProtocolConstant.PROTOCOL_VERSION);
                    header.setSerializer((byte) ProtocolMessageSerializerEnum.getEnumByValue(RpcApplication.getRpcConfig().getSerializer()).getKey());
                    header.setType((byte) ProtocolMessageTypeEnum.REQUEST.getKey());
                    header.setStatus((byte) ProtocolMessageStatusEnum.OK.getValue());
                    // 生成全局请求ID
                    header.setRequestId(IdUtil.getSnowflakeNextId());
                    protocolMessage.setHeader(header);
                    protocolMessage.setBody(rpcRequest);

                    // 2.编码请求
                    try {
                        Buffer buffer = ProtocolMessageEncoder.encode(protocolMessage);
                        socket.write(buffer);
                    } catch (IOException e) {
                        throw new RuntimeException("协议消息编码错误");
                    }
                    // 3.接收响应
                    TcpBufferHandlerWrapper bufferHandlerWrapper = new TcpBufferHandlerWrapper(buffer -> {
                        try {
                            ProtocolMessage<RpcResponse> responseProtocolMessage =
                                    (ProtocolMessage<RpcResponse>) ProtocolMessageDecoder.decode(buffer);
                            // 由于 Vert.x 提供的请求处理器是异步、反应式的，我们为了更方便地获取结果，可以使用CompletableFuture转异步为同步
                            responseFuture.complete(responseProtocolMessage.getBody());
                        } catch (IOException e) {
                            throw new RuntimeException("协议消息解码错误");
                        }
                    });
                    socket.handler(bufferHandlerWrapper);

                });

        // 阻塞，直到完成了响应，才会继续向下执行
        RpcResponse rpcResponse = null;
        try {
            rpcResponse = responseFuture.get(timeout, timeUnit);
        } catch (TimeoutException e) {
            throw new RuntimeException("执行超时");
        } finally {
            // 记得关闭连接
            netClient.close();
        }
        return rpcResponse;
    }

    public void start() {
        // 创建Vert.x实例
        Vertx vertx = Vertx.vertx();

        vertx.createNetClient().connect(8888, "localhost", result -> {
            if (result.succeeded()) {
                System.out.println("connect to TCP server");
                NetSocket socket = result.result();
                // 发送数据
                for (int i = 0; i < 1000; i++) {
                    String message = "Hello,server!Hello,server!";
                    Buffer buffer = Buffer.buffer();
                    buffer.appendInt(0);
                    buffer.appendInt(message.getBytes().length);
                    buffer.appendBytes(message.getBytes());
                    socket.write(buffer);
                }
                // 接收响应
                socket.handler(buffer -> {
                    System.out.println("Received message from TCP server:" + buffer.toString());
                });
            } else {
                System.err.println("Failed to connect to TCP server");
            }
        });
    }

    public static void main(String[] args) {
        new VertxTcpClient().start();
    }
}

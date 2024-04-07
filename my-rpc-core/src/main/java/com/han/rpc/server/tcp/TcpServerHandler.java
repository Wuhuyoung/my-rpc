package com.han.rpc.server.tcp;

import com.han.rpc.model.RpcRequest;
import com.han.rpc.model.RpcResponse;
import com.han.rpc.protocol.ProtocolConstant;
import com.han.rpc.protocol.ProtocolMessage;
import com.han.rpc.protocol.ProtocolMessageDecoder;
import com.han.rpc.protocol.ProtocolMessageEncoder;
import com.han.rpc.protocol.ProtocolMessageTypeEnum;
import com.han.rpc.register.LocalRegister;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetSocket;

import java.io.IOException;
import java.lang.reflect.Method;

/**
 * TCP请求处理器（服务提供端）
 */
public class TcpServerHandler implements Handler<NetSocket> {

    @Override
    public void handle(NetSocket socket) {
        // 使用装饰者模式解决半包粘包问题
        TcpBufferHandlerWrapper bufferHandlerWrapper = new TcpBufferHandlerWrapper(buffer -> {
            // 解决半包问题
            if (buffer == null || buffer.length() == 0) {
                throw new RuntimeException("消息 buffer 为空");
            }
            if (buffer.getBytes().length < ProtocolConstant.MESSAGE_HEADER_LENGTH) {
                throw new RuntimeException("出现了半包问题");
            }
            // 1.接受请求，解码
            ProtocolMessage<RpcRequest> protocolMessage;
            try {
                protocolMessage = (ProtocolMessage<RpcRequest>) ProtocolMessageDecoder.decode(buffer);
            } catch (IOException e) {
                throw new RuntimeException("协议消息解码错误");
            }
            RpcRequest rpcRequest = protocolMessage.getBody();

            // 2.处理请求
            RpcResponse rpcResponse = new RpcResponse();
            try {
                // 反射调用服务方法
                Class<?> implClass = LocalRegister.get(rpcRequest.getServiceName());
                Method method = implClass.getMethod(rpcRequest.getMethodName(), rpcRequest.getParameterTypes());
                Object result = method.invoke(implClass.newInstance(), rpcRequest.getArgs());
                // 封装返回结果
                rpcResponse.setData(result);
                rpcResponse.setDataType(method.getReturnType());
                rpcResponse.setMessage("ok");
            } catch (Exception e) {
                e.printStackTrace();
                rpcResponse.setMessage(e.getMessage());
                rpcResponse.setException(e);
            }

            // 3.发送响应，编码
            ProtocolMessage.Header header = protocolMessage.getHeader();
            header.setType((byte) ProtocolMessageTypeEnum.RESPONSE.getKey());
            ProtocolMessage<RpcResponse> responseProtocolMessage = new ProtocolMessage<>(header, rpcResponse);

            try {
                Buffer encode = ProtocolMessageEncoder.encode(responseProtocolMessage);
                socket.write(encode);
            } catch (IOException e) {
                throw new RuntimeException("协议消息编码错误");
            }
        });
        socket.handler(bufferHandlerWrapper);
    }
}

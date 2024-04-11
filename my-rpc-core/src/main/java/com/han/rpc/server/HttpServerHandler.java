package com.han.rpc.server;

import com.han.rpc.RpcApplication;
import com.han.rpc.model.RpcRequest;
import com.han.rpc.model.RpcResponse;
import com.han.rpc.register.LocalRegistry;
import com.han.rpc.serializer.Serializer;
import com.han.rpc.serializer.SerializerFactory;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Objects;

/**
 * HTTP请求处理器
 */
public class HttpServerHandler implements Handler<HttpServerRequest> {
    @Override
    public void handle(HttpServerRequest request) {
        // 指定序列化器
        final Serializer serializer = SerializerFactory.getInstance(RpcApplication.getRpcConfig().getSerializer());

        // 记录日志
        System.out.println("Received request: " + request.method() + " " + request.uri());

        // 异步处理HTTP请求
        request.bodyHandler(body -> {
            // 1.反序列化
            byte[] bytes = body.getBytes();
            RpcRequest rpcRequest = null;
            RpcResponse rpcResponse = new RpcResponse();
            try {
                rpcRequest = serializer.deserialize(bytes, RpcRequest.class);
            } catch (Exception e) {
                e.printStackTrace();
            }

            // 2.请求为null，直接返回
            if (Objects.isNull(rpcRequest)) {
                rpcResponse.setMessage("rpcRequest is null");
                doResponse(request, rpcResponse, serializer);
                return;
            }
            try {
                // 3.通过服务注册器得到对应实现类
                Class<?> implClass = LocalRegistry.get(rpcRequest.getServiceName());
                // 4.反射调用
                Method method = implClass.getMethod(rpcRequest.getMethodName(), rpcRequest.getParameterTypes());
                Object result = method.invoke(implClass.newInstance(), rpcRequest.getArgs());
                // 5.封装返回对象
                rpcResponse.setData(result);
                rpcResponse.setDataType(method.getReturnType());
                rpcResponse.setMessage("ok");
            } catch (Exception e) {
                e.printStackTrace();
                rpcResponse.setMessage(e.getMessage());
                rpcResponse.setException(e);
            }
            // 6.响应
            doResponse(request, rpcResponse, serializer);
        });
    }

    /**
     * 响应
     * @param request
     * @param rpcResponse
     * @param serializer
     */
    private void doResponse(HttpServerRequest request, RpcResponse rpcResponse, Serializer serializer) {
        HttpServerResponse httpServerResponse = request.response()
                .putHeader("content-type", "application/json");
        try {
            // 序列化调用结果，并响应
            byte[] bytes = serializer.serialize(rpcResponse);
            httpServerResponse.end(Buffer.buffer(bytes));
        } catch (IOException e) {
            e.printStackTrace();
            httpServerResponse.end(Buffer.buffer());
        }
    }
}

package com.han.rpc.server;


import io.vertx.core.Vertx;

public class VerxHttpServer implements HttpServer {
    public void doStart(int port) {
        // 创建verx实例
        Vertx vertx = Vertx.vertx();
        // 创建Http服务器
        io.vertx.core.http.HttpServer server = vertx.createHttpServer();

        // 监听端口并处理请求
        server.requestHandler(new HttpServerHandler());

        // 启动服务器并监听指定端口
        server.listen(port, result -> {
            if (result.succeeded()) {
                System.out.println("Server starts successfully on port " + port);
            } else {
                System.out.println("Failed to start server: " + result.cause());
            }
        });
    }
}

package com.han.rpc.protocol;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.IdUtil;
import com.han.rpc.constant.RpcConstant;
import com.han.rpc.model.RpcRequest;
import io.vertx.core.buffer.Buffer;
import org.junit.jupiter.api.Test;

import java.io.IOException;

public class ProtocolMessageTest {

    @Test
    public void testEncodeAndDecode() throws IOException {
        // 构造消息
        ProtocolMessage<RpcRequest> message = new ProtocolMessage<>();
        ProtocolMessage.Header header = new ProtocolMessage.Header();
        header.setMagic(ProtocolConstant.PROTOCOL_MAGIC);
        header.setVersion(ProtocolConstant.PROTOCOL_VERSION);
        header.setSerializer((byte) ProtocolMessageSerializerEnum.JDK.getKey());
        header.setType((byte) ProtocolMessageTypeEnum.REQUEST.getKey());
        header.setStatus((byte) ProtocolMessageStatusEnum.OK.getValue());
        header.setRequestId(IdUtil.getSnowflakeNextId());

        RpcRequest rpcRequest = new RpcRequest();
        rpcRequest.setServiceName("myService");
        rpcRequest.setMethodName("testEncodeAndDecode");
        rpcRequest.setParameterTypes(new Class[]{String.class});
        rpcRequest.setArgs(new Object[]{"hello"});
        rpcRequest.setServiceVersion(RpcConstant.DEFAULT_SERVICE_VERSION);

        message.setHeader(header);
        message.setBody(rpcRequest);

        // 编解码
        Buffer encodeBuffer = ProtocolMessageEncoder.encode(message);
        ProtocolMessage<?> protocolMessage = ProtocolMessageDecoder.decode(encodeBuffer);
        Assert.notNull(protocolMessage);
    }
}

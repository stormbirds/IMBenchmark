package cn.stormbirds.stormim.imbenchmark;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import io.netty.channel.*;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;

import java.util.UUID;

/**
 * <p>
 * cn.stormbirds.stormim.imbenchmark
 * </p>
 *
 * @author StormBirds Email：xbaojun@gmail.com
 * @since 2020/1/16 11:54
 */
public class TCPChannelInitializerHandler extends ChannelInitializer<Channel> {
    private String userId;
    private String token;
    public TCPChannelInitializerHandler(String userId,String token){
        this.token = token;
        this.userId = userId;
    }

    @Override
    protected void initChannel(Channel channel) throws Exception {
        ChannelPipeline pipeline = channel.pipeline();

        // netty提供的自定义长度解码器，解决TCP拆包/粘包问题
        pipeline.addLast("frameEncoder", new LengthFieldPrepender(2));
        pipeline.addLast("frameDecoder", new LengthFieldBasedFrameDecoder(65535,
                0, 2, 0, 2));

        // 增加protobuf编解码支持
        pipeline.addLast(new ProtobufEncoder());
        pipeline.addLast(new ProtobufDecoder(MessageProtobuf.Msg.getDefaultInstance()));

        // 握手认证消息响应处理handler
        pipeline.addLast("LoginAuthRespHandler.class.getSimpleName()", new ChannelInboundHandlerAdapter(){

            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                MessageProtobuf.Msg handshakeRespMsg = (MessageProtobuf.Msg) msg;
                if (handshakeRespMsg == null || handshakeRespMsg.getHead() == null) {
                    return;
                }

                MessageProtobuf.Msg handshakeMsg = getHandshakeMsg(TCPChannelInitializerHandler.this.userId,
                        TCPChannelInitializerHandler.this.token);
                if (handshakeMsg == null || handshakeMsg.getHead() == null) {
                    return;
                }

                int handshakeMsgType = handshakeMsg.getHead().getMsgType();
                if (handshakeMsgType == handshakeRespMsg.getHead().getMsgType()) {
                    System.out.println("收到服务端握手响应消息，message=" + handshakeRespMsg);
                    int status = -1;
                    try {
                        JSONObject jsonObj = JSON.parseObject(handshakeRespMsg.getHead().getExtend());
                        status = jsonObj.getIntValue("status");
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {

//                        if (status == 1) {
//                        } else {
////                            imsClient.resetConnect(false);// 握手失败，触发重连
//                        }
                    }
                } else {
                    // 消息透传
                    ctx.fireChannelRead(msg);
                }
            }
        });
        // 心跳消息响应处理handler
//        pipeline.addLast("HeartbeatRespHandler.class.getSimpleName()", new HeartbeatRespHandler(imsClient));
        // 接收消息处理handler
//        pipeline.addLast("TCPReadHandler.class.getSimpleName()", new TCPReadHandler(imsClient));
    }
    public MessageProtobuf.Msg getHandshakeMsg(String userId,String token) {
        MessageProtobuf.Msg.Builder builder = MessageProtobuf.Msg.newBuilder();
        MessageProtobuf.Head.Builder headBuilder = MessageProtobuf.Head.newBuilder();
        headBuilder.setMsgId(UUID.randomUUID().toString());
        headBuilder.setMsgType(MessageType.HANDSHAKE.getMsgType());
        headBuilder.setFromId(userId);
        headBuilder.setTimestamp(System.currentTimeMillis());

        JSONObject jsonObj = new JSONObject();
        jsonObj.put("token", token);
        headBuilder.setExtend(jsonObj.toString());
        builder.setHead(headBuilder.build());

        return builder.build();
    }
}

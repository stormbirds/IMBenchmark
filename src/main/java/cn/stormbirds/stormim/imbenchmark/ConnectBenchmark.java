package cn.stormbirds.stormim.imbenchmark;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.protocol.java.sampler.AbstractJavaSamplerClient;
import org.apache.jmeter.protocol.java.sampler.JavaSamplerContext;
import org.apache.jmeter.samplers.SampleResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * <p>
 * cn.stormbirds.stormim.imbenchmark
 * </p>
 *
 * @author StormBirds Email：xbaojun@gmail.com
 * @since 2020/1/16 11:11
 */
public class ConnectBenchmark extends AbstractJavaSamplerClient{

    private final Logger logger = LoggerFactory.getLogger(ConnectBenchmark.class);

    int status = 0;
    public SampleResult runTest(JavaSamplerContext javaSamplerContext) {

        SampleResult sp = new SampleResult(); //采样结果

        UUID uuid = UUID.randomUUID();
        final String key = uuid.toString();
        logger.info("生成用户名："+key );
        EventLoopGroup loopGroup = new NioEventLoopGroup(4);
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(loopGroup).channel(NioSocketChannel.class);
        // 设置该选项以后，如果在两小时内没有数据的通信时，TCP会自动发送一个活动探测数据报文
        bootstrap.option(ChannelOption.SO_KEEPALIVE, true);
        // 设置禁用nagle算法
        bootstrap.option(ChannelOption.TCP_NODELAY, true);
        // 设置连接超时时长
        bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 8000);
        // 设置初始化Channel
        bootstrap.handler(new ChannelInitializer<Channel>(){
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
                    public void channelActive(ChannelHandlerContext ctx) throws Exception {
//                        super.channelActive(ctx);
                        MessageProtobuf.Msg handshakeMsg = getHandshakeMsg(key,
                                key);
                        ctx.writeAndFlush(handshakeMsg);
                    }

                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                        MessageProtobuf.Msg handshakeRespMsg = (MessageProtobuf.Msg) msg;
                        if (handshakeRespMsg == null || handshakeRespMsg.getHead() == null) {
                            logger.info("收到服务端握手响应消息，message 为NULL" );
                            status = -1;
                            return;
                        }

                        MessageProtobuf.Msg handshakeMsg = getHandshakeMsg(key,
                                key);
                        if (handshakeMsg == null || handshakeMsg.getHead() == null) {
                            status = -1;
                            return;
                        }

                        int handshakeMsgType = handshakeMsg.getHead().getMsgType();
                        if (handshakeMsgType == handshakeRespMsg.getHead().getMsgType()) {
                            logger.info("收到服务端握手响应消息，message=" + handshakeRespMsg);

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
            }
        });

        String currentHost = javaSamplerContext.getParameter("host", "wifi.stormbirds.cn");
        int currentPort = javaSamplerContext.getIntParameter("port", 8855);
        sp.sampleStart();

        try {
            Channel channel = bootstrap.connect(currentHost, currentPort).sync().channel();
            if(channel!=null){

                sp.setSuccessful(true);
            }else {
                sp.setSuccessful(false);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
            sp.setSuccessful(false);
        }
        sp.sampleEnd();
        return sp;
    }

//    @Override
//    public void setupTest(JavaSamplerContext context) {
//
//        super.setupTest(context);
//    }
//
//    @Override
//    public void teardownTest(JavaSamplerContext context) {
//        super.teardownTest(context);
//    }
//
//    @Override
//    public Arguments getDefaultParameters() {
//        Arguments arguments = super.getDefaultParameters();
//        arguments.addArgument("host","127.0.0.1");
//        arguments.addArgument("port","8855");
//        return arguments;
//    }

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


    public enum MessageType {

        /*
         * 握手消息
         */
        HANDSHAKE(1001),

        /*
         * 心跳消息
         */
        HEARTBEAT(1002),

        /*
         * 客户端提交的消息接收状态报告
         */
        CLIENT_MSG_RECEIVED_STATUS_REPORT(1009),

        /*
         * 服务端返回的消息发送状态报告
         */
        SERVER_MSG_SENT_STATUS_REPORT(1010),

        /**
         * 单聊消息
         */
        SINGLE_CHAT(2001),

        /**
         * 群聊消息
         */
        GROUP_CHAT(3001);

        private int msgType;

        MessageType(int msgType) {
            this.msgType = msgType;
        }

        public int getMsgType() {
            return this.msgType;
        }

        public enum MessageContentType {

            /**
             * 文本消息
             */
            TEXT(101),

            /**
             * 图片消息
             */
            IMAGE(102),

            /**
             * 语音消息
             */
            VOICE(103),

            /**
             * 视频消息
             */
            VIDEO(104);

            private int msgContentType;

            MessageContentType(int msgContentType) {
                this.msgContentType = msgContentType;
            }

            public int getMsgContentType() {
                return this.msgContentType;
            }
        }
    }

    public static final class MessageProtobuf {
        private MessageProtobuf() {}
        public static void registerAllExtensions(
                com.google.protobuf.ExtensionRegistryLite registry) {
        }

        public static void registerAllExtensions(
                com.google.protobuf.ExtensionRegistry registry) {
            registerAllExtensions(
                    (com.google.protobuf.ExtensionRegistryLite) registry);
        }
        public interface MsgOrBuilder extends
                // @@protoc_insertion_point(interface_extends:Msg)
                com.google.protobuf.MessageOrBuilder {

            /**
             * <pre>
             * 消息头
             * </pre>
             *
             * <code>.Head head = 1;</code>
             */
            boolean hasHead();
            /**
             * <pre>
             * 消息头
             * </pre>
             *
             * <code>.Head head = 1;</code>
             */
            MessageProtobuf.Head getHead();
            /**
             * <pre>
             * 消息头
             * </pre>
             *
             * <code>.Head head = 1;</code>
             */
            MessageProtobuf.HeadOrBuilder getHeadOrBuilder();

            /**
             * <pre>
             * 消息体
             * </pre>
             *
             * <code>string body = 2;</code>
             */
            String getBody();
            /**
             * <pre>
             * 消息体
             * </pre>
             *
             * <code>string body = 2;</code>
             */
            com.google.protobuf.ByteString
            getBodyBytes();
        }
        /**
         * Protobuf type {@code Msg}
         */
        public  static final class Msg extends
                com.google.protobuf.GeneratedMessageV3 implements
                // @@protoc_insertion_point(message_implements:Msg)
                MessageProtobuf.MsgOrBuilder {
            private static final long serialVersionUID = 0L;
            // Use Msg.newBuilder() to construct.
            private Msg(com.google.protobuf.GeneratedMessageV3.Builder<?> builder) {
                super(builder);
            }
            private Msg() {
                body_ = "";
            }

            @Override
            public final com.google.protobuf.UnknownFieldSet
            getUnknownFields() {
                return this.unknownFields;
            }
            private Msg(
                    com.google.protobuf.CodedInputStream input,
                    com.google.protobuf.ExtensionRegistryLite extensionRegistry)
                    throws com.google.protobuf.InvalidProtocolBufferException {
                this();
                if (extensionRegistry == null) {
                    throw new NullPointerException();
                }
                int mutable_bitField0_ = 0;
                com.google.protobuf.UnknownFieldSet.Builder unknownFields =
                        com.google.protobuf.UnknownFieldSet.newBuilder();
                try {
                    boolean done = false;
                    while (!done) {
                        int tag = input.readTag();
                        switch (tag) {
                            case 0:
                                done = true;
                                break;
                            default: {
                                if (!parseUnknownFieldProto3(
                                        input, unknownFields, extensionRegistry, tag)) {
                                    done = true;
                                }
                                break;
                            }
                            case 10: {
                                MessageProtobuf.Head.Builder subBuilder = null;
                                if (head_ != null) {
                                    subBuilder = head_.toBuilder();
                                }
                                head_ = input.readMessage(MessageProtobuf.Head.parser(), extensionRegistry);
                                if (subBuilder != null) {
                                    subBuilder.mergeFrom(head_);
                                    head_ = subBuilder.buildPartial();
                                }

                                break;
                            }
                            case 18: {
                                String s = input.readStringRequireUtf8();

                                body_ = s;
                                break;
                            }
                        }
                    }
                } catch (com.google.protobuf.InvalidProtocolBufferException e) {
                    throw e.setUnfinishedMessage(this);
                } catch (java.io.IOException e) {
                    throw new com.google.protobuf.InvalidProtocolBufferException(
                            e).setUnfinishedMessage(this);
                } finally {
                    this.unknownFields = unknownFields.build();
                    makeExtensionsImmutable();
                }
            }
            public static final com.google.protobuf.Descriptors.Descriptor
            getDescriptor() {
                return MessageProtobuf.internal_static_Msg_descriptor;
            }

            protected FieldAccessorTable
            internalGetFieldAccessorTable() {
                return MessageProtobuf.internal_static_Msg_fieldAccessorTable
                        .ensureFieldAccessorsInitialized(
                                MessageProtobuf.Msg.class, MessageProtobuf.Msg.Builder.class);
            }

            public static final int HEAD_FIELD_NUMBER = 1;
            private MessageProtobuf.Head head_;
            /**
             * <pre>
             * 消息头
             * </pre>
             *
             * <code>.Head head = 1;</code>
             */
            public boolean hasHead() {
                return head_ != null;
            }
            /**
             * <pre>
             * 消息头
             * </pre>
             *
             * <code>.Head head = 1;</code>
             */
            public MessageProtobuf.Head getHead() {
                return head_ == null ? MessageProtobuf.Head.getDefaultInstance() : head_;
            }
            /**
             * <pre>
             * 消息头
             * </pre>
             *
             * <code>.Head head = 1;</code>
             */
            public MessageProtobuf.HeadOrBuilder getHeadOrBuilder() {
                return getHead();
            }

            public static final int BODY_FIELD_NUMBER = 2;
            private volatile Object body_;
            /**
             * <pre>
             * 消息体
             * </pre>
             *
             * <code>string body = 2;</code>
             */
            public String getBody() {
                Object ref = body_;
                if (ref instanceof String) {
                    return (String) ref;
                } else {
                    com.google.protobuf.ByteString bs =
                            (com.google.protobuf.ByteString) ref;
                    String s = bs.toStringUtf8();
                    body_ = s;
                    return s;
                }
            }
            /**
             * <pre>
             * 消息体
             * </pre>
             *
             * <code>string body = 2;</code>
             */
            public com.google.protobuf.ByteString
            getBodyBytes() {
                Object ref = body_;
                if (ref instanceof String) {
                    com.google.protobuf.ByteString b =
                            com.google.protobuf.ByteString.copyFromUtf8(
                                    (String) ref);
                    body_ = b;
                    return b;
                } else {
                    return (com.google.protobuf.ByteString) ref;
                }
            }

            private byte memoizedIsInitialized = -1;
            public final boolean isInitialized() {
                byte isInitialized = memoizedIsInitialized;
                if (isInitialized == 1) return true;
                if (isInitialized == 0) return false;

                memoizedIsInitialized = 1;
                return true;
            }

            public void writeTo(com.google.protobuf.CodedOutputStream output)
                    throws java.io.IOException {
                if (head_ != null) {
                    output.writeMessage(1, getHead());
                }
                if (!getBodyBytes().isEmpty()) {
                    com.google.protobuf.GeneratedMessageV3.writeString(output, 2, body_);
                }
                unknownFields.writeTo(output);
            }

            public int getSerializedSize() {
                int size = memoizedSize;
                if (size != -1) return size;

                size = 0;
                if (head_ != null) {
                    size += com.google.protobuf.CodedOutputStream
                            .computeMessageSize(1, getHead());
                }
                if (!getBodyBytes().isEmpty()) {
                    size += com.google.protobuf.GeneratedMessageV3.computeStringSize(2, body_);
                }
                size += unknownFields.getSerializedSize();
                memoizedSize = size;
                return size;
            }

            @Override
            public boolean equals(final Object obj) {
                if (obj == this) {
                    return true;
                }
                if (!(obj instanceof MessageProtobuf.Msg)) {
                    return super.equals(obj);
                }
                MessageProtobuf.Msg other = (MessageProtobuf.Msg) obj;

                boolean result = true;
                result = result && (hasHead() == other.hasHead());
                if (hasHead()) {
                    result = result && getHead()
                            .equals(other.getHead());
                }
                result = result && getBody()
                        .equals(other.getBody());
                result = result && unknownFields.equals(other.unknownFields);
                return result;
            }

            @Override
            public int hashCode() {
                if (memoizedHashCode != 0) {
                    return memoizedHashCode;
                }
                int hash = 41;
                hash = (19 * hash) + getDescriptor().hashCode();
                if (hasHead()) {
                    hash = (37 * hash) + HEAD_FIELD_NUMBER;
                    hash = (53 * hash) + getHead().hashCode();
                }
                hash = (37 * hash) + BODY_FIELD_NUMBER;
                hash = (53 * hash) + getBody().hashCode();
                hash = (29 * hash) + unknownFields.hashCode();
                memoizedHashCode = hash;
                return hash;
            }

            public static MessageProtobuf.Msg parseFrom(
                    java.nio.ByteBuffer data)
                    throws com.google.protobuf.InvalidProtocolBufferException {
                return PARSER.parseFrom(data);
            }
            public static MessageProtobuf.Msg parseFrom(
                    java.nio.ByteBuffer data,
                    com.google.protobuf.ExtensionRegistryLite extensionRegistry)
                    throws com.google.protobuf.InvalidProtocolBufferException {
                return PARSER.parseFrom(data, extensionRegistry);
            }
            public static MessageProtobuf.Msg parseFrom(
                    com.google.protobuf.ByteString data)
                    throws com.google.protobuf.InvalidProtocolBufferException {
                return PARSER.parseFrom(data);
            }
            public static MessageProtobuf.Msg parseFrom(
                    com.google.protobuf.ByteString data,
                    com.google.protobuf.ExtensionRegistryLite extensionRegistry)
                    throws com.google.protobuf.InvalidProtocolBufferException {
                return PARSER.parseFrom(data, extensionRegistry);
            }
            public static MessageProtobuf.Msg parseFrom(byte[] data)
                    throws com.google.protobuf.InvalidProtocolBufferException {
                return PARSER.parseFrom(data);
            }
            public static MessageProtobuf.Msg parseFrom(
                    byte[] data,
                    com.google.protobuf.ExtensionRegistryLite extensionRegistry)
                    throws com.google.protobuf.InvalidProtocolBufferException {
                return PARSER.parseFrom(data, extensionRegistry);
            }
            public static MessageProtobuf.Msg parseFrom(java.io.InputStream input)
                    throws java.io.IOException {
                return com.google.protobuf.GeneratedMessageV3
                        .parseWithIOException(PARSER, input);
            }
            public static MessageProtobuf.Msg parseFrom(
                    java.io.InputStream input,
                    com.google.protobuf.ExtensionRegistryLite extensionRegistry)
                    throws java.io.IOException {
                return com.google.protobuf.GeneratedMessageV3
                        .parseWithIOException(PARSER, input, extensionRegistry);
            }
            public static MessageProtobuf.Msg parseDelimitedFrom(java.io.InputStream input)
                    throws java.io.IOException {
                return com.google.protobuf.GeneratedMessageV3
                        .parseDelimitedWithIOException(PARSER, input);
            }
            public static MessageProtobuf.Msg parseDelimitedFrom(
                    java.io.InputStream input,
                    com.google.protobuf.ExtensionRegistryLite extensionRegistry)
                    throws java.io.IOException {
                return com.google.protobuf.GeneratedMessageV3
                        .parseDelimitedWithIOException(PARSER, input, extensionRegistry);
            }
            public static MessageProtobuf.Msg parseFrom(
                    com.google.protobuf.CodedInputStream input)
                    throws java.io.IOException {
                return com.google.protobuf.GeneratedMessageV3
                        .parseWithIOException(PARSER, input);
            }
            public static MessageProtobuf.Msg parseFrom(
                    com.google.protobuf.CodedInputStream input,
                    com.google.protobuf.ExtensionRegistryLite extensionRegistry)
                    throws java.io.IOException {
                return com.google.protobuf.GeneratedMessageV3
                        .parseWithIOException(PARSER, input, extensionRegistry);
            }

            public MessageProtobuf.Msg.Builder newBuilderForType() { return newBuilder(); }
            public static MessageProtobuf.Msg.Builder newBuilder() {
                return DEFAULT_INSTANCE.toBuilder();
            }
            public static MessageProtobuf.Msg.Builder newBuilder(MessageProtobuf.Msg prototype) {
                return DEFAULT_INSTANCE.toBuilder().mergeFrom(prototype);
            }
            public MessageProtobuf.Msg.Builder toBuilder() {
                return this == DEFAULT_INSTANCE
                        ? new MessageProtobuf.Msg.Builder() : new MessageProtobuf.Msg.Builder().mergeFrom(this);
            }

            @Override
            protected MessageProtobuf.Msg.Builder newBuilderForType(
                    BuilderParent parent) {
                MessageProtobuf.Msg.Builder builder = new MessageProtobuf.Msg.Builder(parent);
                return builder;
            }
            /**
             * Protobuf type {@code Msg}
             */
            public static final class Builder extends
                    com.google.protobuf.GeneratedMessageV3.Builder<MessageProtobuf.Msg.Builder> implements
                    // @@protoc_insertion_point(builder_implements:Msg)
                    MessageProtobuf.MsgOrBuilder {
                public static final com.google.protobuf.Descriptors.Descriptor
                getDescriptor() {
                    return MessageProtobuf.internal_static_Msg_descriptor;
                }

                protected FieldAccessorTable
                internalGetFieldAccessorTable() {
                    return MessageProtobuf.internal_static_Msg_fieldAccessorTable
                            .ensureFieldAccessorsInitialized(
                                    MessageProtobuf.Msg.class, MessageProtobuf.Msg.Builder.class);
                }

                // Construct using com.freddy.im.cn.stormbirds.stormim.imserver.protobuf.MessageProtobuf.Msg.newBuilder()
                private Builder() {
                    maybeForceBuilderInitialization();
                }

                private Builder(
                        BuilderParent parent) {
                    super(parent);
                    maybeForceBuilderInitialization();
                }
                private void maybeForceBuilderInitialization() {
                    if (com.google.protobuf.GeneratedMessageV3
                            .alwaysUseFieldBuilders) {
                    }
                }
                public MessageProtobuf.Msg.Builder clear() {
                    super.clear();
                    if (headBuilder_ == null) {
                        head_ = null;
                    } else {
                        head_ = null;
                        headBuilder_ = null;
                    }
                    body_ = "";

                    return this;
                }

                public com.google.protobuf.Descriptors.Descriptor
                getDescriptorForType() {
                    return MessageProtobuf.internal_static_Msg_descriptor;
                }

                public MessageProtobuf.Msg getDefaultInstanceForType() {
                    return MessageProtobuf.Msg.getDefaultInstance();
                }

                public MessageProtobuf.Msg build() {
                    MessageProtobuf.Msg result = buildPartial();
                    if (!result.isInitialized()) {
                        throw newUninitializedMessageException(result);
                    }
                    return result;
                }

                public MessageProtobuf.Msg buildPartial() {
                    MessageProtobuf.Msg result = new MessageProtobuf.Msg(this);
                    if (headBuilder_ == null) {
                        result.head_ = head_;
                    } else {
                        result.head_ = headBuilder_.build();
                    }
                    result.body_ = body_;
                    onBuilt();
                    return result;
                }

                public MessageProtobuf.Msg.Builder clone() {
                    return (MessageProtobuf.Msg.Builder) super.clone();
                }
                public MessageProtobuf.Msg.Builder setField(
                        com.google.protobuf.Descriptors.FieldDescriptor field,
                        Object value) {
                    return (MessageProtobuf.Msg.Builder) super.setField(field, value);
                }
                public MessageProtobuf.Msg.Builder clearField(
                        com.google.protobuf.Descriptors.FieldDescriptor field) {
                    return (MessageProtobuf.Msg.Builder) super.clearField(field);
                }
                public MessageProtobuf.Msg.Builder clearOneof(
                        com.google.protobuf.Descriptors.OneofDescriptor oneof) {
                    return (MessageProtobuf.Msg.Builder) super.clearOneof(oneof);
                }
                public MessageProtobuf.Msg.Builder setRepeatedField(
                        com.google.protobuf.Descriptors.FieldDescriptor field,
                        int index, Object value) {
                    return (MessageProtobuf.Msg.Builder) super.setRepeatedField(field, index, value);
                }
                public MessageProtobuf.Msg.Builder addRepeatedField(
                        com.google.protobuf.Descriptors.FieldDescriptor field,
                        Object value) {
                    return (MessageProtobuf.Msg.Builder) super.addRepeatedField(field, value);
                }
                public MessageProtobuf.Msg.Builder mergeFrom(com.google.protobuf.Message other) {
                    if (other instanceof MessageProtobuf.Msg) {
                        return mergeFrom((MessageProtobuf.Msg)other);
                    } else {
                        super.mergeFrom(other);
                        return this;
                    }
                }

                public MessageProtobuf.Msg.Builder mergeFrom(MessageProtobuf.Msg other) {
                    if (other == MessageProtobuf.Msg.getDefaultInstance()) return this;
                    if (other.hasHead()) {
                        mergeHead(other.getHead());
                    }
                    if (!other.getBody().isEmpty()) {
                        body_ = other.body_;
                        onChanged();
                    }
                    this.mergeUnknownFields(other.unknownFields);
                    onChanged();
                    return this;
                }

                public final boolean isInitialized() {
                    return true;
                }

                public MessageProtobuf.Msg.Builder mergeFrom(
                        com.google.protobuf.CodedInputStream input,
                        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
                        throws java.io.IOException {
                    MessageProtobuf.Msg parsedMessage = null;
                    try {
                        parsedMessage = PARSER.parsePartialFrom(input, extensionRegistry);
                    } catch (com.google.protobuf.InvalidProtocolBufferException e) {
                        parsedMessage = (MessageProtobuf.Msg) e.getUnfinishedMessage();
                        throw e.unwrapIOException();
                    } finally {
                        if (parsedMessage != null) {
                            mergeFrom(parsedMessage);
                        }
                    }
                    return this;
                }

                private MessageProtobuf.Head head_ = null;
                private com.google.protobuf.SingleFieldBuilderV3<
                        MessageProtobuf.Head, MessageProtobuf.Head.Builder, MessageProtobuf.HeadOrBuilder> headBuilder_;
                /**
                 * <pre>
                 * 消息头
                 * </pre>
                 *
                 * <code>.Head head = 1;</code>
                 */
                public boolean hasHead() {
                    return headBuilder_ != null || head_ != null;
                }
                /**
                 * <pre>
                 * 消息头
                 * </pre>
                 *
                 * <code>.Head head = 1;</code>
                 */
                public MessageProtobuf.Head getHead() {
                    if (headBuilder_ == null) {
                        return head_ == null ? MessageProtobuf.Head.getDefaultInstance() : head_;
                    } else {
                        return headBuilder_.getMessage();
                    }
                }
                /**
                 * <pre>
                 * 消息头
                 * </pre>
                 *
                 * <code>.Head head = 1;</code>
                 */
                public MessageProtobuf.Msg.Builder setHead(MessageProtobuf.Head value) {
                    if (headBuilder_ == null) {
                        if (value == null) {
                            throw new NullPointerException();
                        }
                        head_ = value;
                        onChanged();
                    } else {
                        headBuilder_.setMessage(value);
                    }

                    return this;
                }
                /**
                 * <pre>
                 * 消息头
                 * </pre>
                 *
                 * <code>.Head head = 1;</code>
                 */
                public MessageProtobuf.Msg.Builder setHead(
                        MessageProtobuf.Head.Builder builderForValue) {
                    if (headBuilder_ == null) {
                        head_ = builderForValue.build();
                        onChanged();
                    } else {
                        headBuilder_.setMessage(builderForValue.build());
                    }

                    return this;
                }
                /**
                 * <pre>
                 * 消息头
                 * </pre>
                 *
                 * <code>.Head head = 1;</code>
                 */
                public MessageProtobuf.Msg.Builder mergeHead(MessageProtobuf.Head value) {
                    if (headBuilder_ == null) {
                        if (head_ != null) {
                            head_ =
                                    MessageProtobuf.Head.newBuilder(head_).mergeFrom(value).buildPartial();
                        } else {
                            head_ = value;
                        }
                        onChanged();
                    } else {
                        headBuilder_.mergeFrom(value);
                    }

                    return this;
                }
                /**
                 * <pre>
                 * 消息头
                 * </pre>
                 *
                 * <code>.Head head = 1;</code>
                 */
                public MessageProtobuf.Msg.Builder clearHead() {
                    if (headBuilder_ == null) {
                        head_ = null;
                        onChanged();
                    } else {
                        head_ = null;
                        headBuilder_ = null;
                    }

                    return this;
                }
                /**
                 * <pre>
                 * 消息头
                 * </pre>
                 *
                 * <code>.Head head = 1;</code>
                 */
                public MessageProtobuf.Head.Builder getHeadBuilder() {

                    onChanged();
                    return getHeadFieldBuilder().getBuilder();
                }
                /**
                 * <pre>
                 * 消息头
                 * </pre>
                 *
                 * <code>.Head head = 1;</code>
                 */
                public MessageProtobuf.HeadOrBuilder getHeadOrBuilder() {
                    if (headBuilder_ != null) {
                        return headBuilder_.getMessageOrBuilder();
                    } else {
                        return head_ == null ?
                                MessageProtobuf.Head.getDefaultInstance() : head_;
                    }
                }
                /**
                 * <pre>
                 * 消息头
                 * </pre>
                 *
                 * <code>.Head head = 1;</code>
                 */
                private com.google.protobuf.SingleFieldBuilderV3<
                        MessageProtobuf.Head, MessageProtobuf.Head.Builder, MessageProtobuf.HeadOrBuilder>
                getHeadFieldBuilder() {
                    if (headBuilder_ == null) {
                        headBuilder_ = new com.google.protobuf.SingleFieldBuilderV3<
                                MessageProtobuf.Head, MessageProtobuf.Head.Builder, MessageProtobuf.HeadOrBuilder>(
                                getHead(),
                                getParentForChildren(),
                                isClean());
                        head_ = null;
                    }
                    return headBuilder_;
                }

                private Object body_ = "";
                /**
                 * <pre>
                 * 消息体
                 * </pre>
                 *
                 * <code>string body = 2;</code>
                 */
                public String getBody() {
                    Object ref = body_;
                    if (!(ref instanceof String)) {
                        com.google.protobuf.ByteString bs =
                                (com.google.protobuf.ByteString) ref;
                        String s = bs.toStringUtf8();
                        body_ = s;
                        return s;
                    } else {
                        return (String) ref;
                    }
                }
                /**
                 * <pre>
                 * 消息体
                 * </pre>
                 *
                 * <code>string body = 2;</code>
                 */
                public com.google.protobuf.ByteString
                getBodyBytes() {
                    Object ref = body_;
                    if (ref instanceof String) {
                        com.google.protobuf.ByteString b =
                                com.google.protobuf.ByteString.copyFromUtf8(
                                        (String) ref);
                        body_ = b;
                        return b;
                    } else {
                        return (com.google.protobuf.ByteString) ref;
                    }
                }
                /**
                 * <pre>
                 * 消息体
                 * </pre>
                 *
                 * <code>string body = 2;</code>
                 */
                public MessageProtobuf.Msg.Builder setBody(
                        String value) {
                    if (value == null) {
                        throw new NullPointerException();
                    }

                    body_ = value;
                    onChanged();
                    return this;
                }
                /**
                 * <pre>
                 * 消息体
                 * </pre>
                 *
                 * <code>string body = 2;</code>
                 */
                public MessageProtobuf.Msg.Builder clearBody() {

                    body_ = getDefaultInstance().getBody();
                    onChanged();
                    return this;
                }
                /**
                 * <pre>
                 * 消息体
                 * </pre>
                 *
                 * <code>string body = 2;</code>
                 */
                public MessageProtobuf.Msg.Builder setBodyBytes(
                        com.google.protobuf.ByteString value) {
                    if (value == null) {
                        throw new NullPointerException();
                    }
                    checkByteStringIsUtf8(value);

                    body_ = value;
                    onChanged();
                    return this;
                }
                public final MessageProtobuf.Msg.Builder setUnknownFields(
                        final com.google.protobuf.UnknownFieldSet unknownFields) {
                    return super.setUnknownFieldsProto3(unknownFields);
                }

                public final MessageProtobuf.Msg.Builder mergeUnknownFields(
                        final com.google.protobuf.UnknownFieldSet unknownFields) {
                    return super.mergeUnknownFields(unknownFields);
                }


                // @@protoc_insertion_point(builder_scope:Msg)
            }

            // @@protoc_insertion_point(class_scope:Msg)
            private static final MessageProtobuf.Msg DEFAULT_INSTANCE;
            static {
                DEFAULT_INSTANCE = new MessageProtobuf.Msg();
            }

            public static MessageProtobuf.Msg getDefaultInstance() {
                return DEFAULT_INSTANCE;
            }

            private static final com.google.protobuf.Parser<MessageProtobuf.Msg>
                    PARSER = new com.google.protobuf.AbstractParser<MessageProtobuf.Msg>() {
                public MessageProtobuf.Msg parsePartialFrom(
                        com.google.protobuf.CodedInputStream input,
                        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
                        throws com.google.protobuf.InvalidProtocolBufferException {
                    return new MessageProtobuf.Msg(input, extensionRegistry);
                }
            };

            public static com.google.protobuf.Parser<MessageProtobuf.Msg> parser() {
                return PARSER;
            }

            @Override
            public com.google.protobuf.Parser<MessageProtobuf.Msg> getParserForType() {
                return PARSER;
            }

            public MessageProtobuf.Msg getDefaultInstanceForType() {
                return DEFAULT_INSTANCE;
            }

        }

        public interface HeadOrBuilder extends
                // @@protoc_insertion_point(interface_extends:Head)
                com.google.protobuf.MessageOrBuilder {

            /**
             * <pre>
             * 消息id
             * </pre>
             *
             * <code>string msgId = 1;</code>
             */
            String getMsgId();
            /**
             * <pre>
             * 消息id
             * </pre>
             *
             * <code>string msgId = 1;</code>
             */
            com.google.protobuf.ByteString
            getMsgIdBytes();

            /**
             * <pre>
             * 消息类型
             * </pre>
             *
             * <code>int32 msgType = 2;</code>
             */
            int getMsgType();

            /**
             * <pre>
             * 消息内容类型
             * </pre>
             *
             * <code>int32 msgContentType = 3;</code>
             */
            int getMsgContentType();

            /**
             * <pre>
             * 消息发送者id
             * </pre>
             *
             * <code>string fromId = 4;</code>
             */
            String getFromId();
            /**
             * <pre>
             * 消息发送者id
             * </pre>
             *
             * <code>string fromId = 4;</code>
             */
            com.google.protobuf.ByteString
            getFromIdBytes();

            /**
             * <pre>
             * 消息接收者id
             * </pre>
             *
             * <code>string toId = 5;</code>
             */
            String getToId();
            /**
             * <pre>
             * 消息接收者id
             * </pre>
             *
             * <code>string toId = 5;</code>
             */
            com.google.protobuf.ByteString
            getToIdBytes();

            /**
             * <pre>
             * 消息时间戳
             * </pre>
             *
             * <code>int64 timestamp = 6;</code>
             */
            long getTimestamp();

            /**
             * <pre>
             * 状态报告
             * </pre>
             *
             * <code>int32 statusReport = 7;</code>
             */
            int getStatusReport();

            /**
             * <pre>
             * 扩展字段，以key/value形式存放的json
             * </pre>
             *
             * <code>string extend = 8;</code>
             */
            String getExtend();
            /**
             * <pre>
             * 扩展字段，以key/value形式存放的json
             * </pre>
             *
             * <code>string extend = 8;</code>
             */
            com.google.protobuf.ByteString
            getExtendBytes();
        }
        /**
         * Protobuf type {@code Head}
         */
        public  static final class Head extends
                com.google.protobuf.GeneratedMessageV3 implements
                // @@protoc_insertion_point(message_implements:Head)
                MessageProtobuf.HeadOrBuilder {
            private static final long serialVersionUID = 0L;
            // Use Head.newBuilder() to construct.
            private Head(com.google.protobuf.GeneratedMessageV3.Builder<?> builder) {
                super(builder);
            }
            private Head() {
                msgId_ = "";
                msgType_ = 0;
                msgContentType_ = 0;
                fromId_ = "";
                toId_ = "";
                timestamp_ = 0L;
                statusReport_ = 0;
                extend_ = "";
            }

            @Override
            public final com.google.protobuf.UnknownFieldSet
            getUnknownFields() {
                return this.unknownFields;
            }
            private Head(
                    com.google.protobuf.CodedInputStream input,
                    com.google.protobuf.ExtensionRegistryLite extensionRegistry)
                    throws com.google.protobuf.InvalidProtocolBufferException {
                this();
                if (extensionRegistry == null) {
                    throw new NullPointerException();
                }
                int mutable_bitField0_ = 0;
                com.google.protobuf.UnknownFieldSet.Builder unknownFields =
                        com.google.protobuf.UnknownFieldSet.newBuilder();
                try {
                    boolean done = false;
                    while (!done) {
                        int tag = input.readTag();
                        switch (tag) {
                            case 0:
                                done = true;
                                break;
                            default: {
                                if (!parseUnknownFieldProto3(
                                        input, unknownFields, extensionRegistry, tag)) {
                                    done = true;
                                }
                                break;
                            }
                            case 10: {
                                String s = input.readStringRequireUtf8();

                                msgId_ = s;
                                break;
                            }
                            case 16: {

                                msgType_ = input.readInt32();
                                break;
                            }
                            case 24: {

                                msgContentType_ = input.readInt32();
                                break;
                            }
                            case 34: {
                                String s = input.readStringRequireUtf8();

                                fromId_ = s;
                                break;
                            }
                            case 42: {
                                String s = input.readStringRequireUtf8();

                                toId_ = s;
                                break;
                            }
                            case 48: {

                                timestamp_ = input.readInt64();
                                break;
                            }
                            case 56: {

                                statusReport_ = input.readInt32();
                                break;
                            }
                            case 66: {
                                String s = input.readStringRequireUtf8();

                                extend_ = s;
                                break;
                            }
                        }
                    }
                } catch (com.google.protobuf.InvalidProtocolBufferException e) {
                    throw e.setUnfinishedMessage(this);
                } catch (java.io.IOException e) {
                    throw new com.google.protobuf.InvalidProtocolBufferException(
                            e).setUnfinishedMessage(this);
                } finally {
                    this.unknownFields = unknownFields.build();
                    makeExtensionsImmutable();
                }
            }
            public static final com.google.protobuf.Descriptors.Descriptor
            getDescriptor() {
                return MessageProtobuf.internal_static_Head_descriptor;
            }

            protected FieldAccessorTable
            internalGetFieldAccessorTable() {
                return MessageProtobuf.internal_static_Head_fieldAccessorTable
                        .ensureFieldAccessorsInitialized(
                                MessageProtobuf.Head.class, MessageProtobuf.Head.Builder.class);
            }

            public static final int MSGID_FIELD_NUMBER = 1;
            private volatile Object msgId_;
            /**
             * <pre>
             * 消息id
             * </pre>
             *
             * <code>string msgId = 1;</code>
             */
            public String getMsgId() {
                Object ref = msgId_;
                if (ref instanceof String) {
                    return (String) ref;
                } else {
                    com.google.protobuf.ByteString bs =
                            (com.google.protobuf.ByteString) ref;
                    String s = bs.toStringUtf8();
                    msgId_ = s;
                    return s;
                }
            }
            /**
             * <pre>
             * 消息id
             * </pre>
             *
             * <code>string msgId = 1;</code>
             */
            public com.google.protobuf.ByteString
            getMsgIdBytes() {
                Object ref = msgId_;
                if (ref instanceof String) {
                    com.google.protobuf.ByteString b =
                            com.google.protobuf.ByteString.copyFromUtf8(
                                    (String) ref);
                    msgId_ = b;
                    return b;
                } else {
                    return (com.google.protobuf.ByteString) ref;
                }
            }

            public static final int MSGTYPE_FIELD_NUMBER = 2;
            private int msgType_;
            /**
             * <pre>
             * 消息类型
             * </pre>
             *
             * <code>int32 msgType = 2;</code>
             */
            public int getMsgType() {
                return msgType_;
            }

            public static final int MSGCONTENTTYPE_FIELD_NUMBER = 3;
            private int msgContentType_;
            /**
             * <pre>
             * 消息内容类型
             * </pre>
             *
             * <code>int32 msgContentType = 3;</code>
             */
            public int getMsgContentType() {
                return msgContentType_;
            }

            public static final int FROMID_FIELD_NUMBER = 4;
            private volatile Object fromId_;
            /**
             * <pre>
             * 消息发送者id
             * </pre>
             *
             * <code>string fromId = 4;</code>
             */
            public String getFromId() {
                Object ref = fromId_;
                if (ref instanceof String) {
                    return (String) ref;
                } else {
                    com.google.protobuf.ByteString bs =
                            (com.google.protobuf.ByteString) ref;
                    String s = bs.toStringUtf8();
                    fromId_ = s;
                    return s;
                }
            }
            /**
             * <pre>
             * 消息发送者id
             * </pre>
             *
             * <code>string fromId = 4;</code>
             */
            public com.google.protobuf.ByteString
            getFromIdBytes() {
                Object ref = fromId_;
                if (ref instanceof String) {
                    com.google.protobuf.ByteString b =
                            com.google.protobuf.ByteString.copyFromUtf8(
                                    (String) ref);
                    fromId_ = b;
                    return b;
                } else {
                    return (com.google.protobuf.ByteString) ref;
                }
            }

            public static final int TOID_FIELD_NUMBER = 5;
            private volatile Object toId_;
            /**
             * <pre>
             * 消息接收者id
             * </pre>
             *
             * <code>string toId = 5;</code>
             */
            public String getToId() {
                Object ref = toId_;
                if (ref instanceof String) {
                    return (String) ref;
                } else {
                    com.google.protobuf.ByteString bs =
                            (com.google.protobuf.ByteString) ref;
                    String s = bs.toStringUtf8();
                    toId_ = s;
                    return s;
                }
            }
            /**
             * <pre>
             * 消息接收者id
             * </pre>
             *
             * <code>string toId = 5;</code>
             */
            public com.google.protobuf.ByteString
            getToIdBytes() {
                Object ref = toId_;
                if (ref instanceof String) {
                    com.google.protobuf.ByteString b =
                            com.google.protobuf.ByteString.copyFromUtf8(
                                    (String) ref);
                    toId_ = b;
                    return b;
                } else {
                    return (com.google.protobuf.ByteString) ref;
                }
            }

            public static final int TIMESTAMP_FIELD_NUMBER = 6;
            private long timestamp_;
            /**
             * <pre>
             * 消息时间戳
             * </pre>
             *
             * <code>int64 timestamp = 6;</code>
             */
            public long getTimestamp() {
                return timestamp_;
            }

            public static final int STATUSREPORT_FIELD_NUMBER = 7;
            private int statusReport_;
            /**
             * <pre>
             * 状态报告
             * </pre>
             *
             * <code>int32 statusReport = 7;</code>
             */
            public int getStatusReport() {
                return statusReport_;
            }

            public static final int EXTEND_FIELD_NUMBER = 8;
            private volatile Object extend_;
            /**
             * <pre>
             * 扩展字段，以key/value形式存放的json
             * </pre>
             *
             * <code>string extend = 8;</code>
             */
            public String getExtend() {
                Object ref = extend_;
                if (ref instanceof String) {
                    return (String) ref;
                } else {
                    com.google.protobuf.ByteString bs =
                            (com.google.protobuf.ByteString) ref;
                    String s = bs.toStringUtf8();
                    extend_ = s;
                    return s;
                }
            }
            /**
             * <pre>
             * 扩展字段，以key/value形式存放的json
             * </pre>
             *
             * <code>string extend = 8;</code>
             */
            public com.google.protobuf.ByteString
            getExtendBytes() {
                Object ref = extend_;
                if (ref instanceof String) {
                    com.google.protobuf.ByteString b =
                            com.google.protobuf.ByteString.copyFromUtf8(
                                    (String) ref);
                    extend_ = b;
                    return b;
                } else {
                    return (com.google.protobuf.ByteString) ref;
                }
            }

            private byte memoizedIsInitialized = -1;
            public final boolean isInitialized() {
                byte isInitialized = memoizedIsInitialized;
                if (isInitialized == 1) return true;
                if (isInitialized == 0) return false;

                memoizedIsInitialized = 1;
                return true;
            }

            public void writeTo(com.google.protobuf.CodedOutputStream output)
                    throws java.io.IOException {
                if (!getMsgIdBytes().isEmpty()) {
                    com.google.protobuf.GeneratedMessageV3.writeString(output, 1, msgId_);
                }
                if (msgType_ != 0) {
                    output.writeInt32(2, msgType_);
                }
                if (msgContentType_ != 0) {
                    output.writeInt32(3, msgContentType_);
                }
                if (!getFromIdBytes().isEmpty()) {
                    com.google.protobuf.GeneratedMessageV3.writeString(output, 4, fromId_);
                }
                if (!getToIdBytes().isEmpty()) {
                    com.google.protobuf.GeneratedMessageV3.writeString(output, 5, toId_);
                }
                if (timestamp_ != 0L) {
                    output.writeInt64(6, timestamp_);
                }
                if (statusReport_ != 0) {
                    output.writeInt32(7, statusReport_);
                }
                if (!getExtendBytes().isEmpty()) {
                    com.google.protobuf.GeneratedMessageV3.writeString(output, 8, extend_);
                }
                unknownFields.writeTo(output);
            }

            public int getSerializedSize() {
                int size = memoizedSize;
                if (size != -1) return size;

                size = 0;
                if (!getMsgIdBytes().isEmpty()) {
                    size += com.google.protobuf.GeneratedMessageV3.computeStringSize(1, msgId_);
                }
                if (msgType_ != 0) {
                    size += com.google.protobuf.CodedOutputStream
                            .computeInt32Size(2, msgType_);
                }
                if (msgContentType_ != 0) {
                    size += com.google.protobuf.CodedOutputStream
                            .computeInt32Size(3, msgContentType_);
                }
                if (!getFromIdBytes().isEmpty()) {
                    size += com.google.protobuf.GeneratedMessageV3.computeStringSize(4, fromId_);
                }
                if (!getToIdBytes().isEmpty()) {
                    size += com.google.protobuf.GeneratedMessageV3.computeStringSize(5, toId_);
                }
                if (timestamp_ != 0L) {
                    size += com.google.protobuf.CodedOutputStream
                            .computeInt64Size(6, timestamp_);
                }
                if (statusReport_ != 0) {
                    size += com.google.protobuf.CodedOutputStream
                            .computeInt32Size(7, statusReport_);
                }
                if (!getExtendBytes().isEmpty()) {
                    size += com.google.protobuf.GeneratedMessageV3.computeStringSize(8, extend_);
                }
                size += unknownFields.getSerializedSize();
                memoizedSize = size;
                return size;
            }

            @Override
            public boolean equals(final Object obj) {
                if (obj == this) {
                    return true;
                }
                if (!(obj instanceof MessageProtobuf.Head)) {
                    return super.equals(obj);
                }
                MessageProtobuf.Head other = (MessageProtobuf.Head) obj;

                boolean result = true;
                result = result && getMsgId()
                        .equals(other.getMsgId());
                result = result && (getMsgType()
                        == other.getMsgType());
                result = result && (getMsgContentType()
                        == other.getMsgContentType());
                result = result && getFromId()
                        .equals(other.getFromId());
                result = result && getToId()
                        .equals(other.getToId());
                result = result && (getTimestamp()
                        == other.getTimestamp());
                result = result && (getStatusReport()
                        == other.getStatusReport());
                result = result && getExtend()
                        .equals(other.getExtend());
                result = result && unknownFields.equals(other.unknownFields);
                return result;
            }

            @Override
            public int hashCode() {
                if (memoizedHashCode != 0) {
                    return memoizedHashCode;
                }
                int hash = 41;
                hash = (19 * hash) + getDescriptor().hashCode();
                hash = (37 * hash) + MSGID_FIELD_NUMBER;
                hash = (53 * hash) + getMsgId().hashCode();
                hash = (37 * hash) + MSGTYPE_FIELD_NUMBER;
                hash = (53 * hash) + getMsgType();
                hash = (37 * hash) + MSGCONTENTTYPE_FIELD_NUMBER;
                hash = (53 * hash) + getMsgContentType();
                hash = (37 * hash) + FROMID_FIELD_NUMBER;
                hash = (53 * hash) + getFromId().hashCode();
                hash = (37 * hash) + TOID_FIELD_NUMBER;
                hash = (53 * hash) + getToId().hashCode();
                hash = (37 * hash) + TIMESTAMP_FIELD_NUMBER;
                hash = (53 * hash) + com.google.protobuf.Internal.hashLong(
                        getTimestamp());
                hash = (37 * hash) + STATUSREPORT_FIELD_NUMBER;
                hash = (53 * hash) + getStatusReport();
                hash = (37 * hash) + EXTEND_FIELD_NUMBER;
                hash = (53 * hash) + getExtend().hashCode();
                hash = (29 * hash) + unknownFields.hashCode();
                memoizedHashCode = hash;
                return hash;
            }

            public static MessageProtobuf.Head parseFrom(
                    java.nio.ByteBuffer data)
                    throws com.google.protobuf.InvalidProtocolBufferException {
                return PARSER.parseFrom(data);
            }
            public static MessageProtobuf.Head parseFrom(
                    java.nio.ByteBuffer data,
                    com.google.protobuf.ExtensionRegistryLite extensionRegistry)
                    throws com.google.protobuf.InvalidProtocolBufferException {
                return PARSER.parseFrom(data, extensionRegistry);
            }
            public static MessageProtobuf.Head parseFrom(
                    com.google.protobuf.ByteString data)
                    throws com.google.protobuf.InvalidProtocolBufferException {
                return PARSER.parseFrom(data);
            }
            public static MessageProtobuf.Head parseFrom(
                    com.google.protobuf.ByteString data,
                    com.google.protobuf.ExtensionRegistryLite extensionRegistry)
                    throws com.google.protobuf.InvalidProtocolBufferException {
                return PARSER.parseFrom(data, extensionRegistry);
            }
            public static MessageProtobuf.Head parseFrom(byte[] data)
                    throws com.google.protobuf.InvalidProtocolBufferException {
                return PARSER.parseFrom(data);
            }
            public static MessageProtobuf.Head parseFrom(
                    byte[] data,
                    com.google.protobuf.ExtensionRegistryLite extensionRegistry)
                    throws com.google.protobuf.InvalidProtocolBufferException {
                return PARSER.parseFrom(data, extensionRegistry);
            }
            public static MessageProtobuf.Head parseFrom(java.io.InputStream input)
                    throws java.io.IOException {
                return com.google.protobuf.GeneratedMessageV3
                        .parseWithIOException(PARSER, input);
            }
            public static MessageProtobuf.Head parseFrom(
                    java.io.InputStream input,
                    com.google.protobuf.ExtensionRegistryLite extensionRegistry)
                    throws java.io.IOException {
                return com.google.protobuf.GeneratedMessageV3
                        .parseWithIOException(PARSER, input, extensionRegistry);
            }
            public static MessageProtobuf.Head parseDelimitedFrom(java.io.InputStream input)
                    throws java.io.IOException {
                return com.google.protobuf.GeneratedMessageV3
                        .parseDelimitedWithIOException(PARSER, input);
            }
            public static MessageProtobuf.Head parseDelimitedFrom(
                    java.io.InputStream input,
                    com.google.protobuf.ExtensionRegistryLite extensionRegistry)
                    throws java.io.IOException {
                return com.google.protobuf.GeneratedMessageV3
                        .parseDelimitedWithIOException(PARSER, input, extensionRegistry);
            }
            public static MessageProtobuf.Head parseFrom(
                    com.google.protobuf.CodedInputStream input)
                    throws java.io.IOException {
                return com.google.protobuf.GeneratedMessageV3
                        .parseWithIOException(PARSER, input);
            }
            public static MessageProtobuf.Head parseFrom(
                    com.google.protobuf.CodedInputStream input,
                    com.google.protobuf.ExtensionRegistryLite extensionRegistry)
                    throws java.io.IOException {
                return com.google.protobuf.GeneratedMessageV3
                        .parseWithIOException(PARSER, input, extensionRegistry);
            }

            public MessageProtobuf.Head.Builder newBuilderForType() { return newBuilder(); }
            public static MessageProtobuf.Head.Builder newBuilder() {
                return DEFAULT_INSTANCE.toBuilder();
            }
            public static MessageProtobuf.Head.Builder newBuilder(MessageProtobuf.Head prototype) {
                return DEFAULT_INSTANCE.toBuilder().mergeFrom(prototype);
            }
            public MessageProtobuf.Head.Builder toBuilder() {
                return this == DEFAULT_INSTANCE
                        ? new MessageProtobuf.Head.Builder() : new MessageProtobuf.Head.Builder().mergeFrom(this);
            }

            @Override
            protected MessageProtobuf.Head.Builder newBuilderForType(
                    BuilderParent parent) {
                MessageProtobuf.Head.Builder builder = new MessageProtobuf.Head.Builder(parent);
                return builder;
            }
            /**
             * Protobuf type {@code Head}
             */
            public static final class Builder extends
                    com.google.protobuf.GeneratedMessageV3.Builder<MessageProtobuf.Head.Builder> implements
                    // @@protoc_insertion_point(builder_implements:Head)
                    MessageProtobuf.HeadOrBuilder {
                public static final com.google.protobuf.Descriptors.Descriptor
                getDescriptor() {
                    return MessageProtobuf.internal_static_Head_descriptor;
                }

                protected FieldAccessorTable
                internalGetFieldAccessorTable() {
                    return MessageProtobuf.internal_static_Head_fieldAccessorTable
                            .ensureFieldAccessorsInitialized(
                                    MessageProtobuf.Head.class, MessageProtobuf.Head.Builder.class);
                }

                // Construct using com.freddy.im.cn.stormbirds.stormim.imserver.protobuf.MessageProtobuf.Head.newBuilder()
                private Builder() {
                    maybeForceBuilderInitialization();
                }

                private Builder(
                        BuilderParent parent) {
                    super(parent);
                    maybeForceBuilderInitialization();
                }
                private void maybeForceBuilderInitialization() {
                    if (com.google.protobuf.GeneratedMessageV3
                            .alwaysUseFieldBuilders) {
                    }
                }
                public MessageProtobuf.Head.Builder clear() {
                    super.clear();
                    msgId_ = "";

                    msgType_ = 0;

                    msgContentType_ = 0;

                    fromId_ = "";

                    toId_ = "";

                    timestamp_ = 0L;

                    statusReport_ = 0;

                    extend_ = "";

                    return this;
                }

                public com.google.protobuf.Descriptors.Descriptor
                getDescriptorForType() {
                    return MessageProtobuf.internal_static_Head_descriptor;
                }

                public MessageProtobuf.Head getDefaultInstanceForType() {
                    return MessageProtobuf.Head.getDefaultInstance();
                }

                public MessageProtobuf.Head build() {
                    MessageProtobuf.Head result = buildPartial();
                    if (!result.isInitialized()) {
                        throw newUninitializedMessageException(result);
                    }
                    return result;
                }

                public MessageProtobuf.Head buildPartial() {
                    MessageProtobuf.Head result = new MessageProtobuf.Head(this);
                    result.msgId_ = msgId_;
                    result.msgType_ = msgType_;
                    result.msgContentType_ = msgContentType_;
                    result.fromId_ = fromId_;
                    result.toId_ = toId_;
                    result.timestamp_ = timestamp_;
                    result.statusReport_ = statusReport_;
                    result.extend_ = extend_;
                    onBuilt();
                    return result;
                }

                public MessageProtobuf.Head.Builder clone() {
                    return (MessageProtobuf.Head.Builder) super.clone();
                }
                public MessageProtobuf.Head.Builder setField(
                        com.google.protobuf.Descriptors.FieldDescriptor field,
                        Object value) {
                    return (MessageProtobuf.Head.Builder) super.setField(field, value);
                }
                public MessageProtobuf.Head.Builder clearField(
                        com.google.protobuf.Descriptors.FieldDescriptor field) {
                    return (MessageProtobuf.Head.Builder) super.clearField(field);
                }
                public MessageProtobuf.Head.Builder clearOneof(
                        com.google.protobuf.Descriptors.OneofDescriptor oneof) {
                    return (MessageProtobuf.Head.Builder) super.clearOneof(oneof);
                }
                public MessageProtobuf.Head.Builder setRepeatedField(
                        com.google.protobuf.Descriptors.FieldDescriptor field,
                        int index, Object value) {
                    return (MessageProtobuf.Head.Builder) super.setRepeatedField(field, index, value);
                }
                public MessageProtobuf.Head.Builder addRepeatedField(
                        com.google.protobuf.Descriptors.FieldDescriptor field,
                        Object value) {
                    return (MessageProtobuf.Head.Builder) super.addRepeatedField(field, value);
                }
                public MessageProtobuf.Head.Builder mergeFrom(com.google.protobuf.Message other) {
                    if (other instanceof MessageProtobuf.Head) {
                        return mergeFrom((MessageProtobuf.Head)other);
                    } else {
                        super.mergeFrom(other);
                        return this;
                    }
                }

                public MessageProtobuf.Head.Builder mergeFrom(MessageProtobuf.Head other) {
                    if (other == MessageProtobuf.Head.getDefaultInstance()) return this;
                    if (!other.getMsgId().isEmpty()) {
                        msgId_ = other.msgId_;
                        onChanged();
                    }
                    if (other.getMsgType() != 0) {
                        setMsgType(other.getMsgType());
                    }
                    if (other.getMsgContentType() != 0) {
                        setMsgContentType(other.getMsgContentType());
                    }
                    if (!other.getFromId().isEmpty()) {
                        fromId_ = other.fromId_;
                        onChanged();
                    }
                    if (!other.getToId().isEmpty()) {
                        toId_ = other.toId_;
                        onChanged();
                    }
                    if (other.getTimestamp() != 0L) {
                        setTimestamp(other.getTimestamp());
                    }
                    if (other.getStatusReport() != 0) {
                        setStatusReport(other.getStatusReport());
                    }
                    if (!other.getExtend().isEmpty()) {
                        extend_ = other.extend_;
                        onChanged();
                    }
                    this.mergeUnknownFields(other.unknownFields);
                    onChanged();
                    return this;
                }

                public final boolean isInitialized() {
                    return true;
                }

                public MessageProtobuf.Head.Builder mergeFrom(
                        com.google.protobuf.CodedInputStream input,
                        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
                        throws java.io.IOException {
                    MessageProtobuf.Head parsedMessage = null;
                    try {
                        parsedMessage = PARSER.parsePartialFrom(input, extensionRegistry);
                    } catch (com.google.protobuf.InvalidProtocolBufferException e) {
                        parsedMessage = (MessageProtobuf.Head) e.getUnfinishedMessage();
                        throw e.unwrapIOException();
                    } finally {
                        if (parsedMessage != null) {
                            mergeFrom(parsedMessage);
                        }
                    }
                    return this;
                }

                private Object msgId_ = "";
                /**
                 * <pre>
                 * 消息id
                 * </pre>
                 *
                 * <code>string msgId = 1;</code>
                 */
                public String getMsgId() {
                    Object ref = msgId_;
                    if (!(ref instanceof String)) {
                        com.google.protobuf.ByteString bs =
                                (com.google.protobuf.ByteString) ref;
                        String s = bs.toStringUtf8();
                        msgId_ = s;
                        return s;
                    } else {
                        return (String) ref;
                    }
                }
                /**
                 * <pre>
                 * 消息id
                 * </pre>
                 *
                 * <code>string msgId = 1;</code>
                 */
                public com.google.protobuf.ByteString
                getMsgIdBytes() {
                    Object ref = msgId_;
                    if (ref instanceof String) {
                        com.google.protobuf.ByteString b =
                                com.google.protobuf.ByteString.copyFromUtf8(
                                        (String) ref);
                        msgId_ = b;
                        return b;
                    } else {
                        return (com.google.protobuf.ByteString) ref;
                    }
                }
                /**
                 * <pre>
                 * 消息id
                 * </pre>
                 *
                 * <code>string msgId = 1;</code>
                 */
                public MessageProtobuf.Head.Builder setMsgId(
                        String value) {
                    if (value == null) {
                        throw new NullPointerException();
                    }

                    msgId_ = value;
                    onChanged();
                    return this;
                }
                /**
                 * <pre>
                 * 消息id
                 * </pre>
                 *
                 * <code>string msgId = 1;</code>
                 */
                public MessageProtobuf.Head.Builder clearMsgId() {

                    msgId_ = getDefaultInstance().getMsgId();
                    onChanged();
                    return this;
                }
                /**
                 * <pre>
                 * 消息id
                 * </pre>
                 *
                 * <code>string msgId = 1;</code>
                 */
                public MessageProtobuf.Head.Builder setMsgIdBytes(
                        com.google.protobuf.ByteString value) {
                    if (value == null) {
                        throw new NullPointerException();
                    }
                    checkByteStringIsUtf8(value);

                    msgId_ = value;
                    onChanged();
                    return this;
                }

                private int msgType_ ;
                /**
                 * <pre>
                 * 消息类型
                 * </pre>
                 *
                 * <code>int32 msgType = 2;</code>
                 */
                public int getMsgType() {
                    return msgType_;
                }
                /**
                 * <pre>
                 * 消息类型
                 * </pre>
                 *
                 * <code>int32 msgType = 2;</code>
                 */
                public MessageProtobuf.Head.Builder setMsgType(int value) {

                    msgType_ = value;
                    onChanged();
                    return this;
                }
                /**
                 * <pre>
                 * 消息类型
                 * </pre>
                 *
                 * <code>int32 msgType = 2;</code>
                 */
                public MessageProtobuf.Head.Builder clearMsgType() {

                    msgType_ = 0;
                    onChanged();
                    return this;
                }

                private int msgContentType_ ;
                /**
                 * <pre>
                 * 消息内容类型
                 * </pre>
                 *
                 * <code>int32 msgContentType = 3;</code>
                 */
                public int getMsgContentType() {
                    return msgContentType_;
                }
                /**
                 * <pre>
                 * 消息内容类型
                 * </pre>
                 *
                 * <code>int32 msgContentType = 3;</code>
                 */
                public MessageProtobuf.Head.Builder setMsgContentType(int value) {

                    msgContentType_ = value;
                    onChanged();
                    return this;
                }
                /**
                 * <pre>
                 * 消息内容类型
                 * </pre>
                 *
                 * <code>int32 msgContentType = 3;</code>
                 */
                public MessageProtobuf.Head.Builder clearMsgContentType() {

                    msgContentType_ = 0;
                    onChanged();
                    return this;
                }

                private Object fromId_ = "";
                /**
                 * <pre>
                 * 消息发送者id
                 * </pre>
                 *
                 * <code>string fromId = 4;</code>
                 */
                public String getFromId() {
                    Object ref = fromId_;
                    if (!(ref instanceof String)) {
                        com.google.protobuf.ByteString bs =
                                (com.google.protobuf.ByteString) ref;
                        String s = bs.toStringUtf8();
                        fromId_ = s;
                        return s;
                    } else {
                        return (String) ref;
                    }
                }
                /**
                 * <pre>
                 * 消息发送者id
                 * </pre>
                 *
                 * <code>string fromId = 4;</code>
                 */
                public com.google.protobuf.ByteString
                getFromIdBytes() {
                    Object ref = fromId_;
                    if (ref instanceof String) {
                        com.google.protobuf.ByteString b =
                                com.google.protobuf.ByteString.copyFromUtf8(
                                        (String) ref);
                        fromId_ = b;
                        return b;
                    } else {
                        return (com.google.protobuf.ByteString) ref;
                    }
                }
                /**
                 * <pre>
                 * 消息发送者id
                 * </pre>
                 *
                 * <code>string fromId = 4;</code>
                 */
                public MessageProtobuf.Head.Builder setFromId(
                        String value) {
                    if (value == null) {
                        throw new NullPointerException();
                    }

                    fromId_ = value;
                    onChanged();
                    return this;
                }
                /**
                 * <pre>
                 * 消息发送者id
                 * </pre>
                 *
                 * <code>string fromId = 4;</code>
                 */
                public MessageProtobuf.Head.Builder clearFromId() {

                    fromId_ = getDefaultInstance().getFromId();
                    onChanged();
                    return this;
                }
                /**
                 * <pre>
                 * 消息发送者id
                 * </pre>
                 *
                 * <code>string fromId = 4;</code>
                 */
                public MessageProtobuf.Head.Builder setFromIdBytes(
                        com.google.protobuf.ByteString value) {
                    if (value == null) {
                        throw new NullPointerException();
                    }
                    checkByteStringIsUtf8(value);

                    fromId_ = value;
                    onChanged();
                    return this;
                }

                private Object toId_ = "";
                /**
                 * <pre>
                 * 消息接收者id
                 * </pre>
                 *
                 * <code>string toId = 5;</code>
                 */
                public String getToId() {
                    Object ref = toId_;
                    if (!(ref instanceof String)) {
                        com.google.protobuf.ByteString bs =
                                (com.google.protobuf.ByteString) ref;
                        String s = bs.toStringUtf8();
                        toId_ = s;
                        return s;
                    } else {
                        return (String) ref;
                    }
                }
                /**
                 * <pre>
                 * 消息接收者id
                 * </pre>
                 *
                 * <code>string toId = 5;</code>
                 */
                public com.google.protobuf.ByteString
                getToIdBytes() {
                    Object ref = toId_;
                    if (ref instanceof String) {
                        com.google.protobuf.ByteString b =
                                com.google.protobuf.ByteString.copyFromUtf8(
                                        (String) ref);
                        toId_ = b;
                        return b;
                    } else {
                        return (com.google.protobuf.ByteString) ref;
                    }
                }
                /**
                 * <pre>
                 * 消息接收者id
                 * </pre>
                 *
                 * <code>string toId = 5;</code>
                 */
                public MessageProtobuf.Head.Builder setToId(
                        String value) {
                    if (value == null) {
                        throw new NullPointerException();
                    }

                    toId_ = value;
                    onChanged();
                    return this;
                }
                /**
                 * <pre>
                 * 消息接收者id
                 * </pre>
                 *
                 * <code>string toId = 5;</code>
                 */
                public MessageProtobuf.Head.Builder clearToId() {

                    toId_ = getDefaultInstance().getToId();
                    onChanged();
                    return this;
                }
                /**
                 * <pre>
                 * 消息接收者id
                 * </pre>
                 *
                 * <code>string toId = 5;</code>
                 */
                public MessageProtobuf.Head.Builder setToIdBytes(
                        com.google.protobuf.ByteString value) {
                    if (value == null) {
                        throw new NullPointerException();
                    }
                    checkByteStringIsUtf8(value);

                    toId_ = value;
                    onChanged();
                    return this;
                }

                private long timestamp_ ;
                /**
                 * <pre>
                 * 消息时间戳
                 * </pre>
                 *
                 * <code>int64 timestamp = 6;</code>
                 */
                public long getTimestamp() {
                    return timestamp_;
                }
                /**
                 * <pre>
                 * 消息时间戳
                 * </pre>
                 *
                 * <code>int64 timestamp = 6;</code>
                 */
                public MessageProtobuf.Head.Builder setTimestamp(long value) {

                    timestamp_ = value;
                    onChanged();
                    return this;
                }
                /**
                 * <pre>
                 * 消息时间戳
                 * </pre>
                 *
                 * <code>int64 timestamp = 6;</code>
                 */
                public MessageProtobuf.Head.Builder clearTimestamp() {

                    timestamp_ = 0L;
                    onChanged();
                    return this;
                }

                private int statusReport_ ;
                /**
                 * <pre>
                 * 状态报告
                 * </pre>
                 *
                 * <code>int32 statusReport = 7;</code>
                 */
                public int getStatusReport() {
                    return statusReport_;
                }
                /**
                 * <pre>
                 * 状态报告
                 * </pre>
                 *
                 * <code>int32 statusReport = 7;</code>
                 */
                public MessageProtobuf.Head.Builder setStatusReport(int value) {

                    statusReport_ = value;
                    onChanged();
                    return this;
                }
                /**
                 * <pre>
                 * 状态报告
                 * </pre>
                 *
                 * <code>int32 statusReport = 7;</code>
                 */
                public MessageProtobuf.Head.Builder clearStatusReport() {

                    statusReport_ = 0;
                    onChanged();
                    return this;
                }

                private Object extend_ = "";
                /**
                 * <pre>
                 * 扩展字段，以key/value形式存放的json
                 * </pre>
                 *
                 * <code>string extend = 8;</code>
                 */
                public String getExtend() {
                    Object ref = extend_;
                    if (!(ref instanceof String)) {
                        com.google.protobuf.ByteString bs =
                                (com.google.protobuf.ByteString) ref;
                        String s = bs.toStringUtf8();
                        extend_ = s;
                        return s;
                    } else {
                        return (String) ref;
                    }
                }
                /**
                 * <pre>
                 * 扩展字段，以key/value形式存放的json
                 * </pre>
                 *
                 * <code>string extend = 8;</code>
                 */
                public com.google.protobuf.ByteString
                getExtendBytes() {
                    Object ref = extend_;
                    if (ref instanceof String) {
                        com.google.protobuf.ByteString b =
                                com.google.protobuf.ByteString.copyFromUtf8(
                                        (String) ref);
                        extend_ = b;
                        return b;
                    } else {
                        return (com.google.protobuf.ByteString) ref;
                    }
                }
                /**
                 * <pre>
                 * 扩展字段，以key/value形式存放的json
                 * </pre>
                 *
                 * <code>string extend = 8;</code>
                 */
                public MessageProtobuf.Head.Builder setExtend(
                        String value) {
                    if (value == null) {
                        throw new NullPointerException();
                    }

                    extend_ = value;
                    onChanged();
                    return this;
                }
                /**
                 * <pre>
                 * 扩展字段，以key/value形式存放的json
                 * </pre>
                 *
                 * <code>string extend = 8;</code>
                 */
                public MessageProtobuf.Head.Builder clearExtend() {

                    extend_ = getDefaultInstance().getExtend();
                    onChanged();
                    return this;
                }
                /**
                 * <pre>
                 * 扩展字段，以key/value形式存放的json
                 * </pre>
                 *
                 * <code>string extend = 8;</code>
                 */
                public MessageProtobuf.Head.Builder setExtendBytes(
                        com.google.protobuf.ByteString value) {
                    if (value == null) {
                        throw new NullPointerException();
                    }
                    checkByteStringIsUtf8(value);

                    extend_ = value;
                    onChanged();
                    return this;
                }
                public final MessageProtobuf.Head.Builder setUnknownFields(
                        final com.google.protobuf.UnknownFieldSet unknownFields) {
                    return super.setUnknownFieldsProto3(unknownFields);
                }

                public final MessageProtobuf.Head.Builder mergeUnknownFields(
                        final com.google.protobuf.UnknownFieldSet unknownFields) {
                    return super.mergeUnknownFields(unknownFields);
                }


                // @@protoc_insertion_point(builder_scope:Head)
            }

            // @@protoc_insertion_point(class_scope:Head)
            private static final MessageProtobuf.Head DEFAULT_INSTANCE;
            static {
                DEFAULT_INSTANCE = new MessageProtobuf.Head();
            }

            public static MessageProtobuf.Head getDefaultInstance() {
                return DEFAULT_INSTANCE;
            }

            private static final com.google.protobuf.Parser<MessageProtobuf.Head>
                    PARSER = new com.google.protobuf.AbstractParser<MessageProtobuf.Head>() {
                public MessageProtobuf.Head parsePartialFrom(
                        com.google.protobuf.CodedInputStream input,
                        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
                        throws com.google.protobuf.InvalidProtocolBufferException {
                    return new MessageProtobuf.Head(input, extensionRegistry);
                }
            };

            public static com.google.protobuf.Parser<MessageProtobuf.Head> parser() {
                return PARSER;
            }

            @Override
            public com.google.protobuf.Parser<MessageProtobuf.Head> getParserForType() {
                return PARSER;
            }

            public MessageProtobuf.Head getDefaultInstanceForType() {
                return DEFAULT_INSTANCE;
            }

        }

        private static final com.google.protobuf.Descriptors.Descriptor
                internal_static_Msg_descriptor;
        private static final
        com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
                internal_static_Msg_fieldAccessorTable;
        private static final com.google.protobuf.Descriptors.Descriptor
                internal_static_Head_descriptor;
        private static final
        com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
                internal_static_Head_fieldAccessorTable;

        public static com.google.protobuf.Descriptors.FileDescriptor
        getDescriptor() {
            return descriptor;
        }
        private static  com.google.protobuf.Descriptors.FileDescriptor
                descriptor;
        static {
            String[] descriptorData = {
                    "\n\tmsg.proto\"(\n\003Msg\022\023\n\004head\030\001 \001(\0132\005.Head\022" +
                            "\014\n\004body\030\002 \001(\t\"\225\001\n\004Head\022\r\n\005msgId\030\001 \001(\t\022\017\n" +
                            "\007msgType\030\002 \001(\005\022\026\n\016msgContentType\030\003 \001(\005\022\016" +
                            "\n\006fromId\030\004 \001(\t\022\014\n\004toId\030\005 \001(\t\022\021\n\ttimestam" +
                            "p\030\006 \001(\003\022\024\n\014statusReport\030\007 \001(\005\022\016\n\006extend\030" +
                            "\010 \001(\tB)\n\026com.freddy.im.protobufB\017Message" +
                            "Protobufb\006proto3"
            };
            com.google.protobuf.Descriptors.FileDescriptor.InternalDescriptorAssigner assigner =
                    new com.google.protobuf.Descriptors.FileDescriptor.    InternalDescriptorAssigner() {
                        public com.google.protobuf.ExtensionRegistry assignDescriptors(
                                com.google.protobuf.Descriptors.FileDescriptor root) {
                            descriptor = root;
                            return null;
                        }
                    };
            com.google.protobuf.Descriptors.FileDescriptor
                    .internalBuildGeneratedFileFrom(descriptorData,
                            new com.google.protobuf.Descriptors.FileDescriptor[] {
                            }, assigner);
            internal_static_Msg_descriptor =
                    getDescriptor().getMessageTypes().get(0);
            internal_static_Msg_fieldAccessorTable = new
                    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
                    internal_static_Msg_descriptor,
                    new String[] { "Head", "Body", });
            internal_static_Head_descriptor =
                    getDescriptor().getMessageTypes().get(1);
            internal_static_Head_fieldAccessorTable = new
                    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
                    internal_static_Head_descriptor,
                    new String[] { "MsgId", "MsgType", "MsgContentType", "FromId", "ToId", "Timestamp", "StatusReport", "Extend", });
        }

        // @@protoc_insertion_point(outer_class_scope)
    }
}

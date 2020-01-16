package cn.stormbirds.stormim.imbenchmark;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.protocol.java.sampler.AbstractJavaSamplerClient;
import org.apache.jmeter.protocol.java.sampler.JavaSamplerContext;
import org.apache.jmeter.samplers.SampleResult;

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


    public SampleResult runTest(JavaSamplerContext javaSamplerContext) {
        SampleResult sp = new SampleResult(); //采样结果

        UUID uuid = UUID.randomUUID();
        String key = uuid.toString();

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
        bootstrap.handler(new TCPChannelInitializerHandler(key,""));

        String currentHost = javaSamplerContext.getParameter("host", "");
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

    @Override
    public void setupTest(JavaSamplerContext context) {

        super.setupTest(context);
    }

    @Override
    public void teardownTest(JavaSamplerContext context) {
        super.teardownTest(context);
    }

    @Override
    public Arguments getDefaultParameters() {
        Arguments arguments = super.getDefaultParameters();
        arguments.addArgument("host","127.0.0.1");
        arguments.addArgument("port","8855");
        return arguments;
    }

}

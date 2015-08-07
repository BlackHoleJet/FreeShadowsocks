package glacier.Shadowsocks;

import org.apache.log4j.Logger;

public class SSARefresh
{
    private static Logger logger = Logger.getLogger(SSARefresh.class.getName());
    
    public static void main(String[] args) throws Exception
    {
        ProxyWatcherThread executor = new ProxyWatcherThread();
        executor.startMonitor();
        logger.info("start monitor...");
    }
}

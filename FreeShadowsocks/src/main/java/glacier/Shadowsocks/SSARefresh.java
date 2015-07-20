package glacier.Shadowsocks;

public class SSARefresh
{
    public static void main(String[] args) throws Exception
    {
        ProxyWatcherThread executor = new ProxyWatcherThread();
        executor.startMonitor();
    }
}

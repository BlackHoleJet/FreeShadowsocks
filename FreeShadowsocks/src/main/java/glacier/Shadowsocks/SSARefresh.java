package glacier.Shadowsocks;

import it.sauronsoftware.junique.AlreadyLockedException;
import it.sauronsoftware.junique.JUnique;

public class SSARefresh
{
    public static void main(String[] args) throws Exception
    {
        String appName = "shadowsocks_auto_refresher";
        boolean alreadyRunning;
        try {
            JUnique.acquireLock(appName);
            alreadyRunning = false;
        } catch (AlreadyLockedException e) {
            alreadyRunning = true;
        }
        if (!alreadyRunning) {
            ProxyWatcherThread executor = new ProxyWatcherThread();
            executor.startMonitor();
        }
    }
}

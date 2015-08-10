package glacier.Shadowsocks;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Proxy.Type;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Properties;

import net.sf.json.JSONObject;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

public class ProxyWatcherThread extends Thread
{
    private final SSConfig config = new SSConfig();
    private PasswordSolver solver;
    private String fileContent;
    private RestOperations proxyClient;
    private int retry = 0;
    private final int retryInterval = 60*1000;
    private final int errorRetryInterval = 3000;
    private final Object proxyChecking = new Object();
    private Logger logger = Logger.getLogger(this.getClass().getName());
    
    public void startMonitor() throws IllegalArgumentException, IllegalAccessException, NoSuchAlgorithmException, IOException, InterruptedException
    {
        logger.info(System.getProperty("os.name"));
        init(config);
        fileContent = readJsonFile(config.getConfig());
        solver = new PasswordSolver(fileContent);
        
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        JSONObject fileJson = JSONObject.fromObject(fileContent);
        int localPort = getLocalPort(fileJson);
        Proxy proxy= new Proxy(Type.HTTP, new InetSocketAddress("localhost", localPort));
        requestFactory.setProxy(proxy);
        requestFactory.setConnectTimeout(10000);
        requestFactory.setReadTimeout(10000);
        proxyClient = new RestTemplate(requestFactory);
        
        solver.start();
        synchronized (solver)
        {
            logger.info("waiting for PasswordSolver");
            solver.wait();
            logger.info("PasswordSolver done, start proxy checking");
        }
        this.start();
    }
    
    private void init(SSConfig config) throws IOException, IllegalArgumentException, IllegalAccessException, NoSuchAlgorithmException
    {
        InputStream is = this.getClass().getResourceAsStream("config.properties");
        if(is == null)
            return;
        BufferedReader br = new BufferedReader(new InputStreamReader(is, Charset.forName("utf8")));
        Properties p = new Properties();
        p.load(br);
        Field[] fields = config.getClass().getDeclaredFields();
        for (int i = 0; i < fields.length; i++)
        {
            if (!fields[i].getType().getName().equals("java.lang.String"))
                continue;
            fields[i].set(config, p.getProperty(fields[i].getName(), fields[i].get(config).toString()));
        }
        
        if (config.getIsEncoded().equals("false"))
        {
            byte[] bytesOfMessage = config.getPassword().getBytes("UTF-8");
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] thedigest = md.digest(bytesOfMessage);
            config.setPassword(new String(Hex.encodeHex(thedigest)));
        }
    }
    
    public String readJsonFile(String fileName) throws IOException
    {
        File filename = new File(fileName);
        InputStreamReader reader = new InputStreamReader(new FileInputStream(filename));
        BufferedReader br = new BufferedReader(reader);
        StringBuffer buf = new StringBuffer();
        String line = "";
        line = br.readLine();
        while (line != null)
        {
            buf.append(line);
            line = br.readLine();
        }
        reader.close();
        br.close();
        return buf.toString();
    }
    
    private int getLocalPort(JSONObject fileJson)
    {
        return fileJson.getInt("localPort");
    }
    
    @Override
    public void run()
    {
        setName("proxyChecking");
        while (true)
        {
            try
            {
                ResponseEntity<String> result = proxyClient.exchange("http://www.baidu.com", HttpMethod.GET, null, String.class);
                if (result.getStatusCode().value() != 200)
                    throw new RuntimeException("request to baidu not return 200");
                retry = 0;
                logger.info("request through proxy ok.");
                sleep(retryInterval);
            } catch (Exception e)
            {
                try
                {
                    logger.error(e.getMessage(), e);
                    ++retry;
                    if(retry >= 3)
                    {
                        synchronized (proxyChecking)
                        {
                            logger.info("notify PasswordSolver");
                            proxyChecking.notify();
                        }
                        synchronized (solver)
                        {
                            logger.info("proxy checking waiting for PasswordSolver");
                            solver.wait();
                            logger.info("PasswordSolver finished, proxy checking awake");
                        }
                        retry = 0;
                    }
                    else
                        sleep(errorRetryInterval);
                } catch (InterruptedException Interrupted)
                {
                    solver.interrupt();
                    break;
                }
            }            
        }
    }
    
    
    public class PasswordSolver extends Thread
    {
        private RestOperations restClient = new RestTemplate();
        private String server;
        private String fileContent = null;
        
        public PasswordSolver(String fileConfig)
        {
            fileContent = fileConfig;
        }
        
        @Override
        public void run()
        {
            setName("PasswordSolver");
            while (true)
            {
                try
                {
                    logger.info("PasswordSolver thread start working...");
                    JSONObject fileJson = JSONObject.fromObject(fileContent);
                    JSONObject json = fileJson.getJSONArray("configs").getJSONObject(0);
                    server = json.getString("server");
                    logger.info("server="+server);
                    if (StringUtils.isBlank(server))
                        throw new RuntimeException("解析配置文件出错");
                    logger.info("PasswordSolver thread try to get password");
                    String pwd = getFreePassword();
                    logger.info("pwd="+pwd);
                    if (pwd == null)
                        throw new RuntimeException("密码获取错误");
                    json.put("server", server);
                    json.put("password", pwd);
                    fileContent = fileJson.toString();
                    writeJsonFile();
                    killShadowsocks();
                    Thread.sleep(1000);
                    Runtime.getRuntime().exec(config.getExe());
                    checkTask();
                    synchronized (this)
                    {
                        logger.info("notify proxy checking thread");
                        notify();
                    }
                    synchronized (proxyChecking)
                    {
                        logger.info("PasswordSolver wait for awake");
                        proxyChecking.wait();
                        logger.info("PasswordSolver awake");
                    }
                } catch (Exception e)
                {
                    logger.error(e.getMessage(), e);
                    try
                    {
                        sleep(retryInterval);
                    } catch (InterruptedException Interrupted)
                    {
                        break;
                    }
                }
                
            }
        }
        
        public String getFreePassword() throws Exception
        {
            String content = getServerInfo();
            Document document = Jsoup.parse(content);
            Elements trs = document.select("table").select("tr");
            for (int i = 0; i < trs.size(); i++)
            {
                Elements tds = trs.get(i).select("td");
                for (int j = 0; j < tds.size(); j++)
                {
                    String text = tds.get(j).text();
                    if (server.equals(text))
                        return tds.get(j + 2).text();
                }
            }
            if(trs.size() == 0)
                return null;
            Elements tds = trs.get(1).select("td");
            if(tds.size() == 0)
                return null;
            server = tds.get(1).text();
            return tds.get(3).text();
            
        }
        
        private String getServerInfo() throws Exception
        {
            String cookie = getCookie();
            return getServerInfo(cookie);
        }
        
        private String getServerInfo(String cookie)
        {
            String url = UriComponentsBuilder.fromHttpUrl(config.getHost()).path("my/free").build().toUriString();
            HttpHeaders requestHeaders = new HttpHeaders();
            requestHeaders = new HttpHeaders();
            requestHeaders.set("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:18.0) Gecko/20100101 Firefox/18.0");
            requestHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            requestHeaders.add("X-Requested-With", "XMLHttpRequest");
            requestHeaders.add("Cookie", cookie);
            HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<MultiValueMap<String, String>>(null, requestHeaders);
            ResponseEntity<String> response = restClient.exchange(url, HttpMethod.GET, requestEntity, String.class);
            return response.getBody();
        }
        
        private String getCookie()
        {
            String url = UriComponentsBuilder.fromHttpUrl(config.getHost()).path("/login").build().toUriString();
            HttpHeaders requestHeaders = new HttpHeaders();
            requestHeaders.add("Accept", "text/plain, */*; q=0.01");
            requestHeaders.add("Accept-Encoding", "gzip, deflate");
            requestHeaders.add("Accept-Language", "zh-CN,zh;q=0.8,en;q=0.6,zh-TW;q=0.4");
            requestHeaders.add("Connection", "keep-alive");
            requestHeaders.add("Content-Length", "98");
            requestHeaders.add("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            requestHeaders.add("Origin", "http://www.ss-link.com");
            requestHeaders.add("Host", "www.ss-link.com");
            requestHeaders.add("Referer", "http://www.ss-link.com/login?redirect=/my/free");
            requestHeaders.set("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/43.0.2357.134 Safari/537.36");
            requestHeaders.add("X-Requested-With", "XMLHttpRequest");

            MultiValueMap<String, String> formData = new LinkedMultiValueMap<String, String>();
            formData.add("email", config.getEmail());
            formData.add("password", config.getPassword());
            formData.add("redirect", "/my");

            HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<MultiValueMap<String, String>>(formData, requestHeaders);

            ResponseEntity<String> response = restClient.exchange(url, HttpMethod.POST, requestEntity, String.class);
            List<String> cookies = response.getHeaders().get("Set-Cookie");
            String cookie = null;
            for (String c : cookies)
            {
                String[] line = c.split(";");
                for (String item : line)
                {
                    if (StringUtils.isNotBlank(item) && item.split("=")[0].equals("webpy_session_id"))
                    {
                        cookie = item;
                        break;
                    }
                }
            }
            Assert.notNull(cookie, "获取Cookie失败");
            logger.info("cookie="+cookie);
            return cookie;
        }
        
        public void writeJsonFile() throws IOException
        {
            File writename = new File(config.getConfig());
            writename.createNewFile();
            BufferedWriter out = new BufferedWriter(new FileWriter(writename));
            out.write(fileContent);
            out.flush();
            out.close();
        }
        
        public void killShadowsocks() throws IOException
        {
            String cmd = "taskkill /F /IM " + config.getExe();
            Runtime.getRuntime().exec(cmd);
        }
        
        public void checkTask() throws IOException
        {
            String cmd = "tasklist /FO CSV /FI \"IMAGENAME eq " + config.getExe() + "\"";
            Process process = Runtime.getRuntime().exec(cmd);
            BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream(), Charset.forName("GBK")));
            String line = null;
            boolean exist = false;
            while ((line = br.readLine()) != null)
            {
                if (line.contains(config.getExe()))
                {
                    exist = true;
                    break;
                }
            }
            if (!exist)
                throw new RuntimeException("启动进程失败- -");
        }
    }
}

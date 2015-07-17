package glacier.Shadowsocks;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.util.List;
import java.util.Properties;

import javax.swing.JOptionPane;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import net.sf.json.JSONObject;

public class SSARefresh
{
    private String email = "fonswanefon@gmai.com";
    private String password = "38777b2ef1cda076e3a59f70c3d790c0";
    private String exe = "Shadowsocks.exe";
    private String config = "gui-config.json";
    private String isEncoded = "true";
    private String host = "http://www.ss-link.com";
    private RestOperations restClient = new RestTemplate();

    public static void main(String[] args) throws Exception
    {
        try
        {
            SSARefresh instance = new SSARefresh();
            instance.init();
            if (instance.isEncoded.equals("false"))
            {
                byte[] bytesOfMessage = instance.password.getBytes("UTF-8");
                MessageDigest md = MessageDigest.getInstance("MD5");
                byte[] thedigest = md.digest(bytesOfMessage);
                instance.password = new String(Hex.encodeHex(thedigest));
            }
            String fileContent = instance.readJsonFile();
            JSONObject fileJson = JSONObject.fromObject(fileContent);
            JSONObject json = fileJson.getJSONArray("configs").getJSONObject(0);
            String server = json.getString("server");
            if (StringUtils.isBlank(server))
                throw new RuntimeException("解析配置文件出错");
            String pwd = instance.getFreePassword(server);
            if (pwd == null)
                throw new RuntimeException("密码获取错误");
            json.put("password", pwd);
            instance.writeJsonFile(fileJson.toString());
            instance.killShadowsocks();
            Thread.sleep(100);
            Runtime.getRuntime().exec(instance.exe);
            instance.ckeckTask();
        } catch (Exception e)
        {
            JOptionPane.showMessageDialog(null, e.getMessage(), "出错啦T.T", JOptionPane.ERROR_MESSAGE);
        }
    }

    public void ckeckTask() throws IOException
    {
        String cmd = "tasklist /FO CSV /FI \"IMAGENAME eq " + exe + "\"";
        Process process = Runtime.getRuntime().exec(cmd);
        BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream(), Charset.forName("GBK")));
        String line = null;
        boolean exist = false;
        while ((line = br.readLine()) != null)
        {
            if (line.contains(exe))
            {
                exist = true;
                break;
            }
        }
        if (!exist)
            throw new RuntimeException("启动进程失败- -");
    }

    private void init() throws IOException, IllegalArgumentException, IllegalAccessException
    {
        BufferedReader br = new BufferedReader(new InputStreamReader(this.getClass().getResourceAsStream("config.properties"), Charset.forName("utf8")));
        Properties p = new Properties();
        p.load(br);
        Field[] fields = this.getClass().getDeclaredFields();
        for (int i = 0; i < fields.length; i++)
        {
            if (!fields[i].getType().getName().equals("java.lang.String"))
                continue;
            fields[i].set(this, p.getProperty(fields[i].getName(), fields[i].get(this).toString()));
        }
    }

    public void killShadowsocks() throws IOException
    {
        String cmd = "taskkill /F /IM " + exe;
        Runtime.getRuntime().exec(cmd);
    }

    public String getFreePassword(String server) throws Exception
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
        return null;
    }

    private String getServerInfo() throws Exception
    {
        String cookie = getCookie();
        return getServerInfo(cookie);
    }
    
    private String getServerInfo(String cookie)
    {
        String url = UriComponentsBuilder.fromHttpUrl(host).path("my/free").build().toUriString();
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
        String url = UriComponentsBuilder.fromHttpUrl(host).path("/login").build().toUriString();
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
        formData.add("email", email);
        formData.add("password", password);
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
        return cookie;
    }

    public String readJsonFile() throws IOException
    {
        File filename = new File(config);
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

    public void writeJsonFile(String fileContent) throws IOException
    {
        File writename = new File(config);
        writename.createNewFile();
        BufferedWriter out = new BufferedWriter(new FileWriter(writename));
        out.write(fileContent);
        out.flush();
        out.close();
    }
}

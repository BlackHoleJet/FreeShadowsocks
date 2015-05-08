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
import java.util.Properties;

import javax.swing.JOptionPane;

import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import net.sf.json.JSONObject;

public class SSARefresh 
{
//    private String userAgent = "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/42.0.2311.135 Safari/537.36";
    private String email = "fonswanefon@gmai.com";
    private String password = "38777b2ef1cda076e3a59f70c3d790c0";
    private String exe = "Shadowsocks.exe";
    private String config = "gui-config.json";
    
    public static void main(String[] args) throws Exception
    {
        try
        {
            SSARefresh instance = new SSARefresh();
            instance.init();
            String fileContent = instance.readJsonFile();
            JSONObject fileJson = JSONObject.fromObject(fileContent);
            JSONObject json = fileJson.getJSONArray("configs").getJSONObject(0);
            String server = json.getString("server");
            if(StringUtils.isBlank(server))
                throw new RuntimeException("解析配置文件出错");
            String pwd = instance.getFreePassword(server);
            if(pwd == null)
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
        String cmd = "tasklist /FO CSV /FI \"IMAGENAME eq "+exe+"\"";
        Process process = Runtime.getRuntime().exec(cmd);
        BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream(), Charset.forName("GBK")));   
        String line = null;
        boolean exist = false;
        while((line=br.readLine())!=null)
        {   
            if(line.contains(exe))
            {   
                exist = true;
                break;
            }   
        }   
        if(!exist)
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
            fields[i].set(this, p.getProperty(fields[i].getName(),fields[i].get(this).toString()));
        }
    }

    public void killShadowsocks() throws IOException
    {
        String cmd = "taskkill /F /IM " + exe;
        Runtime.getRuntime().exec(cmd);
    }
    
    public String getFreePassword(String server) throws IOException, ClientProtocolException
    {
        String content = login();
//        String url = "http://my.ss-link.com/my/free";
//        HttpGet get = new HttpGet(url);
//        HttpResponse response = client.execute(get);
//        content = EntityUtils.toString(response.getEntity());
        
        Document document = Jsoup.parse(content);
        Elements trs = document.select("table").select("tr");
        for(int i = 0;i<trs.size();i++){
            Elements tds = trs.get(i).select("td");
            for(int j = 0;j<tds.size();j++){
                String text = tds.get(j).text();
                if(server.equals(text))
                    return tds.get(j+2).text();
            }
        }
        return null;
    }
    
    private String login() throws IOException, ClientProtocolException
    {
        HttpClient client = new DefaultHttpClient();
        StringBuffer loginUrl = new StringBuffer();
        loginUrl.append("http://my.ss-link.com/login").append("?email=").append(email).append("&redirect=/my/free&password=").append(password);
        HttpPost post = new HttpPost(loginUrl.toString());
        HttpResponse response = client.execute(post);
        return EntityUtils.toString(response.getEntity());
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

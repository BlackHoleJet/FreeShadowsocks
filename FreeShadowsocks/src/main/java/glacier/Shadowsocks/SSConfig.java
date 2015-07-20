package glacier.Shadowsocks;

public class SSConfig
{
    String email = "fonswanefon@gmai.com";
    String password = "38777b2ef1cda076e3a59f70c3d790c0";
    String exe = "Shadowsocks.exe";
    String config = "gui-config.json";
    String isEncoded = "true";
    String host = "http://www.ss-link.com";
    
    public void setEmail(String email)
    {
        this.email = email;
    }
    public String getEmail()
    {
        return email;
    }
    public void setPassword(String password)
    {
        this.password = password;
    }
    public String getPassword()
    {
        return password;
    }
    public void setExe(String exe)
    {
        this.exe = exe;
    }
    public String getExe()
    {
        return exe;
    }
    public void setConfig(String config)
    {
        this.config = config;
    }
    public String getConfig()
    {
        return config;
    }
    public void setIsEncoded(String isEncoded)
    {
        this.isEncoded = isEncoded;
    }
    public String getIsEncoded()
    {
        return isEncoded;
    }
    public void setHost(String host)
    {
        this.host = host;
    }
    public String getHost()
    {
        return host;
    }
}

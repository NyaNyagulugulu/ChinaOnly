package neko.chinaOnly;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class ChinaOnly extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        // Plugin startup logic
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("ChinaOnly plugin has been enabled!");
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        getLogger().info("ChinaOnly plugin has been disabled!");
    }

    @EventHandler
    public void onPlayerLogin(PlayerLoginEvent event) {
        String playerIP = event.getAddress().getHostAddress();
        getLogger().info("Player " + event.getPlayer().getName() + " is connecting from IP: " + playerIP);

        // 处理IPv6地址格式
        playerIP = normalizeIP(playerIP);

        if (!isFromChinaAndNotProxy(playerIP)) {
            event.disallow(PlayerLoginEvent.Result.KICK_OTHER, 
                "仅允许来自中国大陆的家庭用户连接。代理、VPN或非中国地区的IP地址已被拒绝。");
        }
    }

    /**
     * 标准化IP地址，处理IPv6映射的IPv4地址
     */
    private String normalizeIP(String ip) {
        if (ip == null || ip.isEmpty()) {
            return ip;
        }
        
        // 处理IPv4映射的IPv6地址，如 ::ffff:192.168.1.1
        if (ip.startsWith("::ffff:") && ip.length() > 7) {
            String potentialIPv4 = ip.substring(7);
            // 检查是否是有效的IPv4地址格式
            if (isValidIPv4(potentialIPv4)) {
                getLogger().info("Extracted IPv4 from IPv6 mapped address: " + ip + " -> " + potentialIPv4);
                return potentialIPv4;
            }
        }
        
        // 处理压缩的IPv6地址（如 ::1）
        if (ip.equals("::1")) {
            getLogger().info("Interpreting ::1 as localhost (127.0.0.1)");
            return "127.0.0.1";
        }
        
        return ip;
    }

    /**
     * 验证是否为有效的IPv4地址
     */
    private boolean isValidIPv4(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        
        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        
        try {
            for (String part : parts) {
                int num = Integer.parseInt(part);
                if (num < 0 || num > 255) {
                    return false;
                }
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * 检测IP是否属于中国且不是代理
     */
    private boolean isFromChinaAndNotProxy(String ip) {
        // 检查IP地理位置
        return checkIPGeolocation(ip);
    }

    /**
     * 通过地理位置API检测IP归属地
     * 使用ip-api.com作为权威的免费IP地理位置服务
     */
    private boolean checkIPGeolocation(String ip) {
        try {
            // 使用ip-api.com - 免费但权威的IP地理位置API
            String apiUrl = "http://ip-api.com/json/" + ip + "?lang=zh-CN";
            URL url = new URL(apiUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                getLogger().warning("IP地理位置API返回错误状态码: " + responseCode);
                return false;
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();

            JsonObject jsonResponse = JsonParser.parseString(response.toString()).getAsJsonObject();
            
            // 检查API响应状态
            String status = jsonResponse.get("status").getAsString();
            if (!"success".equals(status)) {
                getLogger().warning("IP地理位置API返回失败: " + jsonResponse.get("message").getAsString());
                return false;
            }

            String countryCode = jsonResponse.get("countryCode").getAsString();
            String country = jsonResponse.get("country").getAsString();
            String region = jsonResponse.get("regionName").getAsString();
            String city = jsonResponse.get("city").getAsString();
            String isp = jsonResponse.has("isp") ? jsonResponse.get("isp").getAsString() : "";
            String org = jsonResponse.has("org") ? jsonResponse.get("org").getAsString() : "";
            boolean proxy = jsonResponse.has("proxy") ? jsonResponse.get("proxy").getAsBoolean() : false;
            
            getLogger().info("IP归属地信息: " + ip + " -> " + country + " (" + countryCode + "), " + region + ", " + city + " | ISP: " + isp + " | ORG: " + org + " | Proxy: " + proxy);

            // 检查国家代码是否为中国(CN)、香港(HK)、澳门(MO)或台湾(TW)
            boolean isFromChinaRegion = "CN".equals(countryCode) || "HK".equals(countryCode) || 
                                       "MO".equals(countryCode) || "TW".equals(countryCode);
            
            // 如果IP被API标记为代理/VPN，则拒绝
            if (proxy) {
                getLogger().warning("IP被标记为代理/VPN服务: " + ip);
                return false;
            }
            
            // 额外检查ISP/ORG是否为代理服务
            if (isFromChinaRegion) {
                String lowerIsp = isp.toLowerCase();
                String lowerOrg = org.toLowerCase();
                
                // 检查ISP或ORG名称中是否包含代理/VPN相关的关键词
                if (containsProxyKeywords(lowerIsp) || containsProxyKeywords(lowerOrg)) {
                    getLogger().warning("IP来自中国但ISP/ORG可能为代理服务: " + isp + " | " + org);
                    return false;
                }
            }
            
            return isFromChinaRegion && !proxy;

        } catch (Exception e) {
            getLogger().warning("无法检测IP归属地: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 检查字符串是否包含代理相关关键词
     */
    private boolean containsProxyKeywords(String text) {
        if (text == null) return false;
        
        String[] proxyKeywords = {
            "proxy", "vpn", "shadowsocks", "v2ray", "trojan", "openvpn", "wireguard",
            "代理", "翻墙", "机场", "ss", "ssr", "v2ray", "trojan", "加速器", "线路"
        };
        
        String lowerText = text.toLowerCase();
        for (String keyword : proxyKeywords) {
            if (lowerText.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        
        return false;
    }
}


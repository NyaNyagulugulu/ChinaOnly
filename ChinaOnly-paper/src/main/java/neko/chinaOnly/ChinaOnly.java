package neko.chinaOnly;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ChinaOnly extends JavaPlugin implements Listener {
    private DatabaseManager databaseManager;
    private Set<String> deniedRegions;
    private boolean enableRegionRestriction;
    private String deniedMessage;

    @Override
    public void onEnable() {
        // 保存默认配置文件
        saveDefaultConfig();
        loadConfiguration();
        
        // Plugin startup logic
        getServer().getPluginManager().registerEvents(this, this);
        
        // 初始化数据库
        try {
            Class.forName("org.sqlite.JDBC");
            databaseManager = new DatabaseManager();
        } catch (ClassNotFoundException e) {
            getLogger().severe("无法加载SQLite JDBC驱动: " + e.getMessage());
        }
        
        getLogger().info("ChinaOnly插件已启用！");
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        if (databaseManager != null) {
            databaseManager.close();
        }
        getLogger().info("ChinaOnly插件已禁用！");
    }

    @Override
    public void reloadConfig() {
        super.reloadConfig();
        loadConfiguration();
    }

    private void loadConfiguration() {
        FileConfiguration config = getConfig();
        List<String> regions = config.getStringList("denied-regions");
        // 过滤掉空字符串
        this.deniedRegions = new HashSet<>();
        for (String region : regions) {
            if (region != null && !region.trim().isEmpty()) {
                this.deniedRegions.add(region.trim().toUpperCase());
            }
        }
        this.enableRegionRestriction = config.getBoolean("enable-region-restriction", true);
        this.deniedMessage = config.getString("denied-message", "仅允许来自中国大陆的家庭用户连接。代理、VPN或非中国地区的IP地址已被拒绝。");
    }

    @EventHandler
    public void onPlayerLogin(PlayerLoginEvent event) {
        String playerIP = event.getAddress().getHostAddress();
        getLogger().info("玩家 " + event.getPlayer().getName() + " 正从IP地址: " + playerIP + " 连接");

        // 处理IPv6地址格式
        playerIP = normalizeIP(playerIP);

        if (!isFromChinaAndNotProxy(playerIP)) {
            event.disallow(PlayerLoginEvent.Result.KICK_OTHER, deniedMessage);
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
                getLogger().info("从IPv6映射地址中提取IPv4: " + ip + " -> " + potentialIPv4);
                return potentialIPv4;
            }
        }
        
        // 处理压缩的IPv6地址（如 ::1）
        if (ip.equals("::1")) {
            getLogger().info("将 ::1 视为本地回环地址 (127.0.0.1)");
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
        // 首先从数据库中查找IP信息
        if (databaseManager != null) {
            IPInfo ipInfo = databaseManager.getIPInfo(ip);
            if (ipInfo != null) {
                getLogger().info("从数据库中获取IP信息: " + ip + " -> " + 
                    (ipInfo.isChinaRegion() && !ipInfo.isProxy() ? "允许" : "拒绝"));

                // 检查地区限制
                if (enableRegionRestriction && ipInfo.isChinaRegion() && !ipInfo.isProxy()) {
                    return !isRegionDenied(ipInfo.getCountryCode()); // 如果地区被拒绝，则返回false
                }

                return ipInfo.isChinaRegion() && !ipInfo.isProxy();
            }
        }

        // 数据库中没有找到，通过API检测IP归属地
        return checkIPGeolocation(ip);
    }

    /**
     * 检查国家代码是否在拒绝列表中
     */
    private boolean isRegionDenied(String countryCode) {
        if (countryCode == null) return false;
        return deniedRegions.contains(countryCode.toUpperCase());
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
                // 无法识别的IP地址，拒绝连接
                return false;
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();

            JsonParser parser = new JsonParser();
            JsonObject jsonResponse = parser.parse(response.toString()).getAsJsonObject();
            
            // 检查API响应状态
            String status = jsonResponse.get("status").getAsString();
            if (!"success".equals(status)) {
                getLogger().warning("IP地理位置API返回失败: " + jsonResponse.get("message").getAsString());
                // 无法识别的IP地址，拒绝连接
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

            // 检查地区限制
            if (enableRegionRestriction && isFromChinaRegion && !proxy) {
                if (isRegionDenied(countryCode)) {
                    getLogger().info("IP来自被拒绝的地区: " + countryCode + " - " + ip);
                    return false;
                }
            }
            
            // 将IP信息保存到数据库
            if (databaseManager != null) {
                databaseManager.saveIPInfo(ip, jsonResponse, isFromChinaRegion);
            }
            
            return isFromChinaRegion && !proxy;

        } catch (Exception e) {
            getLogger().warning("无法检测IP归属地: " + e.getMessage());
            // 出现异常时，拒绝连接以确保安全性
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


package neko.chinaOnlyBungee;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ConfigurationAdapter;
import net.md_5.bungee.api.connection.PendingConnection;
import net.md_5.bungee.api.event.LoginEvent;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import net.md_5.bungee.event.EventHandler;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public final class ChinaOnlyBungee extends Plugin implements Listener {
    private Set<String> deniedRegions;
    private boolean enableRegionRestriction;
    private String deniedMessage;

    @Override
    public void onEnable() {
        // 加载配置文件
        loadConfiguration();
        // Plugin startup logic
        getProxy().getPluginManager().registerListener(this, this);
        getLogger().info("ChinaOnly-Bungee插件已启用！");
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        getLogger().info("ChinaOnly-Bungee插件已禁用！");
    }

    private void loadConfiguration() {
        try {
            // 确保配置文件存在
            if (!getDataFolder().exists()) {
                getDataFolder().mkdir();
            }

            File configFile = new File(getDataFolder(), "config.yml");
            if (!configFile.exists()) {
                // 从resources复制默认配置文件
                try (InputStream in = getResourceAsStream("config.yml")) {
                    Files.copy(in, configFile.toPath());
                }
            }

            // 加载配置
            Configuration config = ConfigurationProvider.getProvider(YamlConfiguration.class).load(configFile);
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
        } catch (IOException e) {
            getLogger().severe("无法加载配置文件: " + e.getMessage());
            // 设置默认值
            this.deniedRegions = new HashSet<>();
            this.enableRegionRestriction = true;
            this.deniedMessage = "仅允许来自中国大陆的家庭用户连接。代理、VPN或非中国地区的IP地址已被拒绝。";
        }
    }

    @EventHandler
    public void onLogin(LoginEvent event) {
        PendingConnection connection = event.getConnection();
        String playerIP = connection.getAddress().getAddress().getHostAddress();
        getLogger().info("玩家 " + connection.getName() + " 正从IP地址: " + playerIP + " 连接");

        // 处理IPv6地址格式
        playerIP = normalizeIP(playerIP);

        if (!isFromChinaAndNotProxy(playerIP)) {
            event.setCancelled(true);
            event.setCancelReason(new TextComponent(ChatColor.RED + deniedMessage));
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

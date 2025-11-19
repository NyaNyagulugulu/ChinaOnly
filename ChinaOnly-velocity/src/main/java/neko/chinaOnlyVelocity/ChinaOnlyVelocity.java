package neko.chinaOnlyVelocity;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Plugin(
    id = "chinaonly-velocity",
    name = "ChinaOnly-velocity",
    version = "1.0-SNAPSHOT",
    url = "https://cnmsb.xin/",
    authors = {"不穿胖次の小奶猫"},
    description = "A Velocity plugin to restrict connections by region"
)
public class ChinaOnlyVelocity {

    @Inject
    private Logger logger;
    
    @Inject
    private ProxyServer proxyServer;
    
    @Inject
    private com.velocitypowered.api.plugin.PluginContainer pluginContainer;
    
    @Inject
    @DataDirectory
    private Path dataDirectory;
    
    private DatabaseManager databaseManager;
    private Set<String> deniedRegions;
    private boolean enableRegionRestriction;
    private String deniedMessage;

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        // 初始化配置
        this.dataDirectory = this.dataDirectory.resolve("ChinaOnly-velocity");
        if (!Files.exists(this.dataDirectory)) {
            try {
                Files.createDirectories(this.dataDirectory);
            } catch (IOException e) {
                logger.error("无法创建插件数据目录: {}", e.getMessage());
            }
        }
        loadConfiguration();
        
        // 初始化数据库
        try {
            Class.forName("org.sqlite.JDBC");
            databaseManager = new DatabaseManager();
        } catch (ClassNotFoundException e) {
            logger.error("无法加载SQLite JDBC驱动: {}", e.getMessage());
        }
        
        logger.info("ChinaOnly-velocity插件已启用！");
    }

    private void loadConfiguration() {
        try {
            // 确保数据目录存在
            if (!Files.exists(dataDirectory)) {
                Files.createDirectories(dataDirectory);
            }

            Path configFile = dataDirectory.resolve("config.yml");
            if (!Files.exists(configFile)) {
                // 从resources复制默认配置文件
                try (InputStream in = getClass().getClassLoader().getResourceAsStream("config.yml")) {
                    if (in != null) {
                        Files.copy(in, configFile);
                    } else {
                        // 如果资源文件不存在，创建默认配置
                        createDefaultConfig(configFile);
                    }
                } catch (IOException e) {
                    logger.error("无法复制默认配置文件: {}", e.getMessage());
                }
            }

            // 简单地读取YAML文件，逐行解析
            this.deniedRegions = new HashSet<>();
            this.enableRegionRestriction = false; // 默认为false，允许所有区域
            this.deniedMessage = "你所在的区域拒绝连接！或者是你正在使用VPN";

            List<String> lines = Files.readAllLines(configFile);
            for (String line : lines) {
                line = line.trim();
                // 解析被拒绝的地区列表
                if (line.contains("denied-regions:") || line.contains("-")) {
                    if (line.contains("-") && !line.contains("#")) {
                        // 提取地区代码，例如 - 'CN' 或 - CN
                        String regionLine = line.replace("-", "").trim();
                        if (regionLine.startsWith("'") || regionLine.startsWith("\"")) {
                            regionLine = regionLine.substring(1);
                        }
                        if (regionLine.endsWith("'") || regionLine.endsWith("\"")) {
                            regionLine = regionLine.substring(0, regionLine.length() - 1);
                        }
                        regionLine = regionLine.trim();
                        if (!regionLine.isEmpty() && !regionLine.equals("''") && !regionLine.equals("\"\"")) {
                            this.deniedRegions.add(regionLine.toUpperCase());
                        }
                    }
                } else if (line.startsWith("enable-region-restriction:")) {
                    String value = line.replace("enable-region-restriction:", "").trim();
                    this.enableRegionRestriction = Boolean.parseBoolean(value);
                } else if (line.startsWith("denied-message:")) {
                    String value = line.replace("denied-message:", "").trim();
                    if (value.startsWith("\"") || value.startsWith("'")) {
                        value = value.substring(1);
                    }
                    if (value.endsWith("\"") || value.endsWith("'")) {
                        value = value.substring(0, value.length() - 1);
                    }
                    this.deniedMessage = value;
                }
            }
        } catch (Exception e) {
            logger.error("无法加载配置文件: {}", e.getMessage());
            // 设置默认值
            this.deniedRegions = new HashSet<>();
            this.enableRegionRestriction = false; // 默认为false，允许所有区域
            this.deniedMessage = "你所在的区域拒绝连接！或者是你正在使用VPN";
        }
    }

    private void createDefaultConfig(Path configFile) throws IOException {
        String defaultConfig = "# ChinaOnly-velocity 配置文件\n" +
                "# 地区限制配置 - 设置不允许连接的地区\n" +
                "# 默认允许所有地区连接，如需限制特定地区，请将其添加到 denied-regions 列表中\n" +
                "# 例如，如果不想允许台湾地区连接，可以将 'TW' 添加到列表中\n" +
                "\n" +
                "# 不允许连接的地区列表\n" +
                "# 有效值: CN (中国大陆), HK (香港), MO (澳门), TW (台湾)\n" +
                "denied-regions:\n" +
                "  - ''  # 默认为空，表示允许所有地区\n" +
                "\n" +
                "# 是否启用地区限制功能 (true/false) - 默认为 false，即允许所有地区\n" +
                "enable-region-restriction: false\n" +
                "\n" +
                "# 当玩家被拒绝连接时显示的消息\n" +
                "denied-message: \"你所在的区域拒绝连接！或者是你正在使用VPN\"";

        try (FileWriter writer = new FileWriter(configFile.toFile())) {
            writer.write(defaultConfig);
        }
    }

    @Subscribe
    public void onPreLogin(PreLoginEvent event) {
        String playerIP = event.getConnection().getRemoteAddress().getAddress().getHostAddress();
        logger.info("玩家 {} 正从IP地址: {} 连接", event.getUsername(), playerIP);

        // 处理IPv6地址格式
        playerIP = normalizeIP(playerIP);

        // 同步检查IP归属地，阻塞网络线程直到检查完成
        if (!isFromChinaAndNotProxy(playerIP)) {
            event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                Component.text(deniedMessage)));
        }
        // 如果IP有效，则不设置结果，连接继续
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
                logger.info("从IPv6映射地址中提取IPv4: {} -> {}", ip, potentialIPv4);
                return potentialIPv4;
            }
        }

        // 处理压缩的IPv6地址（如 ::1）
        if (ip.equals("::1")) {
            logger.info("将 ::1 视为本地回环地址 (127.0.0.1)");
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
                logger.info("从数据库中获取IP信息: {} -> {}", ip, 
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
                logger.warn("IP地理位置API返回错误状态码: {}", responseCode);
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
                logger.warn("IP地理位置API返回失败: {}", jsonResponse.get("message").getAsString());
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

            logger.info("IP归属地信息: {} -> {} ({}) {} {} | ISP: {} | ORG: {} | Proxy: {}", 
                       ip, country, countryCode, region, city, isp, org, proxy);

            // 检查国家代码是否为中国(CN)、香港(HK)、澳门(MO)或台湾(TW)
            boolean isFromChinaRegion = "CN".equals(countryCode) || "HK".equals(countryCode) ||
                                       "MO".equals(countryCode) || "TW".equals(countryCode);

            // 如果IP被API标记为代理/VPN，则拒绝
            if (proxy) {
                logger.warn("IP被标记为代理/VPN服务: {}", ip);
                return false;
            }

            // 额外检查ISP/ORG是否为代理服务
            if (isFromChinaRegion) {
                String lowerIsp = isp.toLowerCase();
                String lowerOrg = org.toLowerCase();

                // 检查ISP或ORG名称中是否包含代理/VPN相关的关键词
                if (containsProxyKeywords(lowerIsp) || containsProxyKeywords(lowerOrg)) {
                    logger.warn("IP来自中国但ISP/ORG可能为代理服务: {} | {}", isp, org);
                    return false;
                }
            }

            // 检查地区限制
            if (enableRegionRestriction && isFromChinaRegion && !proxy) {
                if (isRegionDenied(countryCode)) {
                    logger.info("IP来自被拒绝的地区: {} - {}", countryCode, ip);
                    return false;
                }
            }

            // 将IP信息保存到数据库
            if (databaseManager != null) {
                databaseManager.saveIPInfo(ip, jsonResponse, isFromChinaRegion);
            }

            return isFromChinaRegion && !proxy;

        } catch (Exception e) {
            logger.warn("无法检测IP归属地: {}", e.getMessage());
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

package neko.chinaOnlyBungee;

import java.sql.Timestamp;

public class IPInfo {
    private String ip;
    private String countryCode;
    private String country;
    private String region;
    private String city;
    private String isp;
    private String org;
    private boolean proxy;
    private boolean isChinaRegion;
    private Timestamp lastUpdated;

    public IPInfo(String ip, String countryCode, String country, String region, 
                  String city, String isp, String org, boolean proxy, boolean isChinaRegion, 
                  Timestamp lastUpdated) {
        this.ip = ip;
        this.countryCode = countryCode;
        this.country = country;
        this.region = region;
        this.city = city;
        this.isp = isp;
        this.org = org;
        this.proxy = proxy;
        this.isChinaRegion = isChinaRegion;
        this.lastUpdated = lastUpdated;
    }

    // Getters
    public String getIp() { return ip; }
    public String getCountryCode() { return countryCode; }
    public String getCountry() { return country; }
    public String getRegion() { return region; }
    public String getCity() { return city; }
    public String getIsp() { return isp; }
    public String getOrg() { return org; }
    public boolean isProxy() { return proxy; }
    public boolean isChinaRegion() { return isChinaRegion; }
    public Timestamp getLastUpdated() { return lastUpdated; }
}
# ChinaOnly

一个Minecraft服务器插件，用于限制仅来自中国地区的玩家连接。

## 项目结构

本项目包含三个模块：

- `ChinaOnly-paper`: PaperMC服务端插件
- `ChinaOnly-velocity`: Velocity代理服务端插件
- `ChinaOnly-Bungee`: BungeeCord代理服务端插件

## 功能特点

- 检测玩家IP地址的地理位置
- 仅允许来自中国大陆、香港、澳门、台湾的玩家连接
- 自动检测并拒绝代理/VPN连接
- 检测ISP/ORG名称中的代理/VPN关键词
- 支持IPv4和IPv6地址
- 使用SQLite数据库缓存IP地理位置信息，减少API调用

## 依赖

- PaperMC 1.18+ (或兼容版本)
- Velocity 3.0.0+ (或兼容版本)
- BungeeCord 1.21+ (或兼容版本)
- Java 17+

## 配置

插件使用免费的IP地理位置API (ip-api.com) 来检测IP归属地，无需额外配置。

## 构建

```bash
# 在项目根目录执行
mvn clean package
```

生成的JAR文件将位于各模块的 `target/` 目录下。

## 安装

### Paper服务器
将 `ChinaOnly-paper/target/ChinaOnly-paper-1.0-SNAPSHOT.jar` 放入服务器的 `plugins/` 目录

### Velocity代理
将 `ChinaOnly-velocity/target/ChinaOnly-velocity-1.0-SNAPSHOT.jar` 放入代理的 `plugins/` 目录

### BungeeCord代理
将 `ChinaOnly-Bungee/target/chinaonly-bungee-1.0-SNAPSHOT.jar` 放入代理的 `plugins/` 目录

## 数据库功能

插件使用SQLite数据库来缓存IP地理位置信息，以提高性能并减少API调用次数：

- 数据库文件位置：`plugins/ChinaOnly/chinaonly.db`
- 当玩家连接时，插件首先查询数据库中的IP信息
- 如果数据库中没有该IP的记录，则调用API获取信息并保存到数据库
- 后续相同IP的连接将直接使用数据库中的信息

## CI/CD

本项目使用GitHub Actions进行持续集成和持续部署：

- 每次推送到`main`分支时会自动构建项目
- 当创建新的Git标签时，会自动发布新版本到GitHub Releases
- 构建产物会作为artifacts保存，可随时下载

## 已完成的工作

1. 完善了根目录的pom.xml，设置为父项目并包含所有子模块
2. 完善了ChinaOnly-paper模块的功能，包括IP地理位置检测和代理识别
3. 完善了ChinaOnly-velocity模块的功能，包括同步IP检测
4. 完善了ChinaOnly-Bungee模块的功能，与其它模块保持一致
5. 为所有模块统一了依赖版本
6. 实现了SQLite数据库功能，缓存IP地理位置信息
7. 创建了详细的README文档
8. 配置了GitHub Actions自动化构建和发布流程

所有三个模块现在都能成功构建。
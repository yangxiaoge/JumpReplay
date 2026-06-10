# JumpReplay (系统签名重构版)

**本项目是基于原开源项目 [FourTwooo/JumpReplay](https://github.com/FourTwooo/JumpReplay) 的二次修改版本（Fork），严格遵循 GNU GPL v3 开源协议。**

### 🛠️ 二次修改与重构说明（符合 GPLv3 声明）
* **修改日期**：2026年6月10日
* **核心重构内容**：
  1. **彻底剥离 Xposed 框架**：删除了所有 Xposed Bridge 依赖、模块声明与 Hook 入口。
  2. **系统签名与特权运行**：配置 `platform.keystore` 进行系统级签名，并声明 `android:sharedUserId="android.uid.system"` 获得系统级 UID。
  3. **全局 IActivityController 拦截**：不再依赖 Xposed 在各个应用进程中注入，改用系统特权通过反射向 `ActivityManagerService` 注册内建的 `IActivityController` 拦截桩，实现对全系统所有第三方应用跳转意图的**全局、免 Xposed、免 Root 拦截与记录**。
  4. **同进程本地通信优化**：重构了列表渲染的接收机制（`DataProcessor` 兼容单条本地数据传输），不再依赖 Xposed 版本的 IPC 批处理 JSON 反序列化过程，彻底消除 NullPointerException 崩溃隐患。

---

<img src="https://github.com/user-attachments/assets/1f3e256c-ff1d-402b-9d1d-52a353d68bb3" width="200"/><img src="https://github.com/user-attachments/assets/cc1c2e44-f5b4-4826-a957-72e727990bc4" width="200"/><img src="https://github.com/user-attachments/assets/b73e8411-abb1-4a06-9dcd-938b148f502e" width="200"/><img src="https://github.com/user-attachments/assets/7d1a4ede-337d-4c44-add8-0d739964bc33" width="200"/>


---

[//]: # (## 使用环境)

[//]: # (**Android < 11**)

[//]: # (- 超过版本应用 **未设置targetSdk>=30** 或 **持有QUERY_ALL_PACKAGES权限** 可以使用)
## 演示视频

**抓包(作用域勾选对应应用)**

https://github.com/user-attachments/assets/b7c47a00-f47d-43e9-848e-a00eda47c6db

**拦截(作用域勾选系统框架)**

https://github.com/user-attachments/assets/634184d4-1bf4-4a4f-9502-01e064bdee9b

---

### 有bug和改进意见请直接提交Issues或QQ群(1021904342)联系我
### 如二改请完全遵守GPL3.0.如果你喜欢项目请点个Star支持一下.


---

# 法律声明与使用协议

## 项目性质声明
1. 本工具为技术研究性质的开源项目，仅用于：
   - 安卓系统合法授权范围内的通信机制分析
   - 应用开发调试过程中的合法数据监测
   - 网络安全领域的授权渗透测试
   - 计算机技术教学研究场景

## 禁止性条款
2. 使用者承诺不会用于以下场景：
   - 未经授权的数据抓取（《网络安全法》第27条）
   - 实施网络入侵、破坏系统安全（《刑法》第285、286条）
   - 制作/传播恶意程序（《刑法》第285条第三款）
   - 任何形式的黑灰产、电信诈骗等违法活动
   - 侵犯他人隐私或商业秘密（《民法典》第1032条）

## 责任豁免
3. 开发者声明：
   - 本工具不包含任何诱导/教唆违法使用的设计
   - 不对使用者行为承担连带责任（《民法典》第1195条）
   - 已尽合理注意义务提供技术警示
   - 保留对滥用者的法律追诉权利

## 使用约束
4. 使用者须知：
   - 必须遵守《数据安全法》《个人信息保护法》等法律法规
   - 须获得被监测主体的明确书面授权
   - 禁止将工具用于生产环境监控
   - 需自行承担所有法律后果

## 附加保护措施
5. 开发者已采取的风险防控措施：
   - 项目采用[GPL3]开源协议，禁止商业滥用
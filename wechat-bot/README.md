# 微信记账助手

这个服务把现有网页工具接到微信消息里。运行后，你可以把账单文字、微信/支付宝 CSV 或 TXT 文件发给登录的微信号，机器人会自动识别支出并写入本地账本。

## 准备

需要 Node.js 22 或更高版本。

```powershell
cd wechat-bot
npm install
```

## 第一次登录

```powershell
npm run login
```

按终端提示扫码登录。登录状态会保存在本机。

## 启动

```powershell
npm run start
```

启动后会同时开启本地网页：

```text
http://localhost:8787
```

用这个地址打开网页时，网页会读取微信助手记录的同一份数据。

## 可发送的内容

普通记账：

```text
记账 瑞幸咖啡 18
餐饮 午饭 23.5
```

账单文件：

```text
发送微信或支付宝导出的 CSV / TXT 文件
```

查询：

```text
本月
```

## 数据位置

默认数据保存在：

```text
wechat-bot/data/records.json
```

可以通过环境变量改路径：

```powershell
$env:MONEY_APP_DATA_FILE="C:\money\records.json"
npm run start
```

## 限制

- 不能直接读取微信或支付宝 App 内部账单。
- 图片截图识别需要 OCR 或视觉模型，当前第一版先支持文字和 CSV/TXT 文件。
- 运行机器人需要你自己的微信号扫码登录。

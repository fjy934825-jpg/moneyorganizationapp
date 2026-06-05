import { readFile } from "node:fs/promises";
import { login, start } from "weixin-agent-sdk";
import { parseBill, parseMessage } from "./parser.js";
import { addRecords, readRecords } from "./store.js";
import { startServer } from "./server.js";

startServer();

const agent = {
  async chat(req) {
    if (req.text?.trim() === "本月") {
      return { text: await monthlySummary() };
    }

    const records = await recordsFromRequest(req);
    if (!records.length) {
      return {
        text: "没有识别到支出。可以发送：\n记账 瑞幸咖啡 18\n也可以发送微信/支付宝 CSV 或 TXT 账单文件。",
      };
    }

    const result = await addRecords(records);
    const total = records.reduce((sum, record) => sum + record.amount, 0);
    const preview = records.slice(0, 3).map((record) => `${record.category} ${record.amount.toFixed(2)} ${record.merchant}`).join("\n");

    return {
      text: `已记录 ${records.length} 笔，新增 ${Math.max(result.added, 0)} 笔，合计 ${total.toFixed(2)} 元。\n${preview}`,
    };
  },
};

if (process.env.MONEY_APP_SKIP_LOGIN !== "1") {
  await login();
}

await start(agent);

async function recordsFromRequest(req) {
  if (req.media?.type === "file" && req.media.filePath) {
    const text = await readFile(req.media.filePath, "utf8");
    return parseBill(text, "微信文件");
  }

  if (req.text) return parseMessage(req.text, "微信消息");
  return [];
}

async function monthlySummary() {
  const records = await readRecords();
  const month = new Date().toISOString().slice(0, 7);
  const current = records.filter((record) => record.date.startsWith(month));
  if (!current.length) return `${month} 还没有支出记录。`;

  const total = current.reduce((sum, record) => sum + record.amount, 0);
  const categories = current.reduce((acc, record) => {
    acc[record.category] = (acc[record.category] || 0) + record.amount;
    return acc;
  }, {});
  const lines = Object.entries(categories)
    .sort((a, b) => b[1] - a[1])
    .map(([category, amount]) => `${category}: ${amount.toFixed(2)} 元`);

  return `${month} 支出 ${total.toFixed(2)} 元\n${lines.join("\n")}`;
}

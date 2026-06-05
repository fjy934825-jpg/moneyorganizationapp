export const categories = [
  "餐饮",
  "购物",
  "学习",
  "交通",
  "居住",
  "娱乐",
  "医疗",
  "人情",
  "通讯",
  "其他",
];

const categoryRules = [
  ["餐饮", ["餐", "饭", "咖啡", "奶茶", "茶百道", "蜜雪", "星巴克", "瑞幸", "美团", "饿了么", "肯德基", "麦当劳", "食堂", "便利店"]],
  ["购物", ["淘宝", "天猫", "京东", "拼多多", "抖音商城", "得物", "唯品会", "商场", "优衣库", "名创", "购物", "小红书"]],
  ["学习", ["书", "课程", "学费", "考试", "教育", "文具", "打印", "知网", "得到", "网易云课堂", "教材"]],
  ["交通", ["地铁", "公交", "滴滴", "高德", "铁路", "12306", "火车", "机票", "打车", "停车", "加油", "高速"]],
  ["居住", ["房租", "物业", "水费", "电费", "燃气", "宽带", "家政", "维修", "租金"]],
  ["娱乐", ["电影", "影院", "游戏", "音乐", "会员", "腾讯视频", "爱奇艺", "优酷", "网易云音乐", "演出", "ktv"]],
  ["医疗", ["医院", "药", "诊所", "体检", "医保", "美团买药", "京东健康"]],
  ["人情", ["红包", "转账", "礼物", "份子", "亲属卡"]],
  ["通讯", ["话费", "移动", "联通", "电信", "流量"]],
];

export function parseMessage(text, source = "微信助手") {
  const trimmed = String(text || "").trim();
  if (!trimmed) return [];

  const billRecords = parseBill(trimmed, source);
  if (billRecords.length) return billRecords;

  const single = parseSingleExpense(trimmed, source);
  return single ? [single] : [];
}

export function parseBill(text, source = "微信助手") {
  const rows = parseDelimitedRows(text);
  const parsed = [];

  for (const row of rows) {
    const record = normalizeRow(row, source);
    if (record) parsed.push(record);
  }

  return parsed;
}

function parseDelimitedRows(text) {
  const lines = String(text)
    .replace(/^\uFEFF/, "")
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean);

  const headerIndex = lines.findIndex((line) => {
    const normalized = line.replace(/\s/g, "");
    return normalized.includes("交易时间") || normalized.includes("交易创建时间") || normalized.includes("时间");
  });

  if (headerIndex === -1) return [];

  const delimiter = guessDelimiter(lines[headerIndex]);
  const headers = splitLine(lines[headerIndex], delimiter).map(cleanCell);

  return lines.slice(headerIndex + 1).flatMap((line) => {
    if (/^-{4,}$/.test(line) || line.includes("共") && line.includes("笔记录")) return [];
    const cells = splitLine(line, delimiter).map(cleanCell);
    if (cells.length < 3) return [];
    const row = {};
    headers.forEach((header, index) => {
      row[header] = cells[index] || "";
    });
    return [row];
  });
}

function normalizeRow(row, source) {
  const date = pick(row, ["交易时间", "交易创建时间", "付款时间", "时间", "账单时间"]);
  const merchant = pick(row, ["交易对方", "商户名称", "商品名称", "商品", "商品说明", "交易说明", "收/付款方", "对方"]);
  const note = pick(row, ["商品名称", "商品", "商品说明", "交易说明", "备注", "类型", "分类"]);
  const type = pick(row, ["收/支", "收支", "资金流向", "交易类型", "类型"]);
  const status = pick(row, ["交易状态", "当前状态", "状态", "资金状态"]);
  const amountText = pick(row, ["金额(元)", "金额（元）", "金额", "支出金额", "交易金额", "收入/支出金额(元)", "收入/支出金额（元）"]);

  if (!date || !amountText) return null;
  if (!isExpense(type, amountText, status, note)) return null;

  const amount = Math.abs(parseAmount(amountText));
  if (!Number.isFinite(amount) || amount <= 0) return null;

  const normalizedDate = normalizeDate(date);
  if (!normalizedDate) return null;

  const description = [merchant, note].filter(Boolean).join(" ");
  return createRecord({
    date: normalizedDate,
    merchant: merchant || note || "未命名支出",
    note,
    amount,
    category: classify(description),
    source,
  });
}

function parseSingleExpense(text, source) {
  const amountMatch = text.match(/(?:¥|￥)?\s*(-?\d+(?:\.\d{1,2})?)\s*元?/);
  if (!amountMatch) return null;

  const amount = Math.abs(Number(amountMatch[1]));
  if (!Number.isFinite(amount) || amount <= 0) return null;

  const category = categories.find((item) => text.includes(item)) || classify(text);
  const cleaned = text
    .replace(amountMatch[0], "")
    .replace(/^(记账|支出|消费|付款|花了|买了)[:：\s]*/, "")
    .trim();

  return createRecord({
    date: formatDate(new Date()),
    merchant: cleaned || "微信手动记账",
    note: text,
    amount,
    category,
    source,
  });
}

export function createRecord({ date, merchant, note, amount, category, source }) {
  return {
    id: makeId(`${date}|${amount}|${merchant}|${source}`),
    date,
    merchant,
    note,
    amount,
    category,
    source,
    createdAt: new Date().toISOString(),
  };
}

function pick(row, names) {
  const key = Object.keys(row).find((item) => names.some((name) => item.replace(/\s/g, "").includes(name.replace(/\s/g, ""))));
  return key ? row[key] : "";
}

function isExpense(type, amountText, status, note) {
  const joined = `${type} ${amountText} ${status} ${note}`;
  if (/退款|已退款|收入|转入|已退|还款成功/.test(joined)) return false;
  if (/支出|付款|消费|交易成功|支付成功/.test(joined)) return true;
  return /^[-－]/.test(amountText.trim());
}

function classify(text) {
  const normalized = String(text).toLowerCase();
  for (const [category, keywords] of categoryRules) {
    if (keywords.some((keyword) => normalized.includes(keyword.toLowerCase()))) return category;
  }
  return "其他";
}

function guessDelimiter(line) {
  const tabCount = (line.match(/\t/g) || []).length;
  const commaCount = (line.match(/,/g) || []).length;
  return tabCount > commaCount ? "\t" : ",";
}

function splitLine(line, delimiter) {
  const cells = [];
  let value = "";
  let quoted = false;

  for (let index = 0; index < line.length; index += 1) {
    const char = line[index];
    const next = line[index + 1];
    if (char === "\"" && next === "\"") {
      value += "\"";
      index += 1;
    } else if (char === "\"") {
      quoted = !quoted;
    } else if (char === delimiter && !quoted) {
      cells.push(value);
      value = "";
    } else {
      value += char;
    }
  }
  cells.push(value);
  return cells;
}

function cleanCell(value) {
  return String(value || "").replace(/^"+|"+$/g, "").trim();
}

function parseAmount(value) {
  return Number(String(value).replace(/[^\d.-]/g, ""));
}

function normalizeDate(value) {
  const match = String(value).match(/(\d{4})[/-](\d{1,2})[/-](\d{1,2})(?:\s+(\d{1,2}):(\d{1,2})(?::(\d{1,2}))?)?/);
  if (!match) return "";
  const [, year, month, day, hour = "00", minute = "00", second = "00"] = match;
  return `${year}-${pad(month)}-${pad(day)} ${pad(hour)}:${pad(minute)}:${pad(second)}`;
}

function formatDate(date) {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}

function pad(value) {
  return String(value).padStart(2, "0");
}

function makeId(input) {
  let hash = 0;
  for (let index = 0; index < input.length; index += 1) {
    hash = (hash << 5) - hash + input.charCodeAt(index);
    hash |= 0;
  }
  return `r_${Math.abs(hash)}`;
}

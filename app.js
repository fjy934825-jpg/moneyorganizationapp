const STORAGE_KEY = "monthly-expense-tracker-records-v1";

const categories = [
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

const categoryColors = {
  "餐饮": "#1f8a64",
  "购物": "#3867c8",
  "学习": "#8a5bce",
  "交通": "#0b7485",
  "居住": "#d59f27",
  "娱乐": "#e06a3a",
  "医疗": "#cf4b45",
  "人情": "#b35c9e",
  "通讯": "#60758f",
  "其他": "#6d7672",
};

const categoryRules = [
  ["餐饮", ["餐", "饭", "咖啡", "奶茶", "茶百道", "蜜雪", "星巴克", "瑞幸", "美团", "饿了么", "肯德基", "麦当劳", "食堂", "超市便利", "便利店"]],
  ["购物", ["淘宝", "天猫", "京东", "拼多多", "抖音商城", "得物", "唯品会", "商场", "优衣库", "名创", "购物", "小红书"]],
  ["学习", ["书", "课程", "学费", "考试", "教育", "文具", "打印", "知网", "得到", "网易云课堂", "b站课堂", "教材"]],
  ["交通", ["地铁", "公交", "滴滴", "高德", "铁路", "12306", "火车", "机票", "打车", "停车", "加油", "高速"]],
  ["居住", ["房租", "物业", "水费", "电费", "燃气", "宽带", "家政", "维修", "租金"]],
  ["娱乐", ["电影", "影院", "游戏", "音乐", "会员", "腾讯视频", "爱奇艺", "优酷", "网易云音乐", "演出", "ktv"]],
  ["医疗", ["医院", "药", "诊所", "体检", "医保", "美团买药", "京东健康"]],
  ["人情", ["红包", "转账", "礼物", "份子", "亲属卡"]],
  ["通讯", ["话费", "移动", "联通", "电信", "流量"]],
];

const els = {
  file: document.querySelector("#billFile"),
  pasteToggleBtn: document.querySelector("#pasteToggleBtn"),
  pastePanel: document.querySelector("#pastePanel"),
  pasteInput: document.querySelector("#pasteInput"),
  pasteImportBtn: document.querySelector("#pasteImportBtn"),
  pasteStatus: document.querySelector("#pasteStatus"),
  installBtn: document.querySelector("#installBtn"),
  sampleBtn: document.querySelector("#sampleBtn"),
  clearBtn: document.querySelector("#clearBtn"),
  exportBtn: document.querySelector("#exportBtn"),
  monthFilter: document.querySelector("#monthFilter"),
  categoryFilter: document.querySelector("#categoryFilter"),
  searchInput: document.querySelector("#searchInput"),
  monthTotal: document.querySelector("#monthTotal"),
  monthCount: document.querySelector("#monthCount"),
  topCategory: document.querySelector("#topCategory"),
  topCategoryAmount: document.querySelector("#topCategoryAmount"),
  todayTotal: document.querySelector("#todayTotal"),
  todayCount: document.querySelector("#todayCount"),
  sourceMix: document.querySelector("#sourceMix"),
  activeMonthLabel: document.querySelector("#activeMonthLabel"),
  categoryChart: document.querySelector("#categoryChart"),
  recordsBody: document.querySelector("#recordsBody"),
  recordStatus: document.querySelector("#recordStatus"),
  pwaStatus: document.querySelector("#pwaStatus"),
  emptyTemplate: document.querySelector("#emptyTemplate"),
};

let records = loadRecords();
let deferredInstallPrompt = null;
let remoteSyncAvailable = false;
let isRemoteSyncing = false;

init();

async function init() {
  populateCategoryFilter();
  els.monthFilter.value = latestMonth(records) || currentMonth();
  setupPwa();
  els.file.addEventListener("change", handleFiles);
  els.pasteToggleBtn.addEventListener("click", togglePastePanel);
  els.pasteImportBtn.addEventListener("click", importPastedBill);
  els.installBtn.addEventListener("click", installApp);
  els.sampleBtn.addEventListener("click", loadSample);
  els.clearBtn.addEventListener("click", clearRecords);
  els.exportBtn.addEventListener("click", exportJson);
  els.monthFilter.addEventListener("change", render);
  els.categoryFilter.addEventListener("change", render);
  els.searchInput.addEventListener("input", render);
  await loadRemoteRecords();
  render();
}

function togglePastePanel() {
  els.pastePanel.hidden = !els.pastePanel.hidden;
  if (!els.pastePanel.hidden) els.pasteInput.focus();
}

function importPastedBill() {
  const text = els.pasteInput.value.trim();
  if (!text) {
    els.pasteStatus.textContent = "请先粘贴账单内容。";
    return;
  }

  const imported = parseBill(text, "粘贴账单.txt");
  if (!imported.length) {
    els.pasteStatus.textContent = "没有识别到支出记录。请确认粘贴内容包含表头和金额列。";
    return;
  }

  const before = records.length;
  mergeRecords(imported);
  const added = records.length - before;
  els.pasteStatus.textContent = `已识别 ${imported.length} 笔支出，新增 ${Math.max(added, 0)} 笔。`;
  els.pasteInput.value = "";
  if (imported[0]?.date) els.monthFilter.value = imported[0].date.slice(0, 7);
  render();
}

function setupPwa() {
  if ("serviceWorker" in navigator) {
    navigator.serviceWorker.register("./sw.js").then(() => {
      els.pwaStatus.textContent = "已支持离线打开，可添加到手机主屏幕。";
    }).catch(() => {
      els.pwaStatus.textContent = "当前浏览器未启用离线缓存。";
    });
  }

  window.addEventListener("beforeinstallprompt", (event) => {
    event.preventDefault();
    deferredInstallPrompt = event;
    els.installBtn.hidden = false;
  });

  window.addEventListener("appinstalled", () => {
    deferredInstallPrompt = null;
    els.installBtn.hidden = true;
    els.pwaStatus.textContent = "已安装到主屏幕。";
  });

  if (window.matchMedia("(display-mode: standalone)").matches || navigator.standalone) {
    els.installBtn.hidden = true;
    els.pwaStatus.textContent = "正在以 App 模式运行。";
  }
}

async function installApp() {
  if (!deferredInstallPrompt) {
    els.pwaStatus.textContent = "请用浏览器菜单里的“添加到主屏幕”安装。";
    return;
  }

  deferredInstallPrompt.prompt();
  await deferredInstallPrompt.userChoice;
  deferredInstallPrompt = null;
  els.installBtn.hidden = true;
}

async function handleFiles(event) {
  const files = [...event.target.files];
  const imported = [];
  for (const file of files) {
    const text = await readFile(file);
    imported.push(...parseBill(text, file.name));
  }

  mergeRecords(imported);
  els.file.value = "";
  render();
}

function readFile(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(decodeBillFile(reader.result));
    reader.onerror = reject;
    reader.readAsArrayBuffer(file);
  });
}

function decodeBillFile(buffer) {
  const bytes = new Uint8Array(buffer || []);
  const utf8Text = new TextDecoder("utf-8").decode(bytes);
  const brokenChars = (utf8Text.match(/\uFFFD/g) || []).length;
  if (brokenChars < 3) return utf8Text;

  try {
    return new TextDecoder("gb18030").decode(bytes);
  } catch {
    return utf8Text;
  }
}

function parseBill(text, filename) {
  const rows = parseDelimitedRows(text);
  const source = detectSource(text, filename);
  const parsed = [];

  for (const row of rows) {
    const record = normalizeRow(row, source);
    if (record) parsed.push(record);
  }

  return parsed;
}

function parseDelimitedRows(text) {
  const lines = text
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
  const rows = [];

  for (const line of lines.slice(headerIndex + 1)) {
    if (/^-{4,}$/.test(line) || line.includes("共") && line.includes("笔记录")) continue;
    const cells = splitLine(line, delimiter).map(cleanCell);
    if (cells.length < 3) continue;
    const row = {};
    headers.forEach((header, index) => {
      row[header] = cells[index] || "";
    });
    rows.push(row);
  }

  return rows;
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

  const description = [merchant, note].filter(Boolean).join(" ");
  const normalizedDate = normalizeDate(date);
  if (!normalizedDate) return null;

  return {
    id: makeId(`${normalizedDate}|${amount}|${description}|${source}`),
    date: normalizedDate,
    merchant: merchant || note || "未命名支出",
    note,
    amount,
    category: classify(description),
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

function parseAmount(value) {
  return Number(String(value).replace(/[^\d.-]/g, ""));
}

function normalizeDate(value) {
  const match = String(value).match(/(\d{4})[/-](\d{1,2})[/-](\d{1,2})(?:\s+(\d{1,2}):(\d{1,2})(?::(\d{1,2}))?)?/);
  if (!match) return "";
  const [, year, month, day, hour = "00", minute = "00", second = "00"] = match;
  return `${year}-${pad(month)}-${pad(day)} ${pad(hour)}:${pad(minute)}:${pad(second)}`;
}

function detectSource(text, filename) {
  const target = `${filename} ${text.slice(0, 1000)}`.toLowerCase();
  if (target.includes("wechat") || target.includes("微信")) return "微信";
  if (target.includes("alipay") || target.includes("支付宝")) return "支付宝";
  return "未知";
}

function classify(text) {
  const normalized = String(text).toLowerCase();
  for (const [category, keywords] of categoryRules) {
    if (keywords.some((keyword) => normalized.includes(keyword.toLowerCase()))) return category;
  }
  return "其他";
}

function mergeRecords(imported) {
  const map = new Map(records.map((record) => [record.id, record]));
  imported.forEach((record) => map.set(record.id, record));
  records = [...map.values()].sort((a, b) => b.date.localeCompare(a.date));
  saveRecords();
  if (imported.length && !els.monthFilter.value) els.monthFilter.value = latestMonth(records);
}

function render() {
  const month = els.monthFilter.value || currentMonth();
  const visible = getVisibleRecords();
  const monthRecords = records.filter((record) => record.date.startsWith(month));
  const filteredMonthRecords = visible.filter((record) => record.date.startsWith(month));
  const categoryTotals = sumBy(monthRecords, "category");
  const top = Object.entries(categoryTotals).sort((a, b) => b[1] - a[1])[0];
  const today = new Date().toISOString().slice(0, 10);
  const todayRecords = records.filter((record) => record.date.startsWith(today));

  els.monthTotal.textContent = formatMoney(sum(monthRecords));
  els.monthCount.textContent = `${monthRecords.length} 笔支出`;
  els.topCategory.textContent = top ? top[0] : "暂无";
  els.topCategoryAmount.textContent = top ? formatMoney(top[1]) : "¥0.00";
  els.todayTotal.textContent = formatMoney(sum(todayRecords));
  els.todayCount.textContent = `${todayRecords.length} 笔`;
  els.sourceMix.textContent = sourceMix(monthRecords);
  els.activeMonthLabel.textContent = `${month} 分类支出`;
  els.recordStatus.textContent = `${filteredMonthRecords.length} 笔匹配记录`;

  renderChart(categoryTotals);
  renderRecords(filteredMonthRecords);
  saveRecords();
}

function getVisibleRecords() {
  const month = els.monthFilter.value;
  const category = els.categoryFilter.value;
  const query = els.searchInput.value.trim().toLowerCase();

  return records.filter((record) => {
    const matchesMonth = !month || record.date.startsWith(month);
    const matchesCategory = category === "all" || record.category === category;
    const haystack = `${record.merchant} ${record.note} ${record.source}`.toLowerCase();
    const matchesQuery = !query || haystack.includes(query);
    return matchesMonth && matchesCategory && matchesQuery;
  });
}

function renderChart(totals) {
  const entries = categories
    .map((category) => [category, totals[category] || 0])
    .filter(([, amount]) => amount > 0)
    .sort((a, b) => b[1] - a[1]);
  const max = Math.max(...entries.map(([, amount]) => amount), 1);

  els.categoryChart.innerHTML = "";
  if (!entries.length) {
    els.categoryChart.innerHTML = "<div class=\"empty\"><strong>暂无分类数据</strong><span>导入账单后会生成分类条形图。</span></div>";
    return;
  }

  entries.forEach(([category, amount]) => {
    const row = document.createElement("div");
    row.className = "category-row";
    row.innerHTML = `
      <strong>${category}</strong>
      <div class="bar"><span style="--width:${Math.max((amount / max) * 100, 4)}%; --color:${categoryColors[category]}"></span></div>
      <em>${formatMoney(amount)}</em>
    `;
    els.categoryChart.append(row);
  });
}

function renderRecords(list) {
  els.recordsBody.innerHTML = "";
  if (!list.length) {
    els.recordsBody.append(els.emptyTemplate.content.cloneNode(true));
    return;
  }

  list.forEach((record) => {
    const tr = document.createElement("tr");
    tr.innerHTML = `
      <td>${record.date}</td>
      <td>
        <div class="merchant">
          <strong>${escapeHtml(record.merchant)}</strong>
          <small>${escapeHtml(record.note || "")}</small>
        </div>
      </td>
      <td><span class="source-pill ${record.source === "微信" ? "wechat" : record.source === "支付宝" ? "alipay" : ""}">${record.source}</span></td>
      <td>${categorySelect(record)}</td>
      <td class="money">${formatMoney(record.amount)}</td>
      <td><button class="delete-btn" type="button" data-delete="${record.id}" title="删除">×</button></td>
    `;
    els.recordsBody.append(tr);
  });

  els.recordsBody.querySelectorAll("select[data-id]").forEach((select) => {
    select.addEventListener("change", (event) => {
      const record = records.find((item) => item.id === event.target.dataset.id);
      if (record) record.category = event.target.value;
      render();
    });
  });

  els.recordsBody.querySelectorAll("button[data-delete]").forEach((button) => {
    button.addEventListener("click", () => {
      records = records.filter((record) => record.id !== button.dataset.delete);
      render();
    });
  });
}

function categorySelect(record) {
  const options = categories
    .map((category) => `<option value="${category}" ${category === record.category ? "selected" : ""}>${category}</option>`)
    .join("");
  return `<select class="category-select" data-id="${record.id}">${options}</select>`;
}

function populateCategoryFilter() {
  categories.forEach((category) => {
    const option = document.createElement("option");
    option.value = category;
    option.textContent = category;
    els.categoryFilter.append(option);
  });
}

function clearRecords() {
  if (!records.length || confirm("确定清空所有本地支出记录吗？")) {
    records = [];
    saveRecords();
    render();
  }
}

function exportJson() {
  const blob = new Blob([JSON.stringify(records, null, 2)], { type: "application/json" });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = `expense-records-${currentMonth()}.json`;
  anchor.click();
  URL.revokeObjectURL(url);
}

function loadSample() {
  const sample = [
    ["2026-06-05 08:42:13", "瑞幸咖啡", "拿铁", "-18.00", "微信"],
    ["2026-06-05 12:16:34", "学校食堂", "午餐", "-21.50", "支付宝"],
    ["2026-06-04 20:04:00", "京东商城", "键盘", "-189.00", "支付宝"],
    ["2026-06-03 09:30:00", "12306", "高铁票", "-86.00", "微信"],
    ["2026-06-01 21:10:20", "网易云课堂", "数据分析课", "-99.00", "支付宝"],
  ].map(([date, merchant, note, amount, source]) => ({
    id: makeId(`${date}|${amount}|${merchant}|${source}`),
    date,
    merchant,
    note,
    amount: Math.abs(parseAmount(amount)),
    category: classify(`${merchant} ${note}`),
    source,
    createdAt: new Date().toISOString(),
  }));

  mergeRecords(sample);
  els.monthFilter.value = "2026-06";
  render();
}

function saveRecords() {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(records));
  saveRemoteRecords();
}

function loadRecords() {
  try {
    return JSON.parse(localStorage.getItem(STORAGE_KEY) || "[]");
  } catch {
    return [];
  }
}

async function loadRemoteRecords() {
  try {
    const response = await fetchWithTimeout("/api/records", { timeout: 800 });
    if (!response.ok) return;
    const remoteRecords = await response.json();
    if (!Array.isArray(remoteRecords)) return;

    remoteSyncAvailable = true;
    const map = new Map(records.map((record) => [record.id, record]));
    remoteRecords.forEach((record) => map.set(record.id, record));
    records = [...map.values()].sort((a, b) => b.date.localeCompare(a.date));
    localStorage.setItem(STORAGE_KEY, JSON.stringify(records));
  } catch {
    remoteSyncAvailable = false;
  }
}

async function saveRemoteRecords() {
  if (!remoteSyncAvailable || isRemoteSyncing) return;
  isRemoteSyncing = true;
  try {
    await fetchWithTimeout("/api/records", {
      method: "PUT",
      headers: { "content-type": "application/json" },
      body: JSON.stringify(records),
      timeout: 1200,
    });
  } catch {
    remoteSyncAvailable = false;
  } finally {
    isRemoteSyncing = false;
  }
}

function fetchWithTimeout(url, options = {}) {
  const { timeout = 1000, ...fetchOptions } = options;
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeout);
  return fetch(url, { ...fetchOptions, signal: controller.signal }).finally(() => clearTimeout(timer));
}

function sum(list) {
  return list.reduce((total, record) => total + record.amount, 0);
}

function sumBy(list, key) {
  return list.reduce((acc, record) => {
    acc[record[key]] = (acc[record[key]] || 0) + record.amount;
    return acc;
  }, {});
}

function latestMonth(list) {
  return list[0]?.date.slice(0, 7) || "";
}

function currentMonth() {
  const now = new Date();
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}`;
}

function pad(value) {
  return String(value).padStart(2, "0");
}

function formatMoney(value) {
  return `¥${Number(value || 0).toFixed(2)}`;
}

function sourceMix(list) {
  if (!list.length) return "暂无";
  const counts = sumBy(list.map((record) => ({ ...record, amount: 1 })), "source");
  return Object.entries(counts).map(([source, count]) => `${source}${count}`).join(" / ");
}

function makeId(input) {
  let hash = 0;
  for (let index = 0; index < input.length; index += 1) {
    hash = (hash << 5) - hash + input.charCodeAt(index);
    hash |= 0;
  }
  return `r_${Math.abs(hash)}`;
}

function escapeHtml(value) {
  return String(value).replace(/[&<>"']/g, (char) => ({
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    "\"": "&quot;",
    "'": "&#039;",
  })[char]);
}

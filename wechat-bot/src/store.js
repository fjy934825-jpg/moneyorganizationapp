import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";

const DATA_FILE = resolve(process.env.MONEY_APP_DATA_FILE || "data/records.json");

export async function readRecords() {
  try {
    return JSON.parse(await readFile(DATA_FILE, "utf8"));
  } catch (error) {
    if (error.code === "ENOENT") return [];
    throw error;
  }
}

export async function addRecords(records) {
  const existing = await readRecords();
  const map = new Map(existing.map((record) => [record.id, record]));
  records.forEach((record) => map.set(record.id, record));
  const merged = [...map.values()].sort((a, b) => b.date.localeCompare(a.date));
  await mkdir(dirname(DATA_FILE), { recursive: true });
  await writeFile(DATA_FILE, `${JSON.stringify(merged, null, 2)}\n`, "utf8");
  return { records: merged, added: merged.length - existing.length };
}

export async function replaceRecords(records) {
  const sorted = [...records].sort((a, b) => b.date.localeCompare(a.date));
  await mkdir(dirname(DATA_FILE), { recursive: true });
  await writeFile(DATA_FILE, `${JSON.stringify(sorted, null, 2)}\n`, "utf8");
  return sorted;
}

import { execFile } from "node:child_process";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { homedir } from "node:os";
import { dirname, join } from "node:path";
import { promisify } from "node:util";
import { run } from "./run.ts";

const execFileAsync = promisify(execFile);
const configDir = join(homedir(), ".config", "claude-watch");

const result = await run({
  now: () => new Date(),
  fetch: globalThis.fetch,
  exec: async (cmd, args) => (await execFileAsync(cmd, args, { timeout: 10_000 })).stdout,
  readFile: async (path) => {
    try {
      return await readFile(path, "utf8");
    } catch (e) {
      if ((e as NodeJS.ErrnoException).code === "ENOENT") return null;
      throw e;
    }
  },
  writeFile: async (path, data) => {
    await mkdir(dirname(path), { recursive: true });
    await writeFile(path, data, { mode: 0o600 });
  },
  log: (msg) => console.log(`[${new Date().toISOString()}] ${msg}`),
  paths: {
    claudeJson: join(homedir(), ".claude.json"),
    config: join(configDir, "config.json"),
    state: join(configDir, "state.json"),
  },
  dryRun: process.argv.includes("--dry-run"),
});

process.exitCode = result.exitCode;

import fs from "node:fs/promises";
import { runBrowserGates } from "./browser-gate-core.mjs";

function requestPath(argumentsList) {
  const index = argumentsList.indexOf("--request");
  if (index < 0 || !argumentsList[index + 1]) throw new Error("사용법: node scripts/browser-gate.mjs --request <request.json>");
  return argumentsList[index + 1];
}

try {
  const file = requestPath(process.argv.slice(2));
  const request = JSON.parse(await fs.readFile(file, "utf8"));
  const report = await runBrowserGates(request);
  process.stdout.write(`${JSON.stringify(report)}\n`);
} catch (error) {
  process.stderr.write(`${error?.stack ?? error}\n`);
  process.exitCode = 1;
}

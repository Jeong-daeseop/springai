#!/usr/bin/env node

const http = require("http");
const https = require("https");

const token = process.env.FIGMA_ACCESS_TOKEN;
const allowedKey = (process.env.FIGMA_ALLOWED_FILE_KEYS || "").split(",")[0].trim();
const nodeId = process.env.FIGMA_RELEASE_A_NODE_ID || "2499:38449";
const figmaUrl = process.env.FIGMA_RELEASE_A_URL
    || `https://www.figma.com/design/${allowedKey}/release-a-review?node-id=${nodeId.replace(":", "-")}`;
const mcpPort = Number(process.env.FIGMA_RELEASE_A_MCP_PORT || "8080");
const rateOnly = process.argv.includes("--rate-only");

function figmaRequest(path) {
    return new Promise((resolve, reject) => {
        const request = https.get({
            hostname: "api.figma.com",
            path,
            headers: {Accept: "application/json", "X-Figma-Token": token}
        }, response => {
            let body = "";
            response.on("data", chunk => body += chunk);
            response.on("end", () => resolve({
                status: response.statusCode,
                retryAfter: response.headers["retry-after"] || null,
                planTier: response.headers["x-figma-plan-tier"] || null,
                rateLimitType: response.headers["x-figma-rate-limit-type"] || null,
                body
            }));
        });
        request.on("error", reject);
        request.setTimeout(30_000, () => request.destroy(new Error("Figma API timeout")));
    });
}

function rpc(payload, session) {
    return new Promise((resolve, reject) => {
        const data = JSON.stringify(payload);
        const headers = {
            "Content-Type": "application/json",
            Accept: "application/json, text/event-stream",
            "Content-Length": Buffer.byteLength(data)
        };
        if (session) headers["Mcp-Session-Id"] = session;
        const request = http.request({
            hostname: "127.0.0.1", port: mcpPort, path: "/mcp", method: "POST", headers
        }, response => {
            let body = "";
            response.on("data", chunk => body += chunk);
            response.on("end", () => resolve({headers: response.headers, body}));
        });
        request.on("error", reject);
        request.write(data);
        request.end();
    });
}

function parseRpc(body) {
    const lines = body.split(/\r?\n/).filter(line => line.startsWith("data:"));
    return lines.length ? JSON.parse(lines.at(-1).slice(5).trim()) : JSON.parse(body);
}

function readableDuration(value) {
    const seconds = Number(value);
    if (!Number.isFinite(seconds)) return null;
    const days = Math.floor(seconds / 86400);
    const hours = Math.floor((seconds % 86400) / 3600);
    const minutes = Math.floor((seconds % 3600) / 60);
    return `${days}일 ${hours}시간 ${minutes}분`;
}

function check(name, pass, actual) {
    return {name, result: pass ? "PASS" : "FAIL", actual};
}

async function main() {
    if (!allowedKey) {
        throw new Error("FIGMA_ALLOWED_FILE_KEYS가 필요합니다.");
    }

    if (rateOnly) {
        if (!token) throw new Error("--rate-only에는 FIGMA_ACCESS_TOKEN이 필요합니다.");
        const probe = await figmaRequest(
            `/v1/files/${encodeURIComponent(allowedKey)}/nodes?ids=${encodeURIComponent(nodeId)}&depth=1`);
        console.log(JSON.stringify({
            overall: probe.status === 200 ? "AVAILABLE" : "BLOCKED",
            reason: probe.status === 200 ? null
                : probe.status === 429 ? "FIGMA_RATE_LIMITED" : `FIGMA_HTTP_${probe.status}`,
            retryAfterSeconds: Number(probe.retryAfter) || null,
            retryAfter: readableDuration(probe.retryAfter),
            planTier: probe.planTier,
            rateLimitType: probe.rateLimitType
        }, null, 2));
        if (probe.status !== 200) process.exitCode = 2;
        return;
    }

    const initialized = await rpc({
        jsonrpc: "2.0", id: 1, method: "initialize",
        params: {protocolVersion: "2024-11-05", capabilities: {}, clientInfo: {name: "release-a-verifier", version: "1"}}
    });
    const session = initialized.headers["mcp-session-id"];
    if (!session) throw new Error(`로컬 MCP 서버(${mcpPort})를 초기화할 수 없습니다.`);
    await rpc({jsonrpc: "2.0", method: "notifications/initialized"}, session);
    const called = await rpc({
        jsonrpc: "2.0", id: 2, method: "tools/call",
        params: {name: "analyzeFigmaReference", arguments: {figmaUrl, nodeId, featureType: "crud"}}
    }, session);
    const response = parseRpc(called.body);
    if (response.error || response.result?.isError) {
        console.log(JSON.stringify({overall: "FAIL", reason: "MCP_ANALYSIS_FAILED"}, null, 2));
        process.exitCode = 1;
        return;
    }

    const content = response.result?.content?.find(item => item.type === "text")?.text;
    const analysis = JSON.parse(content);
    const spec = analysis.uiSpec || {};
    const componentTypes = (spec.components || []).map(component => component.type);
    const actionTypes = (spec.actions || []).map(action => action.type);
    const checks = [
        check("FIGMA 출처", analysis.sourceType === "FIGMA", analysis.sourceType || null),
        check("CRUD 목록 archetype", spec.archetype === "CRUD_LIST", spec.archetype || null),
        check("검색·필터 패널", componentTypes.includes("SEARCH_PANEL"), componentTypes),
        check("카드형 목록 구조", componentTypes.includes("CARD_LIST")
            || componentTypes.includes("TABLE") || componentTypes.includes("LIST"), componentTypes),
        check("페이지네이션", componentTypes.includes("PAGINATION"), componentTypes),
        check("검색 액션", actionTypes.includes("SEARCH"), actionTypes),
        check("신청하기 액션", actionTypes.includes("APPLY"), actionTypes)
    ];
    const failures = checks.filter(item => item.result === "FAIL");
    console.log(JSON.stringify({
        overall: failures.length === 0 ? "PASS" : "REVIEW_REQUIRED",
        checks,
        uncertainties: spec.uncertainties || []
    }, null, 2));
    if (failures.length) process.exitCode = 1;
}

main().catch(error => {
    console.error(JSON.stringify({overall: "ERROR", message: error.message}, null, 2));
    process.exitCode = 1;
});

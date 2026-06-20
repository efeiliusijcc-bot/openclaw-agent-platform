#!/usr/bin/env python3
"""Patch OpenClaw 2026.6.5 dist files to read JeecgBoot draft agents.

The upstream package installed on the server is bundled JavaScript, so this
script applies a narrow, idempotent compatibility patch after npm install.
"""

from __future__ import annotations

import os
from pathlib import Path


DIST = Path(os.environ.get("OPENCLAW_DIST_DIR", "/usr/lib/node_modules/openclaw/dist"))

HELPER = r'''
// JeecgBoot OpenClaw Skill Draft temporary agent registry patch.
import { createRequire as __jeecgCreateRequire } from "node:module";
const __jeecgRequire = __jeecgCreateRequire(import.meta.url);
const __jeecgDraftDebug = process.env.OPENCLAW_DRAFT_AGENT_DEBUG === "1";
function __jeecgDraftLog(event, data = {}) {
	if (!__jeecgDraftDebug && event !== "read_failed") return;
	try {
		console.error("[jeecg-draft-agent] " + JSON.stringify({ event, ...data }));
	} catch {
		console.error("[jeecg-draft-agent] " + event);
	}
}
function __jeecgReadDraftAgentEntries() {
	let registryPath = "";
	try {
		const fs = __jeecgRequire("node:fs");
		const os = __jeecgRequire("node:os");
		const path = __jeecgRequire("node:path");
		registryPath = process.env.OPENCLAW_GATEWAY_DRAFT_AGENT_REGISTRY_PATH || path.join(os.homedir(), ".openclaw", "draft-agents.json");
		const raw = fs.readFileSync(registryPath, "utf8");
		const root = JSON.parse(raw);
		const now = Date.now();
		const entries = Array.isArray(root?.agents) ? root.agents : [];
		const active = [];
		let expired = 0;
		let invalid = 0;
		for (const entry of entries) {
			if (!entry || typeof entry !== "object" || typeof entry.id !== "string" || !entry.id.trim() || typeof entry.workspace !== "string" || !entry.workspace.trim()) {
				invalid++;
				continue;
			}
			const expiredByTtl = typeof entry.expiresAt === "number" && entry.expiresAt <= now;
			if (expiredByTtl) {
				expired++;
				__jeecgDraftLog("expired", { draftAgentsPath: registryPath, agentKey: entry.id, draftId: entry.draftId ?? null, testRunId: entry.testRunId ?? null, expiresAt: entry.expiresAt, now });
				continue;
			}
			const skills = Array.isArray(entry.skills) ? entry.skills : entry.skillSlug ? [entry.skillSlug] : [];
			const skillPath = skills.length > 0 ? path.join(entry.workspace, "skills", skills[0]) : null;
			__jeecgDraftLog("active", {
				draftAgentsPath: registryPath,
				agentKey: entry.id,
				draftId: entry.draftId ?? null,
				testRunId: entry.testRunId ?? null,
				workspaceId: entry.workspaceId ?? null,
				workspacePath: entry.workspace,
				skillPath,
				ttlExpired: false,
				workspaceExists: fs.existsSync(entry.workspace),
				skillPathExists: skillPath ? fs.existsSync(skillPath) : false
			});
			active.push({
				id: entry.id,
				workspace: entry.workspace,
				skills,
				identity: entry.identity && typeof entry.identity === "object" ? entry.identity : { name: entry.id },
				...(entry.model && typeof entry.model === "object" ? { model: entry.model } : {})
			});
		}
		__jeecgDraftLog("read", { draftAgentsPath: registryPath, total: entries.length, active: active.length, expired, invalid });
		return active;
	} catch (err) {
		__jeecgDraftLog("read_failed", { draftAgentsPath: registryPath, message: err instanceof Error ? err.message : String(err) });
		return [];
	}
}
function __jeecgMergeDraftAgents(cfg) {
	const draftAgents = __jeecgReadDraftAgentEntries();
	if (draftAgents.length === 0) return cfg;
	const agents = cfg?.agents && typeof cfg.agents === "object" ? cfg.agents : {};
	const list = Array.isArray(agents.list) ? agents.list : [];
	const existing = new Set(list.map((entry) => normalizeAgentId(entry?.id)));
	const mergedList = list.slice();
	for (const entry of draftAgents) {
		const id = normalizeAgentId(entry.id);
		if (!id || existing.has(id)) continue;
		mergedList.push({ ...entry, id });
		existing.add(id);
	}
	__jeecgDraftLog("merge", { active: draftAgents.length, merged: mergedList.length - list.length, agentKeys: draftAgents.map((entry) => normalizeAgentId(entry.id)) });
	return { ...cfg, agents: { ...agents, list: mergedList } };
}
'''


def patch_file(name: str, replacements: list[tuple[str, str]]) -> None:
    path = DIST / name
    text = path.read_text(encoding="utf-8")
    marker = "//#region "
    if "JeecgBoot OpenClaw Skill Draft temporary agent registry patch." in text:
        start = text.index("// JeecgBoot OpenClaw Skill Draft temporary agent registry patch.")
        end = text.index(marker, start)
        text = text[:start] + HELPER + "\n" + text[end:]
    elif "__jeecgMergeDraftAgents" not in text:
        index = text.index(marker)
        text = text[:index] + HELPER + "\n" + text[index:]
    for old, new in replacements:
        if old in text:
            text = text.replace(old, new, 1)
        elif new not in text:
            raise RuntimeError(f"Patch target not found in {path}: {old}")
    path.write_text(text, encoding="utf-8")
    print(f"patched {path}")


def main() -> None:
    patch_file(
        "client-1OJ6okpi.js",
        [
            (
                '''\t\tws.on("close", (code, reason) => {
\t\t\tconst reasonText = rawDataToString(reason);''',
                '''\t\tws.on("close", (code, reason) => {
\t\t\tconst reasonText = rawDataToString(reason);
\t\t\ttry {
\t\t\t\tif (process.env.OPENCLAW_DRAFT_AGENT_DEBUG === "1" || code !== 1000) console.error("[jeecg-gateway-ws-close] " + JSON.stringify({ code, reason: reasonText }));
\t\t\t} catch {
\t\t\t\tif (process.env.OPENCLAW_DRAFT_AGENT_DEBUG === "1" || code !== 1000) console.error("[jeecg-gateway-ws-close] code=" + code);
\t\t\t}''',
            )
        ],
    )
    patch_file(
        "agent-scope-config-KLbWcRY1.js",
        [
            (
                "function listAgentEntries(cfg) {\n\tconst list = cfg.agents?.list;\n\tif (!Array.isArray(list)) return [];\n\treturn list.filter((entry) => entry !== null && typeof entry === \"object\");\n}",
                "function listAgentEntries(cfg) {\n\tconst list = cfg.agents?.list;\n\tconst base = Array.isArray(list) ? list.filter((entry) => entry !== null && typeof entry === \"object\") : [];\n\tconst draftEntries = __jeecgReadDraftAgentEntries();\n\tif (draftEntries.length === 0) return base;\n\tconst seen = new Set(base.map((entry) => normalizeAgentId(entry?.id)));\n\tconst merged = base.slice();\n\tfor (const entry of draftEntries) {\n\t\tconst id = normalizeAgentId(entry?.id);\n\t\tif (!id || seen.has(id)) continue;\n\t\tmerged.push({ ...entry, id });\n\t\tseen.add(id);\n\t}\n\treturn merged;\n}",
            )
        ],
    )
    patch_file(
        "agent-via-gateway-kIPK668y.js",
        [("let cfg = await getGatewayDispatchConfig();", "let cfg = __jeecgMergeDraftAgents(await getGatewayDispatchConfig());")],
    )
    patch_file(
        "agent-YgzFw64q.js",
        [("const cfg = context.getRuntimeConfig();", "const cfg = __jeecgMergeDraftAgents(context.getRuntimeConfig());")],
    )


if __name__ == "__main__":
    main()

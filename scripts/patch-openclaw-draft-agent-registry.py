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
function __jeecgReadDraftAgentEntries() {
	try {
		const fs = __jeecgRequire("node:fs");
		const os = __jeecgRequire("node:os");
		const path = __jeecgRequire("node:path");
		const registryPath = process.env.OPENCLAW_GATEWAY_DRAFT_AGENT_REGISTRY_PATH || path.join(os.homedir(), ".openclaw", "draft-agents.json");
		const raw = fs.readFileSync(registryPath, "utf8");
		const root = JSON.parse(raw);
		const now = Date.now();
		const entries = Array.isArray(root?.agents) ? root.agents : [];
		return entries.filter((entry) => {
			if (!entry || typeof entry !== "object") return false;
			if (typeof entry.id !== "string" || !entry.id.trim()) return false;
			if (typeof entry.workspace !== "string" || !entry.workspace.trim()) return false;
			if (typeof entry.expiresAt === "number" && entry.expiresAt <= now) return false;
			return true;
		}).map((entry) => ({
			id: entry.id,
			workspace: entry.workspace,
			skills: Array.isArray(entry.skills) ? entry.skills : entry.skillSlug ? [entry.skillSlug] : [],
			identity: entry.identity && typeof entry.identity === "object" ? entry.identity : { name: entry.id }
		}));
	} catch {
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
	return { ...cfg, agents: { ...agents, list: mergedList } };
}
'''


def patch_file(name: str, replacements: list[tuple[str, str]]) -> None:
    path = DIST / name
    text = path.read_text(encoding="utf-8")
    if "__jeecgMergeDraftAgents" not in text:
        marker = "//#region "
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

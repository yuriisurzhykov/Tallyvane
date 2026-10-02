// Prints what is left in the frontend build context after the root .dockerignore, and which
// directories weigh the most. Not Docker's own matcher — it prunes by directory name only —
// but it never follows junctions, so pnpm's links are not counted twice.
//
//   node ops/local/context-size.mjs

import { readdirSync, lstatSync, readFileSync } from "node:fs";
import { join, relative, sep } from "node:path";

const root = process.cwd();
const ignored = readFileSync(join(root, ".dockerignore"), "utf8")
  .split(/\r?\n/)
  .map((line) => line.trim())
  .filter((line) => line && !line.startsWith("#") && !/[*!]/.test(line.replace(/^\*\*\//, "")))
  .map((line) => line.replace(/^\*\*\//, "").replace(/\/$/, ""));

const sizes = new Map();

const walk = (dir) => {
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const path = join(dir, entry.name);
    const rel = relative(root, path).split(sep).join("/");
    if (ignored.includes(entry.name) || ignored.includes(rel)) continue;
    if (entry.isDirectory()) {
      walk(path);
    } else if (entry.isFile()) {
      const bucket = rel.split("/").slice(0, 3).join("/");
      sizes.set(bucket, (sizes.get(bucket) ?? 0) + lstatSync(path).size);
    }
  }
};

walk(root);

const rows = [...sizes].sort((a, b) => b[1] - a[1]);
const total = rows.reduce((sum, [, bytes]) => sum + bytes, 0);
console.log(`left in context: ${(total / 1048576).toFixed(1)} MB`);
for (const [name, bytes] of rows.slice(0, 15)) {
  console.log(`${(bytes / 1048576).toFixed(1).padStart(9)} MB  ${name}`);
}

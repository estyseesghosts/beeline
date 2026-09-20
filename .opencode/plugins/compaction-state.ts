import { readdir, readFile } from "node:fs/promises";
import { join } from "node:path";

const TASK_DIR = "docs/agents/tasks";
const TEMPLATE = "_template.md";

const MAX_FILE_CHARS = 24_000;
const MAX_TOTAL_CHARS = 64_000;

function truncateMiddle(text: string, limit: number): string {
  if (text.length <= limit) return text;

  const marker =
    "\n\n[... truncated; reread the repository task file before acting ...]\n\n";

  const available = Math.max(0, limit - marker.length);
  const head = Math.ceil(available / 2);
  const tail = Math.floor(available / 2);

  return text.slice(0, head) + marker + text.slice(-tail);
}

export default {
  id: "long-horizon-state",

  async setup(ctx: any) {
    await ctx.session.hook("compaction", async (event: any) => {
      const root = ctx.location.directory;
      const taskDir = join(root, TASK_DIR);

      let entries;
      try {
        entries = await readdir(taskDir, { withFileTypes: true });
      } catch {
        return;
      }

      const files = entries
        .filter(
          (entry) =>
            entry.isFile() &&
            entry.name.endsWith(".md") &&
            entry.name !== TEMPLATE,
        )
        .map((entry) => entry.name)
        .sort((a, b) => a.localeCompare(b));

      if (files.length === 0) return;

      const sections: string[] = [
        `## Repository task-state contract

The repository owns the current task state.

Task-state files are stored under \`${TASK_DIR}\`.

Preserve important task state from the files below in the compaction summary.

After compaction, reread the relevant task-state files and inspect Git before making changes.

Repository state overrides information preserved in the compaction summary.`,
      ];

      let remaining = MAX_TOTAL_CHARS;

      for (const file of files) {
        if (remaining <= 0) {
          sections.push(
            `Additional task-state files were omitted because the size limit was reached. Reread \`${TASK_DIR}\` before acting.`,
          );
          break;
        }

        try {
          const body = await readFile(join(taskDir, file), "utf8");
          const limit = Math.min(MAX_FILE_CHARS, remaining);
          const included = truncateMiddle(body, limit);

          sections.push(
            `## Repository task state: ${TASK_DIR}/${file}\n\n${included}`,
          );

          remaining -= included.length;
        } catch {
          // Do not break compaction because one task file is unreadable.
        }
      }

      event.system.push({
        type: "text",
        text: sections.join("\n\n"),
      });
    });
  },
};

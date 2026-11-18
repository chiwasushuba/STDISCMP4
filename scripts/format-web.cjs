const fs = require("node:fs");
const path = require("node:path");
const prettier = require("prettier");
const check = process.argv.includes("--check");
const files = [];
function walk(directory) {
  for (const item of fs.readdirSync(directory, { withFileTypes: true })) {
    const file = path.join(directory, item.name);
    if (item.isDirectory()) walk(file);
    else if (/\.(html|css|js)$/.test(item.name)) files.push(file);
  }
}
async function main() {
  walk("web-app/src/main/resources");
  for (const file of files) {
    const original = fs.readFileSync(file, "utf8");
    // HTML's namespace parser mistakes th:block for a foreign-content namespace.
    const input = original.replaceAll("th:block", "thymeleaf-block");
    const result = (await prettier.format(input, { filepath: file })).replaceAll(
      "thymeleaf-block",
      "th:block",
    );
    if (check && result !== original) {
      console.error("Formatting required: " + file);
      process.exitCode = 1;
    } else if (!check) fs.writeFileSync(file, result);
  }
}
main().catch((error) => {
  console.error(error.message);
  process.exitCode = 1;
});

const { test, expect } = require("@playwright/test");
const { execFileSync } = require("node:child_process");
const { login } = require("./helpers.cjs");

test.skip(process.env.TEST_FAULTS !== "1", "Set TEST_FAULTS=1 only against the local full Docker stack.");

function docker(action, node) {
  execFileSync("docker", ["compose", action, node], { stdio: "pipe", timeout: 30000 });
}

for (const node of ["grade-service", "course-service", "identity-service", "grade-db"]) {
  test(`${node} failure preserves unrelated features and recovers`, async ({ page }) => {
    test.setTimeout(90000);
    await login(page);
    try {
      docker("stop", node);
      const response = await page.goto("/");
      expect(response.status()).toBe(200);
      if (node === "identity-service") {
        await expect(page.locator(".unavailable")).toHaveCount(0);
        const other = await page.context().browser().newContext();
        try {
          const fresh = await other.newPage();
          await fresh.goto("http://localhost:8080/login");
          await fresh.getByLabel("University email").fill("student@northstar.edu");
          await fresh.getByLabel("Password", { exact: true }).fill("deliberately-incorrect-password");
          await fresh.getByRole("button", { name: "Sign in", exact: true }).click();
          await expect(fresh.getByRole("alert")).toContainText("temporarily unavailable");
        } finally { await other.close(); }
      } else {
        await expect(page.locator(".unavailable")).toHaveCount(1);
        await page.goto(node === "course-service" ? "/grades" : "/courses");
        await expect(page.locator(".unavailable")).toHaveCount(0);
      }
      await page.screenshot({ path: `.evidence/browser/outage-${node}.png`, fullPage: true });
    } finally { docker("start", node); }
    await expect(async () => {
      await page.goto("/");
      await expect(page.locator(".unavailable")).toHaveCount(0);
    }).toPass({ timeout: 30000, intervals: [1000, 2000] });
    if (node === "identity-service") {
      // A valid old JWT proves offline verification, not that login has restarted.
      await expect(async () => { await login(page, "student", 2000); })
        .toPass({ timeout: 30000, intervals: [1000, 2000] });
    }
  });
}

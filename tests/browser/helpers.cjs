const fs = require("node:fs");
const { expect } = require("@playwright/test");

async function waitForReady(page) {
  await expect(page.locator("body")).toHaveAttribute("data-ready", "true");
}

async function login(page, role = "student", timeout = 10000) {
  const password = process.env.DEMO_PASSWORD || fs.readFileSync(".env", "utf8").match(/^DEMO_PASSWORD=(.+)$/m)?.[1].trim();
  if (!password) throw new Error("Provide DEMO_PASSWORD or run scripts/setup.ps1.");
  await page.goto("/login");
  await page.getByLabel("University email").fill(`${role}@animoroll.edu`);
  await page.getByLabel("Password", { exact: true }).fill(password);
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
  await expect(page).toHaveURL(role === "faculty" ? /\/faculty$/ : /\/$/, { timeout });
  await waitForReady(page);
}

module.exports = { login, waitForReady };

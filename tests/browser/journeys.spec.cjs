const { test, expect } = require("@playwright/test");
const fs = require("node:fs");
const { login, waitForReady } = require("./helpers.cjs");

test("student searches, enrolls, sees the saved course and reads owned grades", async ({ page }) => {
  await login(page);
  await page.getByRole("link", { name: "Course catalog", exact: true }).click();
  await page.getByLabel("Search courses", { exact: true }).fill("Distributed");
  await page.getByRole("button", { name: "Find courses", exact: true }).click();
  await expect(page.locator(".course-card")).toHaveCount(1);
  const enroll = page.getByRole("button", { name: "Enroll in Distributed Systems", exact: true });
  if (await enroll.count()) {
    await enroll.click();
    await expect(page.getByRole("status")).toContainText("Enrollment confirmed for CS 301");
  } else {
    await expect(page.locator(".course-card")).toContainText("Enrollment confirmed");
    const session = await page.request.get("/api/session");
    expect(session.ok()).toBe(true);
    const { csrf } = await session.json();
    const duplicate = await page.request.post("/api/enroll", {
      form: { courseId: "CS301" },
      headers: { [csrf.headerName]: csrf.token },
    });
    expect(duplicate.ok()).toBe(true);
    expect((await duplicate.json()).message).toBe("You are already enrolled in this course.");
  }
  await page.goto("/enrollment");
  await expect(page.getByRole("heading", { name: "Distributed Systems", exact: true })).toBeVisible();
  await page.reload();
  await expect(page.getByText("Enrollment confirmed", { exact: false })).toHaveCount(1);
  await page.goto("/grades");
  await expect(page.getByText("Data Structures & Algorithms", { exact: true })).toBeVisible();
  await expect(page.getByText("1.50", { exact: true }).first()).toBeVisible();
  await page.getByRole("button", { name: "Sign out", exact: false }).click();
  await expect(page).toHaveURL(/\/login\?signedout/);
  await page.goto("/grades");
  await expect(page).toHaveURL(/\/login$/);
});

test("closed course has no enrollment action and browser writes require CSRF", async ({ page }) => {
  await login(page);
  await page.goto("/courses?search=Human");
  await expect(page.getByText("Enrollment closed", { exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: /Enroll in/ })).toHaveCount(0);
  const response = await page.request.post("/api/enroll", { form: { courseId: "CS301" } });
  expect(response.status()).toBe(403);
  await page.goto("/faculty");
  await expect(page).toHaveURL(/\/$/);
});

test("faculty previews, confirms and verifies saved grades", async ({ page }) => {
  await login(page, "faculty");
  await page.locator(".class-tabs a").filter({ hasText: "CS 301" }).click();
  const file = page.getByLabel("Grade file (.csv)", { exact: true });
  await file.setInputFiles({ name: "large.csv", mimeType: "text/csv", buffer: Buffer.alloc(256 * 1024 + 1, 65) });
  await expect(file).toHaveJSProperty("validationMessage", "Choose a CSV file no larger than 256 KB.");
  await file.setInputFiles({
    name: "grades.csv", mimeType: "text/csv", buffer: Buffer.from("student_id,grade\n2024-001,1.25\n2024-002,1.50\n"),
  });
  await page.getByRole("button", { name: "Preview grades", exact: false }).click();
  await expect(page.getByRole("heading", { name: "2 grade rows ready for review", exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Confirm & publish grades", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("2 grades saved");
  await expect(page.locator(".table-panel tr").filter({ hasText: "2024-001" })).toContainText("1.25");
  await page.reload();
  await expect(page.locator(".table-panel tr").filter({ hasText: "2024-001" })).toContainText("1.25");
  await page.getByLabel("Grade file (.csv)", { exact: true }).setInputFiles({
    name: "invalid.csv", mimeType: "text/csv", buffer: Buffer.from("student_id,grade\n2024-001,2.00\nUNKNOWN,1.50\n"),
  });
  await page.getByRole("button", { name: "Preview grades", exact: false }).click();
  await page.getByRole("button", { name: "Confirm & publish grades", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("not in this class roster");
  await expect(page.locator(".table-panel tr").filter({ hasText: "2024-001" })).toContainText("1.25");
  await page.getByRole("button", { name: "Discard preview", exact: true }).click();
  await expect(page.getByRole("button", { name: "Confirm & publish grades", exact: true })).toHaveCount(0);

  await page.getByRole("button", { name: "Sign out", exact: false }).click();
  await login(page);
  await page.goto("/grades");
  await expect(page.getByRole("row").filter({ hasText: "Distributed Systems" })).toContainText("1.25");
});

test("faculty confirmation applies only to the preview shown in its tab", async ({ page }) => {
  await login(page, "faculty");
  await page.locator(".class-tabs a").filter({ hasText: "CS 301" }).click();
  const otherTab = await page.context().newPage();
  await otherTab.goto(page.url());

  for (const tab of [page, otherTab]) {
    await tab.getByLabel("Grade file (.csv)", { exact: true }).setInputFiles({
      name: "grades.csv", mimeType: "text/csv", buffer: Buffer.from("student_id,grade\n2024-001,1.25\n"),
    });
    await tab.getByRole("button", { name: "Preview grades", exact: false }).click();
    await expect(tab.getByRole("heading", { name: "1 grade row ready for review", exact: true })).toBeVisible();
  }

  await page.getByRole("button", { name: "Confirm & publish grades", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("This preview is no longer current");
  await otherTab.getByRole("button", { name: "Confirm & publish grades", exact: true }).click();
  await expect(otherTab.getByRole("status")).toContainText("1 grade saved");
});

test("responsive pages fit desktop, tablet and mobile without page overflow", async ({ page }) => {
  await login(page);
  fs.mkdirSync(".evidence/browser", { recursive: true });
  const errors = [];
  page.on("pageerror", (error) => errors.push(error.message));
  for (const [name, width, height] of [["desktop", 1440, 1000], ["tablet", 768, 1024], ["mobile", 390, 844]]) {
    await page.setViewportSize({ width, height });
    for (const [route, label] of [["/", "dashboard"], ["/courses", "catalog"], ["/grades", "grades"]]) {
      const response = await page.goto(route);
      expect(response.status()).toBe(200);
      await waitForReady(page);
      expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth + 1)).toBe(false);
      await page.screenshot({ path: `.evidence/browser/${label}-${name}.png`, fullPage: true });
    }
  }
  expect(errors).toEqual([]);
});

test("faculty workspace fits mobile and essential pages pass automated accessibility checks", async ({ page }) => {
  const axe = fs.readFileSync(require.resolve("axe-core/axe.min.js"), "utf8");
  const inspect = async () => {
    await page.evaluate(axe);
    const violations = await page.evaluate(async () => {
      const result = await window.axe.run(document, { runOnly: { type: "tag", values: ["wcag2a", "wcag2aa", "wcag21aa"] } });
      return result.violations.map(({ id, nodes }) => ({ id, targets: nodes.map(node => node.target) }));
    });
    expect(violations).toEqual([]);
  };
  await page.goto("/login");
  await waitForReady(page);
  await expect(page.getByLabel("University email")).toHaveAttribute("placeholder", "you@animoroll.edu");
  await inspect();
  await login(page, "faculty");
  await inspect();
  fs.mkdirSync(".evidence/browser", { recursive: true });
  for (const [name, width, height] of [["desktop", 1440, 1000], ["mobile", 390, 844]]) {
    await page.setViewportSize({ width, height });
    expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth + 1)).toBe(false);
    await page.screenshot({ path: `.evidence/browser/faculty-${name}.png`, fullPage: true });
  }
  await page.getByRole("button", { name: "Sign out", exact: false }).click();
  await login(page);
  await inspect();
  await page.goto("/courses");
  await waitForReady(page);
  await inspect();
});

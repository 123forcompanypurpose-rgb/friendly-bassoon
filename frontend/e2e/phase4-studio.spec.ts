import { expect, test } from "@playwright/test";

async function waitForVerificationToken(email: string) {
  for (let attempt = 0; attempt < 30; attempt += 1) {
    const inbox = await fetch("http://127.0.0.1:8025/api/v1/messages");
    if (inbox.ok) {
      const payload = await inbox.json() as {
        messages?: Array<{ ID: string; To: Array<{ Address: string }> }>;
      };
      const message = payload.messages?.find((item) =>
        item.To.some((recipient) => recipient.Address === email),
      );
      if (message) {
        const detail = await fetch(`http://127.0.0.1:8025/api/v1/message/${message.ID}`);
        if (detail.ok) {
          const body = await detail.json() as { Text?: string };
          const match = body.Text?.match(/token=([A-Za-z0-9_-]+)/);
          if (match) return match[1];
        }
      }
    }
    await new Promise((resolve) => setTimeout(resolve, 500));
  }
  throw new Error(`Verification email was not received for ${email}`);
}

test("writer can verify, create, publish, and read a chapter", async ({ page, request }) => {
  test.setTimeout(60_000);
  const suffix = Date.now();
  const email = `phase4-${suffix}@example.com`;
  const username = `phase4_${suffix.toString().slice(-8)}`;
  const password = "Phase4pass123";

  await page.goto("/register");
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Username").fill(username);
  await page.getByLabel(/Display name/).fill("Phase Four Writer");
  await page.getByLabel("Password").fill(password);
  await page.getByRole("checkbox").check();
  const [registrationResponse] = await Promise.all([
    page.waitForResponse((response) => response.url().includes("/api/v1/auth/register")),
    page.getByRole("button", { name: "Create account" }).click(),
  ]);
  expect(registrationResponse.status()).toBe(202);
  await expect(page.getByText(/verification email/i)).toBeVisible();

  const token = await waitForVerificationToken(email);
  const verification = await request.post("/api/v1/auth/verify-email", {
    data: { token },
    headers: { "X-Requested-With": "novelverse" },
  });
  expect(verification.status()).toBe(204);

  await page.goto("/login");
  await page.getByLabel("Email or username").fill(username);
  await page.getByLabel("Password").fill(password);
  const [loginResponse] = await Promise.all([
    page.waitForResponse((response) => response.url().includes("/api/v1/auth/login")),
    page.getByRole("button", { name: "Log in" }).click(),
  ]);
  expect(loginResponse.status()).toBe(200);
  await expect(page).toHaveURL(/\/studio|\/$/);

  await page.goto("/studio");
  await page.getByRole("button", { name: "New story" }).click();
  await page.getByLabel("Title").fill(`Phase 4 Story ${suffix}`);
  const genre = page.getByLabel("Primary genre");
  await genre.selectOption({ index: 1 });
  await page.getByLabel("Synopsis").fill("A browser smoke test story.");
  await page.getByRole("button", { name: "Save story" }).click();
  await expect(page.getByText("Draft created.")).toBeVisible();

  await page.getByLabel("New chapter title").fill("The Opening");
  await page.getByRole("button", { name: "Add chapter" }).click();
  await expect(page.getByText("Chapter draft created.")).toBeVisible();

  await page.locator(".studio-editor .tiptap").fill("This chapter was written by the Phase 4 smoke test.");
  const [publishChapterResponse] = await Promise.all([
    page.waitForResponse((response) => response.url().includes("/publish")),
    page.getByRole("button", { name: "Publish chapter" }).click(),
  ]);
  expect(publishChapterResponse.status()).toBe(200);

  await page.getByRole("button", { name: "Back to story" }).click();
  const [publishStoryResponse] = await Promise.all([
    page.waitForResponse((response) => response.url().includes("/publish")),
    page.getByRole("button", { name: "Publish story" }).click(),
  ]);
  expect(publishStoryResponse.status()).toBe(200);

  const viewStory = page.getByRole("link", { name: "View story" });
  await expect(viewStory).toBeVisible();
  const storyUrl = await viewStory.getAttribute("href");
  expect(storyUrl).toBeTruthy();
  await page.context().clearCookies();
  await page.goto(storyUrl!);
  await expect(page.locator("h1")).toHaveText(`Phase 4 Story ${suffix}`);
  await expect(page.getByText("The Opening")).toBeVisible();
});

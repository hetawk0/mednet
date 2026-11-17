import { expect, test, type Locator, type Page } from "@playwright/test";

/**
 * Official MedNet branding: the full-colour logo on light surfaces, the reversed logo in the expanded
 * workspace sidebar, the favicon mark in the collapsed rail, and the favicon/touch icons in <head>.
 * Each placement must load, stay inside its header, keep the "MedNet home" link name, and leave the
 * page without horizontal scroll. Workspace placements use the /preview shell (no API needed).
 */
async function expectLogo(link: Locator, src: string, height: number) {
  await expect(link).toHaveAttribute("href", "/");
  const image = link.locator("img");
  await expect(image).toHaveAttribute("src", src);
  await expect(image).toHaveAttribute("alt", "");
  await expect.poll(() => image.evaluate((el: HTMLImageElement) => el.complete && el.naturalWidth > 0)).toBe(true);
  const box = await image.boundingBox();
  expect(Math.round(box!.height)).toBe(height);
}

async function expectNoHorizontalScroll(page: Page) {
  expect(await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(0);
}

const wide = (page: Page) => (page.viewportSize()?.width ?? 0) >= 768;

test("public header and footer show the full-colour logo", async ({ page }) => {
  await page.goto("/");
  const header = page.locator("header").first();
  await expectLogo(header.getByRole("link", { name: "MedNet home" }), "/brand/mednet-logo.webp", wide(page) ? 40 : 28);
  // The logo never touches the header actions.
  const logo = await header.getByRole("link", { name: "MedNet home" }).boundingBox();
  const book = await header.getByRole("link", { name: "Book an appointment" }).boundingBox();
  expect(book!.x - (logo!.x + logo!.width)).toBeGreaterThanOrEqual(12);
  // The footer logo loads lazily, so it loads once the footer is scrolled into view.
  const footerHome = page.locator("footer").getByRole("link", { name: "MedNet home" });
  await expect(footerHome.locator("img")).toHaveAttribute("loading", "lazy");
  await footerHome.scrollIntoViewIfNeeded();
  await expectLogo(footerHome, "/brand/mednet-logo.webp", 40);
  await expectNoHorizontalScroll(page);
});

for (const path of ["/sign-in", "/sign-up/provider", "/verify", "/reset"]) {
  test(`${path} shows the full-colour logo`, async ({ page }) => {
    await page.route("**/api/v1/auth/session", (route) => route.fulfill({ status: 401, json: {} }));
    await page.route("**/api/v1/auth/google/status", (route) => route.fulfill({ json: { enabled: false } }));
    await page.goto(path);
    await expectLogo(page.locator("header").getByRole("link", { name: "MedNet home" }), "/brand/mednet-logo.webp", wide(page) ? 40 : 32);
    await expectNoHorizontalScroll(page);
  });
}

test("the workspace sidebar uses the reversed logo, or the favicon mark when collapsed", async ({ page }) => {
  await page.goto("/preview/finance");
  const home = page.locator("aside").getByRole("link", { name: "MedNet home" }).locator("visible=true");
  await expect(home).toHaveCount(1);
  if ((page.viewportSize()?.width ?? 0) >= 1200) {
    await expectLogo(home, "/brand/mednet-logo-reversed.webp", 40);
  } else {
    await expectLogo(home, "/brand/mednet-mark.webp", 24);
    await expect(home.locator("span")).toHaveCSS("background-color", "rgb(255, 255, 255)");
  }
  await expectNoHorizontalScroll(page);
});

test("the browser tab and Apple touch icons are the official favicon", async ({ page }) => {
  await page.goto("/");
  const icons = await page.evaluate(() =>
    [...document.querySelectorAll<HTMLLinkElement>("link[rel~=icon], link[rel=apple-touch-icon]")].map((l) => ({
      rel: l.rel,
      href: l.getAttribute("href")!,
      sizes: l.getAttribute("sizes"),
    })),
  );
  expect(icons.map((icon) => `${icon.rel} ${icon.href.split("?")[0]} ${icon.sizes}`).sort()).toEqual([
    "apple-touch-icon /apple-icon.png 180x180",
    "icon /favicon.ico 48x48",
    "icon /icon.png 512x512",
  ]);
  for (const icon of icons) {
    const response = await page.request.get(icon.href);
    expect(response.status(), icon.href).toBe(200);
  }
});

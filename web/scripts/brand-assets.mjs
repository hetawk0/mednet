/**
 * Builds the optimised branding derivatives from the official master assets in public/images.
 * The masters are only read. Run from web/: `node scripts/brand-assets.mjs`.
 *
 * Uses `sharp`, which Next.js already installs for image optimisation (no new dependency).
 * Derived copies are cropped to the artwork (the masters carry uneven transparent padding),
 * resized, and otherwise unchanged: colours, proportions and design come from the masters.
 */
import { mkdir, readFile, writeFile } from "node:fs/promises";
import path from "node:path";
import sharp from "sharp";

const root = path.resolve(import.meta.dirname, "..");
const masters = path.join(root, "public/images");
const brandDir = path.join(root, "public/brand");
const appDir = path.join(root, "src/app");

// Artwork bounds measured on the masters (both logos share one canvas and one bounding box).
const LOGO_BOX = { left: 149, top: 132, width: 3510, height: 860 };
const MARK_BOX = { left: 215, top: 142, width: 1850, height: 1219 };

/** Logos are shown at most 40px tall; 120px covers 3x screens. */
const LOGO_HEIGHT = 120;
/** The collapsed-rail mark is shown 24px tall. */
const MARK_HEIGHT = 96;

const lossless = { lossless: true, effort: 6 };

async function logo(master, name) {
  const output = path.join(brandDir, `${name}.webp`);
  const info = await sharp(path.join(masters, master))
    .extract(LOGO_BOX)
    .resize({ height: LOGO_HEIGHT, kernel: "lanczos3" })
    .webp(lossless)
    .toFile(output);
  return { file: output, ...info };
}

async function mark() {
  const output = path.join(brandDir, "mednet-mark.webp");
  const info = await sharp(path.join(masters, "MedNeT_Favicon.png"))
    .extract(MARK_BOX)
    .resize({ height: MARK_HEIGHT, kernel: "lanczos3" })
    .webp(lossless)
    .toFile(output);
  return { file: output, ...info };
}

/** The favicon artwork centred on a square canvas: transparent, or on white for Apple touch icons. */
async function squareIcon(size, background) {
  const inner = Math.round(size * 0.92);
  const art = await sharp(path.join(masters, "MedNeT_Favicon.png"))
    .extract(MARK_BOX)
    .resize({ width: inner, height: inner, fit: "inside", kernel: "lanczos3" })
    .png()
    .toBuffer();
  return sharp({ create: { width: size, height: size, channels: 4, background } })
    .composite([{ input: art, gravity: "center" }])
    .png({ compressionLevel: 9, palette: false })
    .toBuffer();
}

/** A .ico whose entries are PNG images (supported by every current browser). */
function ico(entries) {
  const header = Buffer.alloc(6);
  header.writeUInt16LE(0, 0);
  header.writeUInt16LE(1, 2);
  header.writeUInt16LE(entries.length, 4);
  const directory = Buffer.alloc(16 * entries.length);
  let offset = 6 + directory.length;
  entries.forEach(({ size, png }, index) => {
    const at = index * 16;
    directory.writeUInt8(size >= 256 ? 0 : size, at);
    directory.writeUInt8(size >= 256 ? 0 : size, at + 1);
    directory.writeUInt8(0, at + 2);
    directory.writeUInt8(0, at + 3);
    directory.writeUInt16LE(1, at + 4);
    directory.writeUInt16LE(32, at + 6);
    directory.writeUInt32LE(png.length, at + 8);
    directory.writeUInt32LE(offset, at + 12);
    offset += png.length;
  });
  return Buffer.concat([header, directory, ...entries.map((entry) => entry.png)]);
}

await mkdir(brandDir, { recursive: true });
const transparent = { r: 0, g: 0, b: 0, alpha: 0 };
const results = [
  await logo("MedNet-Logo.png", "mednet-logo"),
  await logo("MedNeT_Reversed_Logo.png", "mednet-logo-reversed"),
  await mark(),
];

const icon = await squareIcon(512, transparent);
await writeFile(path.join(appDir, "icon.png"), icon);
// iOS fills transparency with black, so the touch icon sits on white.
const appleIcon = await squareIcon(180, { r: 255, g: 255, b: 255, alpha: 1 });
await writeFile(path.join(appDir, "apple-icon.png"), appleIcon);
const favicon = ico(await Promise.all([16, 32, 48].map(async (size) => ({ size, png: await squareIcon(size, transparent) }))));
await writeFile(path.join(appDir, "favicon.ico"), favicon);

for (const result of results) {
  console.log(`${path.relative(root, result.file)}  ${result.width}x${result.height}  ${result.size} bytes`);
}
for (const [name, buffer] of [["src/app/icon.png", icon], ["src/app/apple-icon.png", appleIcon], ["src/app/favicon.ico", favicon]]) {
  console.log(`${name}  ${buffer.length} bytes`);
}
console.log("masters:", (await Promise.all(["MedNet-Logo.png", "MedNeT_Reversed_Logo.png", "MedNeT_Favicon.png"].map(async (file) => `${file} ${(await readFile(path.join(masters, file))).length} bytes`))).join(", "));

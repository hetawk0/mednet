import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { Wordmark } from "./wordmark";

const artwork = (link: HTMLElement) => link.querySelector("img")!;

describe("Wordmark", () => {
  it("links home with one accessible name and the full-colour logo as decoration", () => {
    render(<Wordmark />);
    const link = screen.getByRole("link", { name: "MedNet home" });
    expect(link).toHaveAttribute("href", "/");
    const image = artwork(link);
    expect(image).toHaveAttribute("src", "/brand/mednet-logo.webp");
    expect(image).toHaveAttribute("alt", "");
    // Intrinsic size reserves space before the image loads (no layout shift).
    expect(image).toHaveAttribute("width", "490");
    expect(image).toHaveAttribute("height", "120");
    expect(image.className).toContain("md:h-10");
    expect(screen.queryByRole("img")).toBeNull();
  });

  it("uses the reversed logo on the dark sidebar", () => {
    render(<Wordmark tone="light" size="lg" />);
    const image = artwork(screen.getByRole("link", { name: "MedNet home" }));
    expect(image).toHaveAttribute("src", "/brand/mednet-logo-reversed.webp");
    expect(image.className).toContain("h-10");
  });

  it("shows the official favicon mark on a light tile in the collapsed rail", () => {
    render(<Wordmark tone="light" compact />);
    const link = screen.getByRole("link", { name: "MedNet home" });
    expect(artwork(link)).toHaveAttribute("src", "/brand/mednet-mark.webp");
    expect(link.querySelector("span")?.className).toContain("bg-white");
    expect(link).not.toHaveTextContent(/\S/);
  });

  it("loads lazily when asked and keeps a custom destination", () => {
    render(<Wordmark priority={false} href="/provider" size="sm" />);
    const link = screen.getByRole("link", { name: "MedNet home" });
    expect(link).toHaveAttribute("href", "/provider");
    expect(artwork(link)).toHaveAttribute("loading", "lazy");
    expect(artwork(link).className).toContain("h-8");
  });
});

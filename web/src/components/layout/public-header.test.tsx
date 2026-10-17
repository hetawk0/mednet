import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { PublicHeader } from "./public-header";
import { PUBLIC_HEADER_LINKS } from "./public-nav";

vi.mock("next/navigation", () => ({ usePathname: () => "/about" }));

describe("PublicHeader", () => {
  it("shows every reference link with its route", () => {
    render(<PublicHeader />);
    const nav = screen.getAllByRole("navigation", { name: "Main", hidden: true })[0];
    for (const link of PUBLIC_HEADER_LINKS) {
      expect(within(nav).getByRole("link", { name: link.label })).toHaveAttribute("href", link.href);
    }
    expect(screen.getAllByRole("link", { name: "Sign in", hidden: true })[0]).toHaveAttribute("href", "/sign-in");
    expect(screen.getByRole("link", { name: "Book an appointment" })).toHaveAttribute("href", "/find-care");
  });

  it("toggles the compact menu with an accessible button", async () => {
    render(<PublicHeader />);
    const button = screen.getByRole("button", { name: "Open menu" });
    const menu = document.getElementById("public-mobile-menu");

    expect(button).toHaveAttribute("aria-expanded", "false");
    expect(menu).not.toBeVisible();

    await userEvent.click(button);

    expect(screen.getByRole("button", { name: "Close menu" })).toHaveAttribute("aria-expanded", "true");
    expect(menu).toBeVisible();
  });
});

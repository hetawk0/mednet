import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import type { Page } from "@/features/find-care/provider-api";
import { MessageThread, type ThreadMessage } from "./message-thread";

function message(id: string): ThreadMessage {
  return {
    id,
    senderAccountId: "patient-1",
    body: `Message ${id}`,
    createdAt: "2026-10-25T12:00:00Z",
  };
}

function page(content: ThreadMessage[], number: number, totalPages: number): Page<ThreadMessage> {
  return { content, number, totalPages, totalElements: totalPages * 2, size: 100 };
}

function renderThread(loadPage: (page: number, size: number, signal: AbortSignal) => Promise<Page<ThreadMessage>>) {
  return render(
    <MessageThread
      threadKey="conversation-1"
      eyebrow="Conversation"
      title="Dr. Doe"
      listLabel="Messages with Dr. Doe"
      loadPage={loadPage}
      send={vi.fn()}
      isMine={() => false}
      otherLabel="Dr. Doe"
    />,
  );
}

describe("MessageThread history pagination", () => {
  it("loads only the first and newest pages initially, then walks back without gaps or duplicates", async () => {
    const user = userEvent.setup();
    const pages = [
      page([message("1"), message("2"), message("3")], 0, 3),
      page([message("3"), message("4")], 1, 3),
      page([message("4"), message("5")], 2, 3),
    ];
    const loadPage = vi.fn(async (...args: [number, number, AbortSignal]) => pages[args[0]]!);

    renderThread(loadPage);

    const list = await screen.findByRole("list", { name: "Messages with Dr. Doe" });
    expect(loadPage.mock.calls.map(([pageNumber]) => pageNumber)).toEqual([0, 2]);
    expect(within(list).getByText("Message 4")).toBeInTheDocument();
    expect(within(list).getByText("Message 5")).toBeInTheDocument();
    expect(within(list).queryByText("Message 1")).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Load earlier messages" }));
    expect(await within(list).findByText("Message 3")).toBeInTheDocument();
    expect(loadPage.mock.calls.map(([pageNumber]) => pageNumber)).toEqual([0, 2, 1]);

    await user.click(screen.getByRole("button", { name: "Load earlier messages" }));
    expect(await within(list).findByText("Message 1")).toBeInTheDocument();
    expect(loadPage.mock.calls.map(([pageNumber]) => pageNumber)).toEqual([0, 2, 1, 0]);
    expect(within(list).getAllByText(/^Message /).map((item) => item.textContent)).toEqual([
      "Message 1",
      "Message 2",
      "Message 3",
      "Message 4",
      "Message 5",
    ]);
    expect(screen.queryByRole("button", { name: "Load earlier messages" })).not.toBeInTheDocument();
    expect(loadPage.mock.calls.every(([, size]) => size === 100)).toBe(true);
  });

  it("keeps a failed page available and provides an accessible retry", async () => {
    const user = userEvent.setup();
    const failedPage = page([message("1")], 1, 3);
    const loadPage = vi.fn(async (...args: [number, number, AbortSignal]) => {
      const [pageNumber] = args;
      if (pageNumber === 0) return page([message("0")], 0, 3);
      if (pageNumber === 2) return page([message("2")], 2, 3);
      if (loadPage.mock.calls.filter(([requestedPage]) => requestedPage === 1).length === 1) {
        throw new Error("Network unavailable.");
      }
      return failedPage;
    });

    renderThread(loadPage);
    await user.click(await screen.findByRole("button", { name: "Load earlier messages" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Earlier messages could not be loaded.");
    await user.click(screen.getByRole("button", { name: "Retry loading earlier messages" }));

    const list = screen.getByRole("list", { name: "Messages with Dr. Doe" });
    expect(await within(list).findByText("Message 1")).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(loadPage.mock.calls.filter(([pageNumber]) => pageNumber === 1)).toHaveLength(2);
  });

  it("marks the earlier-page request as busy and prevents duplicate requests", async () => {
    const user = userEvent.setup();
    let resolveOlder!: (value: Page<ThreadMessage>) => void;
    let firstPageCalls = 0;
    const loadPage = vi.fn((...args: [number, number, AbortSignal]) => {
      if (args[0] === 0 && firstPageCalls++ === 0) return Promise.resolve(page([message("0")], 0, 2));
      if (args[0] === 0) {
        return new Promise<Page<ThreadMessage>>((resolve) => {
          resolveOlder = resolve;
        });
      }
      return Promise.resolve(page([message("1")], 1, 2));
    });

    renderThread(loadPage);
    const button = await screen.findByRole("button", { name: "Load earlier messages" });
    await user.click(button);

    expect(button).toBeDisabled();
    expect(button).toHaveAttribute("aria-busy", "true");
    await user.click(button);
    expect(loadPage.mock.calls.filter(([pageNumber]) => pageNumber === 1)).toHaveLength(1);

    resolveOlder(page([message("0")], 0, 2));
    expect(await screen.findByText("Message 0")).toBeInTheDocument();
  });

  it("does not reuse messages from the previous thread while the next thread is loading", async () => {
    const user = userEvent.setup();
    const loadPage = vi.fn(async () => page([message("previous-thread")], 0, 1));
    const send = vi.fn(async () => message("sent"));
    const { rerender } = render(
      <MessageThread
        threadKey="conversation-1"
        eyebrow="Conversation"
        title="Dr. Doe"
        listLabel="Messages with Dr. Doe"
        loadPage={loadPage}
        send={send}
        isMine={() => false}
        otherLabel="Dr. Doe"
      />,
    );
    expect(await screen.findByText("Message previous-thread")).toBeInTheDocument();

    loadPage.mockImplementation(() => new Promise(() => {}));
    rerender(
      <MessageThread
        threadKey="conversation-2"
        eyebrow="Conversation"
        title="Dr. Doe"
        listLabel="Messages with Dr. Doe"
        loadPage={loadPage}
        send={send}
        isMine={() => false}
        otherLabel="Dr. Doe"
      />,
    );

    await user.type(screen.getByRole("textbox", { name: "Your message" }), "Hello");
    await user.click(screen.getByRole("button", { name: "Send message" }));

    expect(send).toHaveBeenCalledWith("Hello");
    expect(screen.queryByText("Message previous-thread")).not.toBeInTheDocument();
  });
});

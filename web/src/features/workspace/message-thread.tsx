"use client";

import { MessageSquareText, RefreshCw } from "lucide-react";
import { useEffect, useRef, useState, type FormEvent, type ReactNode } from "react";
import { Button } from "@/components/ui/button";
import { Card, CardHeader } from "@/components/ui/card";
import { TextArea } from "@/components/ui/inputs";
import { StateMessage } from "@/components/ui/state-message";
import type { Page } from "@/features/find-care/provider-api";
import { formatWhen } from "@/features/patient/format";
import { cn } from "@/lib/cn";
import { ErrorRow, LoadingRows } from "./states";
import { actionError, useResource } from "./use-resource";

export type ThreadMessage = { id: string; senderAccountId: string; body: string; createdAt: string };

type ThreadData = { messages: ThreadMessage[]; nextOlderPage: number | null };

const PAGE_SIZE = 100;

/**
 * One message thread (secure messages or a text consultation), shared by the patient and doctor
 * workspaces. The newest page is shown first; older pages load on request. Not real time: Refresh reloads.
 */
export function MessageThread({
  threadKey,
  eyebrow,
  title,
  headerExtra,
  listLabel,
  loadPage,
  send,
  isMine,
  otherLabel,
  canSend = true,
  closedText,
  forbiddenTitle = "Messages could not be loaded.",
  composerDescription = "Do not use messages for emergencies. MedNet does not replace emergency services.",
  children,
}: {
  /** Changing it reloads the thread. */
  threadKey: string;
  eyebrow: string;
  title: ReactNode;
  headerExtra?: ReactNode;
  listLabel: string;
  loadPage: (page: number, size: number, signal: AbortSignal) => Promise<Page<ThreadMessage>>;
  send: (body: string) => Promise<ThreadMessage>;
  isMine: (message: ThreadMessage) => boolean;
  otherLabel: string;
  canSend?: boolean;
  closedText?: ReactNode;
  /** Shown instead of a generic failure when the API answers 403. */
  forbiddenTitle?: string;
  composerDescription?: string;
  /** Extra content between the thread and the composer. */
  children?: ReactNode;
}) {
  const { resource, reload, replace } = useResource(async (signal) => {
    const first = await loadPage(0, PAGE_SIZE, signal);
    if (first.totalPages <= 1) return { messages: first.content, nextOlderPage: null };
    const last = await loadPage(first.totalPages - 1, PAGE_SIZE, signal);
    return { messages: last.content, nextOlderPage: last.number - 1 };
  }, threadKey);
  const [body, setBody] = useState("");
  const [error, setError] = useState("");
  const [sending, setSending] = useState(false);
  const [loadingEarlier, setLoadingEarlier] = useState(false);
  const [earlierError, setEarlierError] = useState("");
  const endRef = useRef<HTMLLIElement>(null);
  const requestIdRef = useRef(0);
  const earlierRequestRef = useRef<AbortController | null>(null);
  const latestDataRef = useRef<ThreadData | null>(null);
  const preserveScrollRef = useRef(false);
  const count = resource.state === "ready" ? resource.data.messages.length : 0;

  useEffect(() => {
    if (resource.state === "ready") latestDataRef.current = resource.data;
  }, [resource]);

  useEffect(() => {
    if (!preserveScrollRef.current) endRef.current?.scrollIntoView?.({ block: "nearest" });
    preserveScrollRef.current = false;
  }, [count]);

  useEffect(
    () => () => {
      requestIdRef.current += 1;
      earlierRequestRef.current?.abort();
      latestDataRef.current = null;
    },
    [threadKey],
  );

  function refresh() {
    requestIdRef.current += 1;
    earlierRequestRef.current?.abort();
    earlierRequestRef.current = null;
    setLoadingEarlier(false);
    setEarlierError("");
    reload();
  }

  async function loadEarlier() {
    if (resource.state !== "ready" || resource.data.nextOlderPage === null || loadingEarlier) return;
    const page = resource.data.nextOlderPage;
    const requestId = ++requestIdRef.current;
    const controller = new AbortController();
    earlierRequestRef.current = controller;
    setLoadingEarlier(true);
    setEarlierError("");
    try {
      const result = await loadPage(page, PAGE_SIZE, controller.signal);
      if (controller.signal.aborted || requestId !== requestIdRef.current) return;
      const current = latestDataRef.current ?? resource.data;
      const seen = new Set(current.messages.map((message) => message.id));
      const olderMessages = result.content.filter((message) => {
        if (seen.has(message.id)) return false;
        seen.add(message.id);
        return true;
      });
      const updated = {
        messages: [...olderMessages, ...current.messages],
        nextOlderPage: page > 0 ? page - 1 : null,
      };
      preserveScrollRef.current = updated.messages.length > current.messages.length;
      latestDataRef.current = updated;
      replace(updated);
    } catch (failure) {
      if (controller.signal.aborted || requestId !== requestIdRef.current) return;
      setEarlierError(actionError(failure, "Earlier messages could not be loaded."));
    } finally {
      if (requestId === requestIdRef.current) {
        earlierRequestRef.current = null;
        setLoadingEarlier(false);
      }
    }
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const text = body.trim();
    if (!text) return setError("Write a message first.");
    setSending(true);
    setError("");
    try {
      const sent = await send(text);
      setBody("");
      const current = latestDataRef.current;
      if (current) {
        const updated = { ...current, messages: [...current.messages, sent] };
        latestDataRef.current = updated;
        replace(updated);
      } else reload();
    } catch (failure) {
      setError(actionError(failure, "Your message could not be sent."));
    } finally {
      setSending(false);
    }
  }

  return (
    <Card>
      <CardHeader
        eyebrow={eyebrow}
        title={title}
        action={
          <div className="flex flex-wrap items-center gap-2">
            {headerExtra}
            <Button variant="tertiary" size="sm" leadingIcon={<RefreshCw aria-hidden size={16} />} onClick={refresh}>
              Refresh
            </Button>
          </div>
        }
      />
      <div className="mt-4 border-t border-border pt-4">
        {resource.state === "loading" ? (
          <LoadingRows label="Loading messages" />
        ) : resource.state === "error" ? (
          resource.status === 403 ? (
            <StateMessage kind="denied" layout="inline" title={forbiddenTitle} />
          ) : (
            <ErrorRow title="Messages could not be loaded." onRetry={reload} status={resource.status} />
          )
        ) : resource.data.messages.length ? (
          <>
            {resource.data.nextOlderPage !== null ? (
              <div className="mb-3 flex flex-col items-start gap-2">
                {earlierError ? <p role="alert" className="text-caption text-critical">{earlierError}</p> : null}
                <Button
                  variant="tertiary"
                  size="sm"
                  loading={loadingEarlier}
                  disabled={loadingEarlier}
                  onClick={loadEarlier}
                >
                  {earlierError ? "Retry loading earlier messages" : "Load earlier messages"}
                </Button>
              </div>
            ) : null}
            <ol aria-label={listLabel} className="flex max-h-[32rem] flex-col gap-3 overflow-y-auto pr-1">
              {resource.data.messages.map((message) => {
                const mine = isMine(message);
                return (
                  <li key={message.id} className={cn("flex", mine ? "justify-end" : "justify-start")}>
                    <div className={cn("max-w-[85%] rounded-md px-4 py-3", mine ? "bg-green-50" : "border border-border bg-surface-soft")}>
                      <p className="text-caption font-semibold text-text-secondary">
                        {mine ? "You" : otherLabel} · {formatWhen(message.createdAt)}
                      </p>
                      <p className="mt-1 text-data whitespace-pre-line text-ink [overflow-wrap:anywhere]">{message.body}</p>
                    </div>
                  </li>
                );
              })}
              <li ref={endRef} aria-hidden className="h-px" />
            </ol>
          </>
        ) : (
          <StateMessage
            kind="empty"
            layout="inline"
            title={canSend ? "No messages yet. Write the first message below." : "No messages were sent."}
          />
        )}
      </div>
      {children}
      {canSend ? (
        <form noValidate onSubmit={submit} className="mt-5 flex flex-col gap-3 border-t border-border pt-5">
          <TextArea
            label="Your message"
            rows={3}
            maxLength={4000}
            value={body}
            error={error || undefined}
            description={composerDescription}
            onChange={(event) => setBody(event.target.value)}
          />
          <div>
            <Button type="submit" loading={sending} leadingIcon={<MessageSquareText aria-hidden size={18} />}>
              Send message
            </Button>
          </div>
        </form>
      ) : closedText ? (
        <p className="mt-5 border-t border-border pt-5 text-data text-text-secondary">{closedText}</p>
      ) : null}
    </Card>
  );
}

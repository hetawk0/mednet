"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import type { Page } from "@/features/find-care/provider-api";
import { ApiRequestError } from "@/lib/api";
import { reportSessionExpired, type Resource } from "./use-resource";

export type PartnerQueueQuery = {
  search: string;
  status: string;
  /** One-based URL/UI page; the API uses zero-based pages. */
  page: number;
};

export function partnerQueueHref(path: string, query: PartnerQueueQuery): string {
  const params = new URLSearchParams();
  if (query.search) params.set("search", query.search);
  if (query.status) params.set("status", query.status);
  if (query.page > 1) params.set("page", String(query.page));
  const suffix = params.toString();
  return suffix ? `${path}?${suffix}` : path;
}

export function usePartnerQueueQuery(path: string) {
  const router = useRouter();
  const searchParams = useSearchParams();
  const search = searchParams.get("search") ?? "";
  const status = searchParams.get("status") ?? "";
  const parsedPage = Number(searchParams.get("page"));
  const page = Number.isSafeInteger(parsedPage) && parsedPage > 0 ? parsedPage : 1;
  const query = { search, status, page };

  const navigate = useCallback(
    (next: PartnerQueueQuery) => router.push(partnerQueueHref(path, next), { scroll: false }),
    [path, router],
  );

  return { query, navigate };
}

export function usePartnerQueuePage<T>(
  load: (signal: AbortSignal) => Promise<Page<T>>,
  key: string,
): { resource: Resource<Page<T>>; reload: () => void } {
  const [loaded, setLoaded] = useState<{ key: string; resource: Resource<Page<T>> } | null>(null);
  const [attempt, setAttempt] = useState(0);
  const resourceKey = JSON.stringify([key, attempt]);

  useEffect(() => {
    const controller = new AbortController();
    load(controller.signal)
      .then((data) => !controller.signal.aborted && setLoaded({ key: resourceKey, resource: { state: "ready", data } }))
      .catch((error: unknown) => {
        if (controller.signal.aborted) return;
        if (error instanceof ApiRequestError && error.isUnauthenticated) reportSessionExpired();
        setLoaded({
          key: resourceKey,
          resource: {
            state: "error",
            status: error instanceof ApiRequestError ? error.status : 0,
            message: error instanceof Error ? error.message : "The request failed.",
          },
        });
      });
    return () => controller.abort();
    // `load` is recreated each render; key and attempt decide when to refetch.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [resourceKey]);

  const reload = useCallback(() => setAttempt((count) => count + 1), []);
  const resource = loaded?.key === resourceKey ? loaded.resource : { state: "loading" as const };
  return { resource, reload };
}

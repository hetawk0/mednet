/**
 * Helpers shared by the browser API stubs so paged and filtered endpoints answer in the same shape as
 * the Spring Boot API (`Page<T>`: zero-based `number`, `size`, `totalElements`, `totalPages`).
 */

/** One page of `rows`, using the request's `page` and `size` parameters as the API does. */
export function pageOf<T>(rows: T[], url: URL, defaultSize = 20) {
  const size = Math.max(1, Number(url.searchParams.get("size") ?? defaultSize));
  const number = Math.max(0, Number(url.searchParams.get("page") ?? 0));
  return {
    content: rows.slice(number * size, number * size + size),
    totalElements: rows.length,
    totalPages: Math.ceil(rows.length / size),
    number,
    size,
  };
}

/** Applies the optional `status` and case-insensitive `search` filters used by request queues. */
export function filterQueue<T extends { status: string }>(rows: T[], url: URL, searchable: (row: T) => string) {
  const status = url.searchParams.get("status");
  const search = (url.searchParams.get("search") ?? "").trim().toLowerCase();
  return rows.filter(
    (row) => (!status || row.status === status) && (!search || searchable(row).toLowerCase().includes(search)),
  );
}

/** `GET`/`PATCH /notifications/preferences` for a verified account, kept per stubbed page. */
export function preferencesStub() {
  const preferences = { emailEnabled: true, emailVerified: true };
  return (method: string, body: { emailEnabled?: boolean } | undefined) => {
    if (method === "PATCH" && typeof body?.emailEnabled === "boolean") preferences.emailEnabled = body.emailEnabled;
    return { ...preferences };
  };
}

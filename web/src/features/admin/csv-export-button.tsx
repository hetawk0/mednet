"use client";

import { useState } from "react";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { downloadCsv, toCsv } from "./csv-export";

export function CsvExportButton({
  filename,
  headers,
  loadRows,
  disabled = false,
}: {
  filename: string;
  headers: string[];
  loadRows: () => Promise<unknown[][]>;
  disabled?: boolean;
}) {
  const [exporting, setExporting] = useState(false);
  const [failed, setFailed] = useState(false);

  async function exportRows() {
    setExporting(true);
    setFailed(false);
    try {
      const rows = await loadRows();
      const date = new Date().toISOString().slice(0, 10);
      downloadCsv(`${filename}-${date}.csv`, toCsv(headers, rows));
    } catch {
      setFailed(true);
    } finally {
      setExporting(false);
    }
  }

  return (
    <div className="flex flex-col items-start gap-2">
      <Button size="sm" variant="secondary" disabled={disabled || exporting} loading={exporting} onClick={() => void exportRows()}>
        {exporting ? "Exporting CSV…" : "Export CSV"}
      </Button>
      {failed ? (
        <Alert tone="critical" live="assertive" title="The CSV export could not be completed. Try again." />
      ) : null}
    </div>
  );
}

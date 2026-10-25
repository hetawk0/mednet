import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { Appointment } from "@/features/patient/patient-api";
import type { Page } from "@/features/find-care/provider-api";
import { PatientAppointments } from "@/features/patient/appointments";
import { ProviderAppointments } from "@/features/provider/appointments";
import { ProviderPatients } from "@/features/provider/patients";
import { patientApi } from "@/features/patient/patient-api";
import { providerApi } from "@/features/provider/provider-api";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: vi.fn() }),
}));

vi.mock("@/features/patient/patient-api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/features/patient/patient-api")>();
  return {
    ...actual,
    patientApi: { ...actual.patientApi, appointments: vi.fn() },
  };
});

vi.mock("@/features/provider/provider-api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/features/provider/provider-api")>();
  return {
    ...actual,
    providerApi: { ...actual.providerApi, appointments: vi.fn(), patients: vi.fn() },
  };
});

const records = Array.from({ length: 101 }, (_, index) => ({
  id: `appointment-${index}`,
  patientAccountId: "patient",
  status: "CANCELLED",
  patientName: `Patient ${index}`,
  providerName: `Provider ${index}`,
  consultationMode: "TEXT" as const,
  startsAt: `2025-01-${String((index % 28) + 1).padStart(2, "0")}T09:00:00Z`,
  endsAt: `2025-01-${String((index % 28) + 1).padStart(2, "0")}T09:30:00Z`,
  proposedStartsAt: null,
  proposedEndsAt: null,
  createdAt: `2025-01-${String((index % 28) + 1).padStart(2, "0")}T09:00:00Z`,
  updatedAt: "",
})) satisfies Appointment[];

function page<T>(rows: T[], number: number): Page<T> {
  const content = rows.slice(number * 25, (number + 1) * 25);
  return { content, number, size: 25, totalElements: rows.length, totalPages: Math.ceil(rows.length / 25) };
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe("appointment history pagination", () => {
  it("loads and navigates every patient page, including records after the first 100", async () => {
    vi.mocked(patientApi.appointments).mockImplementation(async (requestedPage = 0) => page(records, requestedPage));

    render(<PatientAppointments tab="appointments" />);
    expect(await screen.findByRole("heading", { name: "Provider 0" })).toBeInTheDocument();

    for (let index = 1; index < 5; index += 1) {
      fireEvent.click(screen.getByRole("button", { name: "Next page" }));
      expect(await screen.findByRole("heading", { name: `Provider ${index * 25}` })).toBeInTheDocument();
    }
    expect(screen.getByRole("heading", { name: "Provider 100" })).toBeInTheDocument();
    expect(screen.getByText("Showing appointments 101–101 of 101.")).toBeInTheDocument();
    expect(vi.mocked(patientApi.appointments).mock.calls.map(([requestedPage]) => requestedPage)).toEqual([0, 1, 2, 3, 4]);
  });

  it("loads and navigates every provider appointment page", async () => {
    vi.mocked(providerApi.appointments).mockImplementation(async (requestedPage = 0) => page(records, requestedPage));

    render(<ProviderAppointments tab="history" />);
    expect(await screen.findByText("Patient 0")).toBeInTheDocument();

    for (let index = 1; index < 5; index += 1) {
      fireEvent.click(screen.getByRole("button", { name: "Next page" }));
      expect(await screen.findByText(`Patient ${index * 25}`)).toBeInTheDocument();
    }
    expect(screen.getByText("Patient 100")).toBeInTheDocument();
    await waitFor(() => expect(vi.mocked(providerApi.appointments)).toHaveBeenCalledTimes(5));
    expect(vi.mocked(providerApi.appointments).mock.calls.map(([requestedPage]) => requestedPage)).toEqual([0, 1, 2, 3, 4]);
  });

  it("pages through the server-backed provider patient directory", async () => {
    const patients = Array.from({ length: 101 }, (_, index) => ({
      patientAccountId: `patient-${index}`,
      name: `Care patient ${index}`,
    }));
    vi.mocked(providerApi.patients).mockImplementation(async (requestedPage = 0) => page(patients, requestedPage));

    render(<ProviderPatients />);
    expect(await screen.findByText("Care patient 0")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Next page" }));
    expect(await screen.findByText("Care patient 25")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Next page" }));
    expect(await screen.findByText("Care patient 50")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Next page" }));
    expect(await screen.findByText("Care patient 75")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Next page" }));
    expect(await screen.findByText("Care patient 100")).toBeInTheDocument();
    expect(vi.mocked(providerApi.patients).mock.calls.map(([requestedPage]) => requestedPage)).toEqual([0, 1, 2, 3, 4]);
  });

  it("shows loading, empty and recoverable error states for the provider directory", async () => {
    let finishRequest!: (result: Page<{ patientAccountId: string; name: string }>) => void;
    vi.mocked(providerApi.patients).mockReturnValueOnce(
      new Promise((resolve) => {
        finishRequest = resolve;
      }),
    );
    const { unmount } = render(<ProviderPatients />);
    expect(screen.getByRole("status")).toHaveTextContent("Loading patients");
    await act(async () => {
      finishRequest({ content: [], number: 0, size: 25, totalElements: 0, totalPages: 0 });
    });
    expect(await screen.findByText("No patients yet")).toBeInTheDocument();
    unmount();

    vi.mocked(providerApi.patients)
      .mockRejectedValueOnce(new Error("network failure"))
      .mockResolvedValueOnce({ content: [], number: 0, size: 25, totalElements: 0, totalPages: 0 });
    render(<ProviderPatients />);
    expect(await screen.findByText("Your patients could not be loaded.")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Try again" }));
    expect(await screen.findByText("No patients yet")).toBeInTheDocument();
    await waitFor(() => expect(providerApi.patients).toHaveBeenCalledTimes(3));
  });
});

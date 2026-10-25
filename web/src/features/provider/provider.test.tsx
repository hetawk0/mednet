import { describe, expect, it } from "vitest";
import type { Appointment } from "@/features/patient/patient-api";
import { applicationStatus } from "./application";
import { availableActions, groupProviderAppointments, needsResponse } from "./appointments";
import { todaysSchedule } from "./overview";
import { doctorGreeting } from "./provider-workspace";
import { liberiaInstant } from "./schedule";

const now = new Date("2026-10-11T12:00:00Z"); // noon in Monrovia (GMT)

function appt(id: string, status: string, startsAt: string, extra: Partial<Appointment> = {}): Appointment {
  return {
    id,
    patientAccountId: extra.patientAccountId ?? `pat-${id}`,
    status,
    patientName: extra.patientName ?? `Patient ${id}`,
    providerName: "Dr. Cole",
    consultationMode: "TEXT",
    startsAt,
    endsAt: new Date(new Date(startsAt).getTime() + 30 * 60_000).toISOString(),
    proposedStartsAt: null,
    proposedEndsAt: null,
    createdAt: startsAt,
    updatedAt: startsAt,
    ...extra,
  };
}

describe("provider appointment state", () => {
  it("only pending requests and patient-initiated reschedules need the provider's answer", () => {
    expect(needsResponse(appt("a", "PENDING", "2026-10-12T09:00:00Z"))).toBe(true);
    expect(needsResponse(appt("b", "RESCHEDULE_REQUESTED", "2026-10-12T09:00:00Z", { rescheduleRequestedBy: "PATIENT" }))).toBe(true);
    expect(needsResponse(appt("c", "RESCHEDULE_REQUESTED", "2026-10-12T09:00:00Z", { rescheduleRequestedBy: "PROVIDER" }))).toBe(false);
    expect(needsResponse(appt("d", "CONFIRMED", "2026-10-12T09:00:00Z"))).toBe(false);
  });

  it("offers exactly the transitions the backend allows", () => {
    const future = availableActions(appt("f", "CONFIRMED", "2026-10-12T09:00:00Z"), now);
    expect(future).toMatchObject({ accept: false, propose: true, cancel: true, complete: false, consultation: true });
    const ended = availableActions(appt("e", "CONFIRMED", "2026-10-11T09:00:00Z"), now);
    expect(ended).toMatchObject({ propose: false, cancel: false, complete: true, consultation: true });
    const pending = availableActions(appt("p", "PENDING", "2026-10-12T09:00:00Z"), now);
    expect(pending).toMatchObject({ accept: true, decline: true, propose: false, complete: false, consultation: false });
    const inPerson = availableActions(appt("i", "CONFIRMED", "2026-10-12T09:00:00Z", { consultationMode: "IN_PERSON" }), now);
    expect(inPerson.consultation).toBe(false);
    const waiting = availableActions(appt("w", "RESCHEDULE_REQUESTED", "2026-10-12T09:00:00Z", { rescheduleRequestedBy: "PROVIDER" }), now);
    expect(waiting).toMatchObject({ respondToReschedule: false, waitingForPatient: true, cancel: true });
    for (const closed of ["CANCELLED", "DECLINED", "COMPLETED"]) {
      expect(Object.values(availableActions(appt("x", closed, "2026-10-12T09:00:00Z"), now)).some(Boolean)).toBe(false);
    }
  });

  it("groups requests, upcoming and history", () => {
    const list = [
      appt("req", "PENDING", "2026-10-13T09:00:00Z"),
      appt("up", "CONFIRMED", "2026-10-12T09:00:00Z"),
      appt("mine", "RESCHEDULE_REQUESTED", "2026-10-14T09:00:00Z", { rescheduleRequestedBy: "PROVIDER" }),
      appt("ended", "CONFIRMED", "2026-10-10T09:00:00Z"),
      appt("done", "COMPLETED", "2026-10-09T09:00:00Z"),
      appt("declined", "DECLINED", "2026-10-15T09:00:00Z"),
    ];
    const { requests, upcoming, history } = groupProviderAppointments(list, now);
    expect(requests.map((a) => a.id)).toEqual(["req"]);
    expect(upcoming.map((a) => a.id)).toEqual(["up", "mine"]);
    expect(history.map((a) => a.id)).toEqual(["declined", "ended", "done"]);
  });

  it("builds today's schedule in Liberia time", () => {
    const list = [
      appt("morning", "COMPLETED", "2026-10-11T08:00:00Z"),
      appt("afternoon", "CONFIRMED", "2026-10-11T15:00:00Z"),
      appt("cancelled", "CANCELLED", "2026-10-11T16:00:00Z"),
      appt("tomorrow", "CONFIRMED", "2026-10-12T00:30:00Z"),
    ];
    const { today, active, remaining } = todaysSchedule(list, now);
    expect(today.map((a) => a.id)).toEqual(["morning", "afternoon", "cancelled"]);
    expect(active.map((a) => a.id)).toEqual(["morning", "afternoon"]);
    expect(remaining.map((a) => a.id)).toEqual(["afternoon"]);
  });
});

describe("provider formatting", () => {
  it("greets by surname with the Dr. title, without doubling it", () => {
    expect(doctorGreeting("Dr. Mariama Cole", new Date("2026-10-11T08:00:00Z"))).toBe("Good morning, Dr. Cole");
    expect(doctorGreeting("Mariama Cole", new Date("2026-10-11T13:00:00Z"))).toBe("Good afternoon, Dr. Cole");
    expect(doctorGreeting("", new Date("2026-10-11T20:00:00Z"))).toBe("Good evening");
  });

  it("converts Liberia wall-clock time to an instant (GMT, no daylight saving)", () => {
    expect(liberiaInstant("2026-12-01", "09:30")?.toISOString()).toBe("2026-12-01T09:30:00.000Z");
    expect(liberiaInstant("2026-06-01", "09:30")?.toISOString()).toBe("2026-06-01T09:30:00.000Z");
    expect(liberiaInstant("2026-13-01", "09:30")).toBeNull();
    expect(liberiaInstant("2026-12-01", "")).toBeNull();
  });

  it("never calls an approved provider verified", () => {
    expect(applicationStatus("APPROVED").label).toBe("Approved");
    expect(Object.values(["APPROVED", "PENDING", "REJECTED", "SUSPENDED"].map(applicationStatus)).map((s) => `${s.label} ${s.explanation}`).join(" ")).not.toMatch(/verified/i);
  });
});

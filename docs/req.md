# MedNet

## Product Requirements Document

> _A connected digital healthcare platform for Liberia_

| Field            | Detail                                                                                                                                 |
| ---------------- | -------------------------------------------------------------------------------------------------------------------------------------- |
| Product name     | MedNet                                                                                                                                 |
| Document type    | Product Requirements Document (PRD)                                                                                                    |
| Version          | Draft 1.1 — engineering review; pending client approval                                                                                |
| Date             | 1 October 2026                                                                                                                         |
| Prepared for     | MedNet Client / Product Sponsor                                                                                                        |
| Source artifacts | `docs/MedNet_PRD.docx` matches this draft; the cited original `MedNet.docx` was not found. Verify source attribution with the sponsor. |
| Status           | Working draft. Source confirmation and blocking product, clinical, platform and regulatory decisions remain open.                      |

## Executive Summary

MedNet is intended to connect patients, healthcare professionals, hospitals, laboratories and other healthcare service providers in Liberia. The client concept names twelve capabilities, from patient and provider accounts through appointments, messaging, virtual consultations, medical records, medication schedules, vitals, home healthcare, laboratory services, notifications and an administrator dashboard.

This document organizes the existing product draft into traceable requirements while separating statements attributed to the client source, derived behaviour and unresolved decisions. Its CONFIRMED labels are inherited from the earlier draft and have not been independently verified against the missing `MedNet.docx`; verify them with the sponsor before approval. This is not yet a build baseline: resolve the blocking questions in [Section 19](#19-open-questions-requiring-client-confirmation), especially scope, user roles, target platform, clinical workflows, payments, emergency handling and regulation.

**Provisional engineering direction:** Java 21 LTS, Spring Boot and PostgreSQL as a modular monolith. Next.js is the current web-client recommendation because the public site needs SEO as well as authenticated application flows, but the platform is not selected until the client answers OQ-50. See the [Production Architecture Guide](PRODUCTION_ARCHITECTURE_GUIDE.md); it is engineering guidance, not approved product scope.

## Contents

- [1. Document Purpose](#1-document-purpose)
- [2. Source Basis and How to Read This Document](#2-source-basis-and-how-to-read-this-document)
- [3. Product Overview](#3-product-overview)
- [4. Problem Statement](#4-problem-statement)
- [5. Product Objectives](#5-product-objectives)
- [6. Target Users and User Personas](#6-target-users-and-user-personas)
- [7. User Roles and Permissions](#7-user-roles-and-permissions)
- [8. Scope of the First Release](#8-scope-of-the-first-release)
- [9. Core Features and Functional Requirements](#9-core-features-and-functional-requirements)
  - Modules: M-01 Patient Registration and Login · M-02 Doctor and Provider Accounts · M-03 Appointment Booking · M-04 Patient–Doctor Messaging · M-05 Virtual Consultations · M-06 Medical Records · M-07 Drug Scheduling and Medication Records · M-08 Patient Vitals · M-09 Home Healthcare Requests · M-10 Laboratory Services · M-11 Notifications · M-12 Admin Dashboard
- [10. Key User Workflows](#10-key-user-workflows)
- [11. Business Rules](#11-business-rules)
- [12. Notifications and Communications](#12-notifications-and-communications)
- [13. Data and Information Requirements](#13-data-and-information-requirements)
- [14. Integrations and External Services](#14-integrations-and-external-services)
- [15. Non-Functional Requirements](#15-non-functional-requirements)
- [16. Security and Privacy Requirements](#16-security-and-privacy-requirements)
- [17. Reporting and Dashboard Requirements](#17-reporting-and-dashboard-requirements)
- [18. Assumptions and Dependencies](#18-assumptions-and-dependencies)
- [19. Open Questions Requiring Client Confirmation](#19-open-questions-requiring-client-confirmation)
- [20. Out of Scope](#20-out-of-scope)
- [21. Acceptance Criteria](#21-acceptance-criteria)
- [22. Future Enhancements](#22-future-enhancements)
- [23. Requirements Traceability](#23-requirements-traceability)
- [24. Review and Approval](#24-review-and-approval)

## 1. Document Purpose

This document provides a traceable product specification for client review and, after decisions and approval, a shared baseline for UX design, architecture, implementation, verification and acceptance. Sections 1–14 and 16–24 describe product requirements. The separate Technical Direction section is provisional engineering guidance, not client-approved scope.

It is intended to:

- Preserve the capabilities attributed to the client concept.
- Make functional requirements and acceptance criteria reviewable.
- Separate source-attributed statements from derived behaviour and open decisions.
- Prevent implementation commitments where clinical, commercial, platform or regulatory decisions remain unresolved.

## 2. Source Basis and How to Read This Document

The previous draft cites a client concept file named `MedNet.docx`, but that original file is not present in the workspace. The available `docs/MedNet_PRD.docx` is the formatted PRD and its extracted text matches this draft; it is not independent evidence of the original concept. The content below preserves the previous draft's source attributions. The sponsor must verify them against the original concept before approval.

### Evidence labels

| Label     | Meaning                                                                      | Review action                                                                   |
| --------- | ---------------------------------------------------------------------------- | ------------------------------------------------------------------------------- |
| CONFIRMED | Attributed by the previous draft to the client concept.                      | Verify against the original source and confirm intent.                          |
| DERIVED   | Minimum behaviour inferred as necessary to support an attributed capability. | Review and accept, amend or reject; it is not a client instruction.             |
| OPEN      | Information not established by the available source.                         | Obtain a client decision before estimating or building the affected capability. |

OPEN items are unresolved decisions, not implied requirements. Section 19 consolidates them. Until the original source is verified, CONFIRMED means "source-attributed by the prior draft," not independently confirmed here.

### Priority labels

- **Must:** proposed as necessary for the approved release; sponsor confirmation is still required.
- **Should:** proposed as important but potentially deferrable with sponsor approval.
- **Confirm:** priority depends on an unresolved decision.

A Must label does not imply that all twelve modules must launch together. Release scope and sequence remain open under OQ-01 and OQ-03.

## 3. Product Overview

CONFIRMED. MedNet is a digital healthcare platform that connects patients, healthcare professionals, hospitals, laboratories and other healthcare service providers within a single coordinated ecosystem.

In practical terms, MedNet gives a patient one place to find and reach a healthcare provider, book and attend an appointment, hold a consultation without travelling, keep their medical information in one continuous record, manage their medication, record their vital signs, request healthcare at home, and request laboratory services. It gives healthcare providers a single place to receive and manage those requests, and it gives an administrator oversight of the platform as a whole.

OPEN. The source document does not state whether MedNet is delivered as a mobile application, a web application, or both, nor which of these is the priority for the first release. This affects almost every other decision in the product and is the single most important item for the client to confirm.

## 4. Problem Statement

CONFIRMED. Access to timely, convenient and coordinated quality healthcare has long been a major challenge in Liberia and across Africa. The source document identifies the following specific difficulties:

| #   | Problem stated by the client                                           | What MedNet is expected to change                                              |
| --- | ---------------------------------------------------------------------- | ------------------------------------------------------------------------------ |
| P1  | Patients have difficulty identifying appropriate healthcare providers. | A searchable, verified directory of providers within the platform.             |
| P2  | Patients have difficulty accessing specialists.                        | Direct access to provider accounts and remote consultation.                    |
| P3  | Patients have difficulty scheduling appointments.                      | In-platform appointment booking.                                               |
| P4  | Patients have difficulty obtaining timely medical advice.              | Patient–doctor messaging and virtual consultations.                            |
| P5  | Patients cannot maintain continuity of their medical information.      | A persistent medical record that follows the patient.                          |
| P6  | Healthcare services are fragmented.                                    | A single ecosystem connecting patients, providers, hospitals and laboratories. |
| P7  | Home-based healthcare is difficult to access.                          | Home healthcare requests.                                                      |
| P8  | Laboratory services are difficult to access.                           | Laboratory services within the platform.                                       |
| P9  | Systems for supporting medication adherence are inadequate.            | Drug scheduling and medication records.                                        |
| P10 | Systems for supporting follow-up care are inadequate.                  | Appointments, messaging, records and notifications used together.              |

## 5. Product Objectives

CONFIRMED. The overall goal stated by the client is to help bridge the gap between patients and healthcare services in Liberia by using technology to make quality healthcare more accessible, connected and convenient.

This goal breaks down into the following product objectives:

| Ref    | Objective                                                                                                           | Addresses |
| ------ | ------------------------------------------------------------------------------------------------------------------- | --------- |
| OBJ-01 | Make it easier for patients to find and reach an appropriate healthcare provider, including specialists.            | P1, P2    |
| OBJ-02 | Remove distance and scheduling as barriers to receiving medical advice.                                             | P3, P4    |
| OBJ-03 | Give each patient a single continuous record of their medical information.                                          | P5        |
| OBJ-04 | Connect patients, providers, hospitals and laboratories in one coordinated ecosystem rather than separate services. | P6        |
| OBJ-05 | Extend healthcare and laboratory services into the patient's home.                                                  | P7, P8    |
| OBJ-06 | Support patients in taking their medication correctly and attending follow-up care.                                 | P9, P10   |

OPEN. The source document does not define measurable success targets (for example, number of registered patients, number of onboarded providers, or consultations completed per month). Measurable targets should be agreed with the client so that the success of the first release can be assessed objectively.

## 6. Target Users and User Personas

CONFIRMED. The source document identifies patients, healthcare professionals, hospitals, laboratories and "other healthcare service providers" as the participants in the MedNet ecosystem, and refers to an administrator function through the admin dashboard.

6.1 Primary users

#### U-01 — Patient

| Who they are   | An individual in Liberia seeking healthcare for themselves.                                                                                                                                                                                                                                                                                                           |
| -------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| What they need | To find a provider suited to their condition without relying on personal contacts. To get advice or a consultation without necessarily travelling to a facility. To keep their medical history in one place across different providers. To remember and keep to their medication schedule. To receive care and laboratory services at home where travel is difficult. |
| Basis          | CONFIRMED — patients are the central subject of the source document.                                                                                                                                                                                                                                                                                                  |

#### U-02 — Doctor / Healthcare Provider

| Who they are   | A licensed healthcare professional holding a provider account on MedNet.                                                                                                                                                                                                |
| -------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| What they need | To be discoverable by patients who need their expertise. To manage incoming appointment requests against their availability. To respond to patient messages and conduct virtual consultations. To view a patient's relevant medical information before and during care. |
| Basis          | CONFIRMED — "Doctor/provider accounts" is a named feature.                                                                                                                                                                                                              |

#### U-03 — Platform Administrator

| Who they are   | A member of the MedNet operating team responsible for the platform.                                                                                                 |
| -------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| What they need | To verify and approve providers before they can treat patients. To oversee activity across the platform. To manage accounts, service requests and platform content. |
| Basis          | CONFIRMED — "Admin dashboard" is a named feature. The specific administrator duties are DERIVED.                                                                    |

6.2 Organisational participants

The source document names hospitals, laboratories and "other healthcare service providers" as participants in the ecosystem. It also names laboratory services and home healthcare requests as features, which implies that laboratories and home healthcare workers must interact with the platform in some way.

OPEN. The source document does not state whether hospitals, laboratories and home healthcare workers have their own accounts and log in to MedNet directly, or whether their work is coordinated by the MedNet administrator on their behalf. This is a significant scope decision: it determines whether the first release contains three user-facing roles or six. It is recorded as OQ-02 and must be settled before design begins.

## 7. User Roles and Permissions

The table below sets out the working role model for MedNet. Roles marked "Confirm" depend on the answer to OQ-02.

| Role                   | Core permissions                                                                                                                                                                                                                                                         | Status  | Basis        |
| ---------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ------- | ------------ |
| Patient                | Register and manage own account; search providers; book appointments; message providers; join virtual consultations; view own medical records; manage own drug schedule; record own vitals; request home healthcare; request laboratory services; receive notifications. | Must    | Confirmed    |
| Doctor / Provider      | Hold a provider account; publish professional profile and availability; accept, reschedule or decline appointments; message patients; conduct virtual consultations; view and contribute to patient medical records; receive notifications.                              | Must    | Confirmed    |
| Administrator          | Access the admin dashboard; verify and manage provider accounts; manage patient accounts; oversee appointments, home healthcare requests and laboratory requests; view platform reporting.                                                                               | Must    | Confirmed    |
| Laboratory             | Receive and process laboratory requests; upload results to the patient record.                                                                                                                                                                                           | Confirm | Open — OQ-02 |
| Hospital / Facility    | Represent a facility and the providers attached to it.                                                                                                                                                                                                                   | Confirm | Open — OQ-02 |
| Home healthcare worker | Receive and fulfil assigned home healthcare visits.                                                                                                                                                                                                                      | Confirm | Open — OQ-02 |

OPEN. The source document does not describe permission levels within a role — for example, whether a provider may see a patient's full medical history or only records relating to their own episodes of care. This is both a clinical and a legal question and is recorded as OQ-07.

## 8. Scope of the First Release

The previous draft attributes the following twelve capabilities to the client concept. They are retained as candidate product scope; the available source does not establish that every capability must ship in one release. The client must confirm whether this list is complete and approve the release sequence (OQ-01, OQ-03).

| Module | Capability as stated by client       | Requirements     |
| ------ | ------------------------------------ | ---------------- |
| M-01   | Patients' registration / login       | FR-001 to FR-005 |
| M-02   | Doctor / provider accounts           | FR-006 to FR-011 |
| M-03   | Appointment booking                  | FR-012 to FR-018 |
| M-04   | Patient–doctor messaging             | FR-019 to FR-023 |
| M-05   | Patient–doctor virtual consultations | FR-024 to FR-029 |
| M-06   | Medical records                      | FR-030 to FR-035 |
| M-07   | Drugs scheduling / records           | FR-036 to FR-041 |
| M-08   | Patients' vitals                     | FR-042 to FR-046 |
| M-09   | Home healthcare requests             | FR-047 to FR-052 |
| M-10   | Laboratory services                  | FR-053 to FR-059 |
| M-11   | Notifications                        | FR-060 to FR-064 |
| M-12   | Admin dashboard                      | FR-065 to FR-072 |

### Proposed Delivery Waves (For Estimation; Not Approved Scope)

| Wave                              | Candidate capabilities                                                                                                                               | Gate                                                             |
| --------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------- |
| 0. Discovery and safety decisions | Confirm platform, user roles, scope, care boundaries, privacy and applicable regulation.                                                             | Resolve blocking OQs before final estimation or clinical launch. |
| 1. Platform foundation            | Patient/provider accounts, provider approval, provider directory, baseline medical record, admin controls, access audit and notification foundation. | Confirm OQ-01–OQ-10, OQ-24 and OQ-46/OQ-54 as applicable.        |
| 2. Core care coordination         | Appointments, messaging, medication schedules, vitals and record continuity.                                                                         | Agree appointment, messaging, medication and vitals workflows.   |
| 3. Service channels               | Virtual consultations, home healthcare and laboratory workflows with selected external services.                                                     | Agree OQ-15, OQ-20–OQ-23, OQ-35–OQ-44 and provider contracts.    |

These waves sequence discovery and implementation; they do not remove source-attributed capabilities. The sponsor must approve actual release scope and order. Do not commit dates until blocking decisions and external service dependencies are resolved.

## 9. Core Features and Functional Requirements

Each module below states its purpose, the roles involved, the numbered requirements, the business rules that apply, its dependencies, and the acceptance criteria by which it will be judged complete. Where the source document is silent, the requirement is marked DERIVED or the point is raised as an open question rather than being invented.

### 9.1 Module M-01 — Patient Registration and Login

#### Overview

| Purpose          | To give every patient a secure personal account, which is the entry point to all other MedNet services and the anchor for their medical information. |
| ---------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------- |
| Primary role     | Patient                                                                                                                                              |
| Supporting roles | Administrator                                                                                                                                        |
| Basis            | CONFIRMED — "Patients' registration/login" is a named feature.                                                                                       |

| Ref    | Requirement                                                                                                                                                     | Priority | Basis     |
| ------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------- | --------- |
| FR-001 | A patient must be able to create a MedNet account by registering with their personal details.                                                                   | Must     | Confirmed |
| FR-002 | A returning patient must be able to log in securely to their existing account.                                                                                  | Must     | Confirmed |
| FR-003 | The system must prevent the same person from being registered twice using the same identifying credential (for example the same phone number or email address). | Must     | Derived   |
| FR-004 | A patient must be able to recover access to their account if they forget their password or sign-in credential.                                                  | Must     | Derived   |
| FR-005 | A patient must be able to view and update their own profile information after registration.                                                                     | Must     | Derived   |

#### Rules, Dependencies, and Acceptance

| Business rules      | A patient must hold an account before they can book an appointment, message a provider, request home healthcare or request laboratory services. Each patient account corresponds to exactly one individual and one medical record.                                                                                                                                                                                          |
| ------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Dependencies        | None. This module is a prerequisite for M-03 to M-11.                                                                                                                                                                                                                                                                                                                                                                       |
| Acceptance criteria | A new patient can complete registration and reach their account area without assistance. A registered patient can log out and log back in successfully. An attempt to register a second account with an already-registered credential is rejected with a clear message. A patient who has forgotten their credential can regain access without contacting an administrator.                                                 |
| Open questions      | What identifying details are captured at registration (OQ-04). Whether registration uses a phone number or an email address, given that phone numbers are more widely held than email addresses in Liberia (OQ-05). Whether a patient's identity or contact detail must be verified before the account becomes active (OQ-06). Whether one account may manage dependants, such as a parent managing a child's care (OQ-08). |

### 9.2 Module M-02 — Doctor and Provider Accounts

#### Overview

| Purpose          | To bring healthcare professionals onto the platform with a professional identity that patients can find and trust, and to give providers the means to manage the care they deliver through MedNet. |
| ---------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Primary role     | Doctor / Provider                                                                                                                                                                                  |
| Supporting roles | Administrator, Patient                                                                                                                                                                             |
| Basis            | CONFIRMED — "Doctor/provider accounts" is a named feature. Provider verification is DERIVED from the client's stated goal of quality healthcare and from the presence of an admin dashboard.       |

| Ref    | Requirement                                                                                                                        | Priority | Basis     |
| ------ | ---------------------------------------------------------------------------------------------------------------------------------- | -------- | --------- |
| FR-006 | A healthcare provider must be able to apply for a provider account on MedNet.                                                      | Must     | Confirmed |
| FR-007 | An administrator must be able to review and approve or reject a provider account before that provider becomes visible to patients. | Must     | Derived   |
| FR-008 | A provider must be able to maintain a professional profile describing their specialty and the services they offer.                 | Must     | Derived   |
| FR-009 | A patient must be able to search or browse the directory of approved providers.                                                    | Must     | Derived   |
| FR-010 | A provider must be able to log in securely and manage their own account.                                                           | Must     | Derived   |
| FR-011 | A provider must be able to indicate the times at which they are available to receive appointments.                                 | Must     | Derived   |

#### Rules, Dependencies, and Acceptance

| Business rules      | Only an approved provider may appear in the patient-facing directory and receive appointment requests. A provider account that is suspended or rejected by an administrator must not be visible to patients.                                                                                                                                                                 |
| ------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Dependencies        | Administrator approval capability in M-12.                                                                                                                                                                                                                                                                                                                                   |
| Acceptance criteria | A provider can submit an application and is told clearly that it is pending review. An unapproved provider does not appear in patient search results. Once approved, the provider appears in the directory and can be selected for an appointment. A patient searching for a specialty sees the approved providers offering it.                                              |
| Open questions      | What professional credentials must be supplied and how they are verified — for example against the Liberia Medical and Dental Council register (OQ-09). Whether providers are onboarded individually or through their hospital or facility (OQ-10). What information a provider profile displays to patients (OQ-11). Whether patients can rate or review providers (OQ-12). |

### 9.3 Module M-03 — Appointment Booking

#### Overview

| Purpose          | To let a patient secure a specific time with a specific provider without visiting a facility or relying on informal arrangements, directly addressing problems P3 and P10. |
| ---------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Primary role     | Patient                                                                                                                                                                    |
| Supporting roles | Doctor / Provider, Administrator                                                                                                                                           |
| Basis            | CONFIRMED — "Appointment booking" is a named feature.                                                                                                                      |

| Ref    | Requirement                                                                                           | Priority | Basis     |
| ------ | ----------------------------------------------------------------------------------------------------- | -------- | --------- |
| FR-012 | A patient must be able to select an approved provider and request an appointment.                     | Must     | Confirmed |
| FR-013 | The system must show the patient the times at which the selected provider is available.               | Must     | Derived   |
| FR-014 | The system must confirm to the patient that the appointment has been booked, and notify the provider. | Must     | Derived   |
| FR-015 | A patient must be able to view their upcoming and past appointments.                                  | Must     | Derived   |
| FR-016 | A patient must be able to cancel or request to reschedule an appointment.                             | Must     | Derived   |
| FR-017 | A provider must be able to view, accept, reschedule or decline appointment requests.                  | Must     | Derived   |
| FR-018 | The system must prevent the same appointment slot from being booked by two patients.                  | Must     | Derived   |

#### Rules, Dependencies, and Acceptance

| Business rules      | Appointments may only be booked with approved providers and within the availability that provider has published. Both patient and provider must be notified whenever an appointment is created, changed or cancelled.                                                                                                                      |
| ------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Dependencies        | M-01 (patient account), M-02 (approved provider and published availability), M-11 (notifications).                                                                                                                                                                                                                                         |
| Acceptance criteria | A patient can complete a booking from provider selection to confirmation in a single session. The provider receives the request and the patient is told the outcome. An appointment slot already taken is no longer offered to other patients. A cancelled appointment disappears from both parties' upcoming lists and both are notified. |
| Open questions      | Whether an appointment is confirmed automatically or only once the provider accepts it (OQ-13). Whether appointments distinguish between in-person and virtual attendance (OQ-14). Whether appointments are paid for, and if so at what point (OQ-15). Whether cancellation windows or no-show rules apply (OQ-16).                        |

### 9.4 Module M-04 — Patient–Doctor Messaging

#### Overview

| Purpose          | To give patients a direct channel for obtaining timely medical advice and for following up after care, addressing problems P4 and P10. |
| ---------------- | -------------------------------------------------------------------------------------------------------------------------------------- |
| Primary role     | Patient and Doctor / Provider                                                                                                          |
| Supporting roles | Administrator                                                                                                                          |
| Basis            | CONFIRMED — "Patient-doctor messaging" is a named feature.                                                                             |

| Ref    | Requirement                                                                             | Priority | Basis     |
| ------ | --------------------------------------------------------------------------------------- | -------- | --------- |
| FR-019 | A patient must be able to send a written message to a provider through MedNet.          | Must     | Confirmed |
| FR-020 | A provider must be able to read and reply to messages from patients.                    | Must     | Confirmed |
| FR-021 | Both parties must be able to view the history of their conversation.                    | Must     | Derived   |
| FR-022 | Both parties must be notified when a new message is received.                           | Must     | Derived   |
| FR-023 | Messages must only be visible to the patient and provider involved in the conversation. | Must     | Derived   |

#### Rules, Dependencies, and Acceptance

| Business rules      | Messaging is a private channel between one patient and one provider. A conversation forms part of the patient's clinical communication history and must not be silently deleted.                                                                                                                                                                                                  |
| ------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Dependencies        | M-01, M-02, M-11.                                                                                                                                                                                                                                                                                                                                                                 |
| Acceptance criteria | A message sent by a patient is received by the intended provider and by no one else. The recipient is notified of the new message. Reopening a conversation shows the full previous history in order.                                                                                                                                                                             |
| Open questions      | Whether a patient may message any approved provider or only one with whom they have an appointment or existing care relationship (OQ-17). Whether messages may include attachments such as photographs of a symptom (OQ-18). Whether a response time is expected of providers, and how urgent or emergency messages are handled (OQ-19). Whether messaging is chargeable (OQ-15). |

### 9.5 Module M-05 — Virtual Consultations

#### Overview

| Purpose          | To allow a patient to consult a doctor remotely, removing distance and travel as barriers to care and improving access to specialists (P2, P4). |
| ---------------- | ----------------------------------------------------------------------------------------------------------------------------------------------- |
| Primary role     | Patient and Doctor / Provider                                                                                                                   |
| Supporting roles | Administrator                                                                                                                                   |
| Basis            | CONFIRMED — "Patient-doctor virtual consultations" is a named feature.                                                                          |

| Ref    | Requirement                                                                                        | Priority | Basis     |
| ------ | -------------------------------------------------------------------------------------------------- | -------- | --------- |
| FR-024 | A patient and a provider must be able to hold a virtual consultation through MedNet.               | Must     | Confirmed |
| FR-025 | A virtual consultation must be tied to a scheduled appointment between the two parties.            | Must     | Derived   |
| FR-026 | Both parties must be able to join the consultation at the scheduled time from within MedNet.       | Must     | Derived   |
| FR-027 | Both parties must be reminded that a consultation is due to begin.                                 | Should   | Derived   |
| FR-028 | A provider must be able to record the outcome of the consultation to the patient's medical record. | Must     | Derived   |
| FR-029 | A consultation must only be accessible to the patient and provider concerned.                      | Must     | Derived   |

#### Rules, Dependencies, and Acceptance

| Business rules      | Only the patient and the provider named on the appointment may join that consultation. Every completed consultation should leave a record in the patient's medical history.                                                                                                                                                                                                                                                                                                   |
| ------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Dependencies        | M-03 (appointment), M-06 (medical records), M-11 (reminders), and a real-time communication service (see Section 14).                                                                                                                                                                                                                                                                                                                                                         |
| Acceptance criteria | Both parties can join a scheduled consultation and communicate with each other. A person who is not party to the consultation cannot join it. After the consultation, an entry is available in the patient's record.                                                                                                                                                                                                                                                          |
| Open questions      | Whether consultations are by video, voice, or text — a material decision given bandwidth and data cost in Liberia (OQ-20). Whether consultations are recorded, and if so who may access the recording (OQ-21). What should happen when the connection fails mid-consultation (OQ-22). Whether consultations are chargeable (OQ-15). Whether a doctor may issue a prescription during a virtual consultation, and whether that is permitted under Liberian regulation (OQ-23). |

### 9.6 Module M-06 — Medical Records

#### Overview

| Purpose          | To give each patient a single continuous record of their medical information that persists across providers and over time, addressing problems P5 and P6. |
| ---------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Primary role     | Patient and Doctor / Provider                                                                                                                             |
| Supporting roles | Administrator, Laboratory                                                                                                                                 |
| Basis            | CONFIRMED — "Medical records" is a named feature. This module is central to the client's stated concern about continuity of medical information.          |

| Ref    | Requirement                                                                                                                                                   | Priority | Basis     |
| ------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------- | --------- |
| FR-030 | The system must maintain a medical record for each registered patient.                                                                                        | Must     | Confirmed |
| FR-031 | A patient must be able to view their own medical record.                                                                                                      | Must     | Derived   |
| FR-032 | An authorised provider must be able to view the medical record of a patient in their care.                                                                    | Must     | Derived   |
| FR-033 | An authorised provider must be able to add clinical entries to a patient's record.                                                                            | Must     | Derived   |
| FR-034 | The record must bring together information generated elsewhere in MedNet, including consultation outcomes, medication records, vitals and laboratory results. | Must     | Derived   |
| FR-035 | Every entry in the record must show when it was made and by whom.                                                                                             | Must     | Derived   |

#### Rules, Dependencies, and Acceptance

| Business rules      | A patient's medical record belongs to the patient and follows the patient, not the provider. A clinical entry, once recorded, must not be deleted; corrections must be added as new entries so that history is preserved. Access to a medical record must be restricted to the patient and to providers authorised to see it.                                                                                              |
| ------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Dependencies        | M-01, and receives information from M-05, M-07, M-08 and M-10.                                                                                                                                                                                                                                                                                                                                                             |
| Acceptance criteria | A patient can open their record and see entries from more than one provider in one place. A laboratory result, a recorded vital and a consultation outcome all appear in the same record. A provider who is not authorised cannot open the record. Each entry shows its author and date.                                                                                                                                   |
| Open questions      | What information the record contains — diagnoses, prescriptions, allergies, history, documents (OQ-24). Whether a patient may upload their own historical records or documents (OQ-25). Whether records held by existing hospitals or clinics need to be imported (OQ-26). Which providers may see which parts of the record, and whether the patient controls that access (OQ-07). How long records are retained (OQ-27). |

### 9.7 Module M-07 — Drug Scheduling and Medication Records

#### Overview

| Purpose          | To support patients in taking their medication correctly and on time, directly addressing the client's concern about inadequate support for medication adherence (P9). |
| ---------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Primary role     | Patient                                                                                                                                                                |
| Supporting roles | Doctor / Provider                                                                                                                                                      |
| Basis            | CONFIRMED — "Drugs scheduling/records" is a named feature, and medication adherence is named in the problem statement.                                                 |

| Ref    | Requirement                                                                                 | Priority | Basis     |
| ------ | ------------------------------------------------------------------------------------------- | -------- | --------- |
| FR-036 | A patient must be able to record the medication they are taking, including dose and timing. | Must     | Confirmed |
| FR-037 | The system must remind the patient when a dose is due.                                      | Must     | Derived   |
| FR-038 | A patient must be able to view their current and past medications.                          | Must     | Confirmed |
| FR-039 | A patient must be able to record that a dose was taken.                                     | Should   | Derived   |
| FR-040 | A patient must be able to edit or stop a medication schedule.                               | Must     | Derived   |
| FR-041 | Medication information must be available within the patient's medical record.               | Must     | Derived   |

#### Rules, Dependencies, and Acceptance

| Business rules      | Medication reminders continue for the duration of the schedule until the patient or provider ends it. MedNet supports adherence; it does not replace the prescribing clinician's instructions.                                                                                                                                                                                                                                                       |
| ------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Dependencies        | M-01, M-06, M-11.                                                                                                                                                                                                                                                                                                                                                                                                                                    |
| Acceptance criteria | A patient can create a medication schedule and receives a reminder at the scheduled time. The medication appears in the patient's medication list and in their medical record. Ending a schedule stops further reminders.                                                                                                                                                                                                                            |
| Open questions      | Whether a provider can create or prescribe a medication schedule directly for a patient, or whether the patient enters it themselves (OQ-28). Whether MedNet holds a drug catalogue or the patient types the medication name freely (OQ-29). Whether the platform should warn of drug interactions — noting that this carries clinical risk and requires a licensed data source (OQ-30). Whether adherence is reported back to the provider (OQ-31). |

### 9.8 Module M-08 — Patient Vitals

#### Overview

| Purpose          | To allow patients to record and track their vital signs over time and to make that information available to the clinicians caring for them, supporting follow-up care (P10). |
| ---------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Primary role     | Patient                                                                                                                                                                      |
| Supporting roles | Doctor / Provider, Home healthcare worker                                                                                                                                    |
| Basis            | CONFIRMED — "Patients' Vitals" is a named feature.                                                                                                                           |

| Ref    | Requirement                                                              | Priority | Basis     |
| ------ | ------------------------------------------------------------------------ | -------- | --------- |
| FR-042 | A patient must be able to record their vital signs in MedNet.            | Must     | Confirmed |
| FR-043 | Each vitals entry must be stored with the date and time it was recorded. | Must     | Derived   |
| FR-044 | A patient must be able to view their vitals history over time.           | Must     | Derived   |
| FR-045 | An authorised provider must be able to view a patient's recorded vitals. | Must     | Derived   |
| FR-046 | Recorded vitals must form part of the patient's medical record.          | Must     | Derived   |

#### Rules, Dependencies, and Acceptance

| Business rules      | Vitals recorded by a patient are self-reported and should be distinguishable from readings taken by a clinician.                                                                                                                                                                                                                                  |
| ------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Dependencies        | M-01, M-06.                                                                                                                                                                                                                                                                                                                                       |
| Acceptance criteria | A patient can record a reading and see it in their history with the correct date and time. An authorised provider viewing the patient sees the same readings.                                                                                                                                                                                     |
| Open questions      | Which vital signs are supported — for example blood pressure, blood glucose, temperature, weight, pulse (OQ-32). Whether readings are entered manually only, or whether connected devices are in scope (OQ-33). Whether the system should flag readings outside a safe range, and if so who is alerted — a clinically sensitive decision (OQ-34). |

### 9.9 Module M-09 — Home Healthcare Requests

#### Overview

| Purpose          | To let patients request healthcare services at their home, addressing the client's concern about limited access to home-based healthcare (P7). |
| ---------------- | ---------------------------------------------------------------------------------------------------------------------------------------------- |
| Primary role     | Patient                                                                                                                                        |
| Supporting roles | Administrator, Home healthcare worker / Provider                                                                                               |
| Basis            | CONFIRMED — "Home healthcare requests" is a named feature.                                                                                     |

| Ref    | Requirement                                                                         | Priority | Basis     |
| ------ | ----------------------------------------------------------------------------------- | -------- | --------- |
| FR-047 | A patient must be able to submit a request for healthcare at their home.            | Must     | Confirmed |
| FR-048 | The request must capture the service needed and the location at which it is needed. | Must     | Derived   |
| FR-049 | The system must acknowledge the request to the patient and route it for action.     | Must     | Derived   |
| FR-050 | A patient must be able to see the status of their request.                          | Must     | Derived   |
| FR-051 | The party responsible must be able to accept, schedule and complete the request.    | Must     | Derived   |
| FR-052 | The patient must be notified when the request is accepted, scheduled or completed.  | Must     | Derived   |

#### Rules, Dependencies, and Acceptance

| Business rules      | A home healthcare request must always have a clear current status visible to the patient. A request must not be left unassigned without the patient being informed.                                                                                                                                                                                                                                                                            |
| ------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Dependencies        | M-01, M-11, M-12 (if requests are routed by an administrator).                                                                                                                                                                                                                                                                                                                                                                                 |
| Acceptance criteria | A patient can submit a request and receives an acknowledgement. The request appears to the responsible party for action. The patient can see the status change as the request progresses and is notified at each change.                                                                                                                                                                                                                       |
| Open questions      | Which services can be requested at home — nursing, doctor visit, sample collection, physiotherapy (OQ-35). Who receives and fulfils these requests: MedNet's own staff, partner organisations, or individual providers (OQ-36). How the patient's location is captured, given that formal street addressing is limited in parts of Liberia (OQ-37). Which geographic areas are served (OQ-38). How the service is priced and paid for (OQ-15). |

### 9.10 Module M-10 — Laboratory Services

#### Overview

| Purpose          | To connect patients to laboratory testing and return the results into their medical record, addressing limited access to laboratory services (P8) and fragmentation (P6). |
| ---------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Primary role     | Patient                                                                                                                                                                   |
| Supporting roles | Laboratory, Doctor / Provider, Administrator                                                                                                                              |
| Basis            | CONFIRMED — "Laboratory services" is a named feature, and laboratories are named as ecosystem participants.                                                               |

| Ref    | Requirement                                                                         | Priority | Basis     |
| ------ | ----------------------------------------------------------------------------------- | -------- | --------- |
| FR-053 | A patient must be able to request a laboratory service through MedNet.              | Must     | Confirmed |
| FR-054 | The request must be routed to a laboratory for action.                              | Must     | Derived   |
| FR-055 | A patient must be able to see the status of their laboratory request.               | Must     | Derived   |
| FR-056 | A laboratory result must be returned to the patient through MedNet.                 | Must     | Derived   |
| FR-057 | Laboratory results must be stored in the patient's medical record.                  | Must     | Derived   |
| FR-058 | The patient must be notified when a result is available.                            | Must     | Derived   |
| FR-059 | An authorised provider must be able to view the results of a patient in their care. | Must     | Derived   |

#### Rules, Dependencies, and Acceptance

| Business rules      | A laboratory result must be attached to the correct patient record and must not be visible to any other patient. Results form a permanent part of the medical record.                                                                                                                                                                                                                                                                                                |
| ------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Dependencies        | M-01, M-06, M-11, and laboratory participation (OQ-02).                                                                                                                                                                                                                                                                                                                                                                                                              |
| Acceptance criteria | A patient can raise a laboratory request and track its status. A result is delivered to the correct patient and appears in their medical record. The patient is notified when the result becomes available.                                                                                                                                                                                                                                                          |
| Open questions      | Whether a laboratory request requires a doctor's order or may be initiated by the patient alone (OQ-39). Whether samples are collected at a laboratory or at the patient's home, which would connect this module to M-09 (OQ-40). How results are entered — uploaded by the laboratory, or entered by an administrator (OQ-41). Whether a result should be reviewed by a clinician before the patient sees it (OQ-42). How laboratory services are paid for (OQ-15). |

### 9.11 Module M-11 — Notifications

#### Overview

| Purpose          | To keep patients and providers informed of everything that requires their attention, and to support medication adherence and follow-up care. |
| ---------------- | -------------------------------------------------------------------------------------------------------------------------------------------- |
| Primary role     | All roles                                                                                                                                    |
| Supporting roles | Administrator                                                                                                                                |
| Basis            | CONFIRMED — "Notifications" is a named feature.                                                                                              |

| Ref    | Requirement                                                                                                                                                                | Priority | Basis     |
| ------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------- | --------- |
| FR-060 | The system must notify users of events relevant to them.                                                                                                                   | Must     | Confirmed |
| FR-061 | Notifications must be generated for appointment confirmations, changes, cancellations and reminders.                                                                       | Must     | Derived   |
| FR-062 | Notifications must be generated for new messages, medication reminders, home healthcare status changes and available laboratory results.                                   | Must     | Derived   |
| FR-063 | A user must be able to view their notifications within MedNet.                                                                                                             | Must     | Derived   |
| FR-064 | A notification must never contain sensitive clinical detail outside the platform; it should tell the user that something awaits them and require them to log in to see it. | Should   | Derived   |

The full notification catalogue is set out in Section 12.

#### Rules, Dependencies, and Acceptance

| Business rules      | Every event that changes the state of an appointment, request or result must notify the affected user. Notifications must be addressed only to the user concerned.                                                                                     |
| ------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Dependencies        | All other modules generate notification events.                                                                                                                                                                                                        |
| Acceptance criteria | Each event listed in Section 12 produces a notification to the correct recipient. A user can review notifications they have already received.                                                                                                          |
| Open questions      | Which channels are used — in-app, SMS, email, push (OQ-43). Whether SMS is required for patients without smartphones or reliable data, which has a direct cost implication (OQ-44). Whether users can choose which notifications they receive (OQ-45). |

### 9.12 Module M-12 — Admin Dashboard

#### Overview

| Purpose          | To give the MedNet operating team oversight and control of the platform: who is on it, what is happening, and what needs attention. |
| ---------------- | ----------------------------------------------------------------------------------------------------------------------------------- |
| Primary role     | Administrator                                                                                                                       |
| Supporting roles | All                                                                                                                                 |
| Basis            | CONFIRMED — "Admin dashboard" is a named feature. The specific administrator functions below are DERIVED from the other modules.    |

| Ref    | Requirement                                                                                  | Priority | Basis     |
| ------ | -------------------------------------------------------------------------------------------- | -------- | --------- |
| FR-065 | An administrator must be able to log in to a dedicated admin area.                           | Must     | Confirmed |
| FR-066 | An administrator must be able to review, approve, reject and suspend provider accounts.      | Must     | Derived   |
| FR-067 | An administrator must be able to view and manage patient accounts.                           | Must     | Derived   |
| FR-068 | An administrator must be able to view appointments across the platform.                      | Must     | Derived   |
| FR-069 | An administrator must be able to view and manage home healthcare requests.                   | Must     | Derived   |
| FR-070 | An administrator must be able to view and manage laboratory requests.                        | Must     | Derived   |
| FR-071 | The dashboard must present summary figures of platform activity.                             | Must     | Derived   |
| FR-072 | Administrative actions on accounts and requests must be recorded so that they can be traced. | Must     | Derived   |

#### Rules, Dependencies, and Acceptance

| Business rules      | Administrative access must be restricted to authorised staff. An administrator's access to clinical information should be limited to what their duties require (see OQ-46).                                                                                                                                            |
| ------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Dependencies        | All modules.                                                                                                                                                                                                                                                                                                           |
| Acceptance criteria | An administrator can approve a pending provider, after which that provider becomes visible to patients. An administrator can locate a home healthcare or laboratory request and act on it. The dashboard displays current activity figures. Administrative actions are traceable to the individual who performed them. |
| Open questions      | Whether there is more than one level of administrator, for example a super-administrator and support staff (OQ-47). Whether administrators may view patient clinical information (OQ-46). Which figures the dashboard should display (OQ-48).                                                                          |

## 10. Key User Workflows

The workflows below describe the main journeys through MedNet, using only steps that follow from the capabilities named in the source document. Steps that depend on an unresolved decision are marked.

### Workflow 1 — Patient registration

- The patient opens MedNet and chooses to register.
- The system requests the patient's registration details.
- The patient provides their details.
- The system validates the details and checks that the patient is not already registered.
- The system creates the patient account and its associated medical record.
- The patient is signed in and can access MedNet's services.
  Depends on: OQ-04, OQ-05, OQ-06 — the exact details captured and whether verification is required.

### Workflow 2 — Booking an appointment

- The patient searches or browses the directory of approved providers.
- The system displays matching providers and their profiles.
- The patient selects a provider.
- The system displays the provider's available appointment times.
- The patient selects a time and submits the booking request.
- The system records the request and notifies the provider.
- The provider accepts, reschedules or declines the request.
- The system notifies the patient of the outcome and adds the appointment to both parties' schedules.
- The system reminds both parties before the appointment.
  Depends on: OQ-13 — whether step 7 is required or bookings confirm automatically.

### Workflow 3 — Virtual consultation

- A confirmed appointment exists between the patient and the provider.
- The system reminds both parties that the consultation is due.
- Both parties join the consultation from within MedNet at the scheduled time.
- The consultation takes place.
- The provider records the outcome of the consultation.
- The system saves the outcome to the patient's medical record.
- The patient can view the outcome in their record.
  Depends on: OQ-20, OQ-22, OQ-23 — consultation format, connection failure handling, and prescribing.

### Workflow 4 — Messaging a provider

- The patient opens a conversation with a provider.
- The patient writes and sends a message.
- The system delivers the message and notifies the provider.
- The provider reads the message and replies.
- The system notifies the patient of the reply.
- Both parties can revisit the full conversation at any time.
  Depends on: OQ-17, OQ-19 — who may be messaged and what response expectations apply.

### Workflow 5 — Managing a medication schedule

- The patient opens the medication area and adds a medication with its dose and timing.
- The system saves the schedule and adds it to the patient's medication record.
- The system reminds the patient at each scheduled time.
- The patient confirms that the dose was taken.
- The system records the confirmation.
- The medication and its history remain visible in the patient's medical record.
  Depends on: OQ-28 — whether a provider can create the schedule instead of the patient.

### Workflow 6 — Recording vitals

- The patient opens the vitals area and enters a reading.
- The system saves the reading with its date and time.
- The system displays the reading within the patient's vitals history.
- The reading becomes available in the patient's medical record.
- An authorised provider caring for the patient can view the reading.
  Depends on: OQ-32, OQ-34 — which vitals are supported and whether out-of-range readings raise an alert.

### Workflow 7 — Requesting home healthcare

- The patient opens the home healthcare area and selects the service required.
- The patient provides the location and any relevant detail.
- The system records the request and acknowledges it to the patient.
- The system routes the request to the responsible party.
- The responsible party accepts the request and schedules the visit.
- The system notifies the patient of the accepted request and the scheduled time.
- The visit takes place and is marked as completed.
- The system notifies the patient and updates the request status.
  Depends on: OQ-35, OQ-36, OQ-37 — services offered, who fulfils them, and how location is captured.

### Workflow 8 — Requesting a laboratory service

- The patient opens the laboratory area and selects the service required.
- The system records the request and routes it to a laboratory.
- The patient can view the status of the request.
- The sample is collected and the test is performed.
- The laboratory returns the result through MedNet.
- The system stores the result in the patient's medical record and notifies the patient.
- The patient, and any authorised provider, can view the result.
  Depends on: OQ-39, OQ-40, OQ-41, OQ-42 — whether a doctor's order is needed, where samples are collected, how results are entered, and whether clinical review precedes release.

### Workflow 9 — Provider onboarding and approval

- The provider applies for a MedNet provider account and supplies their professional details.
- The system records the application as pending and notifies the administrator.
- The administrator reviews the application.
- The administrator approves or rejects the application.
- The system notifies the provider of the decision.
- On approval, the provider completes their profile and publishes their availability.
- The provider becomes visible to patients in the directory and can receive appointments.
  Depends on: OQ-09, OQ-10 — what credentials are checked and whether onboarding is individual or through a facility.

## 11. Business Rules

The following rules apply across MedNet. Each is either stated in, or necessarily implied by, the source document.

| Ref   | Rule                                                                                                                                                              | Basis   |
| ----- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------- |
| BR-01 | A person must hold a MedNet account before they can use any service on the platform.                                                                              | Derived |
| BR-02 | Each patient has exactly one account and one medical record.                                                                                                      | Derived |
| BR-03 | Only providers approved by an administrator are visible to patients and may receive appointments, messages or consultations.                                      | Derived |
| BR-04 | A patient's medical information is visible only to that patient and to users authorised to see it.                                                                | Derived |
| BR-05 | Clinical entries are permanent; corrections are recorded as new entries rather than by overwriting.                                                               | Derived |
| BR-06 | Every appointment, home healthcare request and laboratory request has a current status that is visible to the patient at all times.                               | Derived |
| BR-07 | Any change to the status of an appointment, request or result notifies the affected users.                                                                        | Derived |
| BR-08 | A virtual consultation may only be joined by the patient and the provider named on the associated appointment.                                                    | Derived |
| BR-09 | Information generated anywhere in MedNet about a patient — consultations, medication, vitals, laboratory results — is reflected in that patient's medical record. | Derived |
| BR-10 | MedNet supports the relationship between a patient and a clinician; it does not replace clinical judgement or provide medical advice of its own.                  | Derived |

OPEN. The source document contains no rules on payment, cancellation, emergency handling, or what MedNet should do when a patient reports a life-threatening situation. Emergency handling in particular should be addressed explicitly before launch (OQ-49).

## 12. Notifications and Communications

The following notification catalogue is derived from the events described in Section 9. Channels are shown as "To confirm" because the source document does not specify them (OQ-43).

| Ref   | Event                                                    | Recipient                        | Priority | Channel    |
| ----- | -------------------------------------------------------- | -------------------------------- | -------- | ---------- |
| NT-01 | Account successfully created                             | Patient / Provider               | Should   | To confirm |
| NT-02 | Provider application approved or rejected                | Provider                         | Must     | To confirm |
| NT-03 | New appointment request received                         | Provider                         | Must     | To confirm |
| NT-04 | Appointment confirmed, rescheduled or declined           | Patient                          | Must     | To confirm |
| NT-05 | Appointment cancelled                                    | Both parties                     | Must     | To confirm |
| NT-06 | Appointment reminder                                     | Both parties                     | Must     | To confirm |
| NT-07 | Virtual consultation about to begin                      | Both parties                     | Should   | To confirm |
| NT-08 | New message received                                     | Recipient                        | Must     | To confirm |
| NT-09 | Medication dose due                                      | Patient                          | Must     | To confirm |
| NT-10 | Home healthcare request received                         | Patient (acknowledgement)        | Must     | To confirm |
| NT-11 | Home healthcare request accepted, scheduled or completed | Patient                          | Must     | To confirm |
| NT-12 | New home healthcare request to action                    | Administrator / fulfilling party | Must     | To confirm |
| NT-13 | Laboratory request received and routed                   | Patient                          | Must     | To confirm |
| NT-14 | Laboratory result available                              | Patient                          | Must     | To confirm |
| NT-15 | New provider application awaiting review                 | Administrator                    | Must     | To confirm |

Recommendation for client consideration: notifications should announce that something requires attention without disclosing clinical detail outside the platform, since notifications may be seen on a shared or unlocked device. This is captured as FR-064 and requires client confirmation.

## 13. Data and Information Requirements

MedNet will hold the following categories of information. The categories follow from the features named in the source document; the specific fields within each category are not specified in the source and must be agreed with the client.

| Information category             | Purpose within MedNet                                                               | Status                      |
| -------------------------------- | ----------------------------------------------------------------------------------- | --------------------------- |
| Patient account and profile      | Identifies the patient and enables sign-in and contact.                             | Fields to confirm — OQ-04   |
| Provider account and profile     | Identifies the provider, their specialty and services, and supports patient search. | Fields to confirm — OQ-11   |
| Provider availability            | Determines which appointment times can be offered.                                  | Derived                     |
| Appointments                     | Records who is meeting whom, when, and the current status.                          | Derived                     |
| Messages                         | Records patient–provider conversations.                                             | Confirmed                   |
| Consultation records             | Records that a consultation took place and its clinical outcome.                    | Derived                     |
| Medical records                  | The patient's continuous clinical history.                                          | Contents to confirm — OQ-24 |
| Medication schedules and history | Supports adherence and forms part of the clinical record.                           | Confirmed                   |
| Vitals readings                  | Tracks the patient's measurements over time.                                        | Measures to confirm — OQ-32 |
| Home healthcare requests         | Records what was requested, where, and the current status.                          | Confirmed                   |
| Laboratory requests and results  | Records the test requested and the result returned.                                 | Confirmed                   |
| Notifications                    | Records what each user has been told.                                               | Confirmed                   |
| Administrative and audit records | Records administrative actions for accountability.                                  | Derived                     |

All of the above other than provider profile information should be treated as confidential patient health information and handled according to Section 16.

## 14. Integrations and External Services

The source document does not name any external system, provider or integration. Nothing in this section should be taken as agreed. The services below are listed because the features named by the client cannot function without something of that kind, and each requires a client decision on whether it is in scope and who bears its cost.

| Capability                              | Why it is implied                                                                                | Decision required                                    |
| --------------------------------------- | ------------------------------------------------------------------------------------------------ | ---------------------------------------------------- |
| Real-time voice or video service        | Virtual consultations (M-05) cannot take place without one.                                      | Confirm format and provider — OQ-20, OQ-50           |
| SMS delivery                            | Notifications and reminders may need to reach patients without reliable data access.             | Confirm channels and cost ownership — OQ-43, OQ-44   |
| Email delivery                          | Account and notification messages, if email is used.                                             | Confirm — OQ-05, OQ-43                               |
| Payment service                         | Only required if any MedNet service is chargeable. Mobile money is the common method in Liberia. | Confirm whether payments are in scope at all — OQ-15 |
| Document or image storage               | Required if medical records, laboratory results or messages may include files.                   | Confirm — OQ-18, OQ-25, OQ-41                        |
| Mapping or location service             | Only required if home healthcare requests rely on mapped locations.                              | Confirm — OQ-37                                      |
| Existing hospital or laboratory systems | Only required if MedNet must exchange data with systems already in use.                          | Confirm — OQ-26, OQ-51                               |

## Technical Direction (Provisional)

This section is an engineering recommendation for planning, not client-approved product scope. Revisit it after OQ-02 (roles), OQ-20 (consultation mode), OQ-50 (client platform), OQ-51 (integration) and OQ-54 (regulation) are resolved.

### Recommended Baseline

| Area                             | Provisional recommendation                                                                                 | Rationale / decision gate                                                                                                                                                         |
| -------------------------------- | ---------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Language and runtime             | Java 21 LTS; select a supported JDK distribution and patch level at project setup.                         | Stable LTS baseline with broad library and hosting support.                                                                                                                       |
| Backend framework                | Spring Boot 4.1.x, using the latest supported patch compatible with selected libraries.                    | Broad production ecosystem for security, validation, relational data, testing and operations. Spring Boot 4.1.1 documents Java 17–26 compatibility; Java 21 is within that range. |
| Application shape                | Modular monolith, deployed as one service with explicit domain modules.                                    | Keeps initial delivery and operations straightforward while preserving boundaries for later growth; current scale evidence does not justify microservices.                        |
| API                              | Spring MVC REST/JSON with an OpenAPI contract.                                                             | Supports web and mobile clients and gives independently testable contracts.                                                                                                       |
| Primary data store               | PostgreSQL, subject to hosting, backup, residency and recovery confirmation.                               | Relational transactions fit appointments, care records, identities and request-state changes.                                                                                     |
| Persistence and schema changes   | Spring Data JPA/Hibernate with versioned Flyway migrations.                                                | Provides explicit schema history and controlled deployments; avoid ad hoc production schema edits.                                                                                |
| Authentication and authorization | Spring Security; combine role checks with patient/provider relationship checks on every clinical resource. | A role alone must not grant access to every patient's record. Registration and verification remain subject to OQ-04–OQ-07.                                                        |
| Testing                          | JUnit 5, Spring test support and Testcontainers for database integration tests.                            | Exercises authorization and transaction behaviour against a real PostgreSQL-compatible engine.                                                                                    |
| Patient-facing clients           | Next.js, TypeScript and the App Router are the recommended web candidate; final client platform remains open under OQ-50. | SEO, metadata and public discovery can share a web framework with authenticated patient, provider and administrator flows. The client may still choose web, native mobile or both. |
| Build system                     | Gradle with Kotlin DSL and the committed Gradle Wrapper is the supplied architecture recommendation.       | The current starter uses Maven. Resolve whether to retain Maven or migrate before adding substantial backend modules; keep one authoritative build system.                           |
| Deployment shape                  | Separate web, API and database components behind HTTPS routing; begin without Kubernetes or microservices. | Keeps one product and domain experience while retaining clear deployment and security boundaries. Confirm hosting and data residency before production selection.                    |
| External services                | Integrate proven video, SMS, email and payment services only after scope and providers are agreed.         | Avoid building regulated, high-bandwidth or payment infrastructure without cost, provider and failure-mode decisions.                                                             |

### Framework Options

| Option      | Strength                                                                                                   | Trade-off                                                                                                    | Recommendation                                                                                   |
| ----------- | ---------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------ |
| Spring Boot | Broad Java ecosystem and mature support for security, relational data, validation, testing and operations. | More memory/startup overhead than lighter frameworks; keep the first system modular rather than distributed. | **Preferred default** unless team experience or measured hosting constraints indicate otherwise. |
| Quarkus     | Fast startup and lower-memory/container focus, with native-image support.                                  | More framework-specific choices; optimize only if measured hosting constraints require it.                   | Reconsider if profiling shows Spring Boot misses an agreed resource budget.                      |
| Micronaut   | Compile-time dependency injection and an efficient service runtime.                                        | Smaller ecosystem and fewer established patterns for this full workflow than Spring.                         | Viable alternative if the team has stronger Micronaut experience or a measured need.             |

### Architecture Guardrails

- Treat Spring Boot as the authority for security and business rules; frontend route guards improve UX but never grant access.
- Apply role and patient/provider relationship checks to every protected clinical resource. Treat client-supplied resource identifiers as untrusted.
- Keep patient identity, provider directory, appointments, messaging, records, medications, vitals, home care, laboratory workflows, notifications and administration as modules in one deployable application initially.
- Enforce authorization by both role and care relationship. Audit access to sensitive records and administrative actions; do not log clinical payloads, credentials or tokens.
- Use `/api/v1` for the Spring Boot REST API, version its contract with OpenAPI, and return consistent request IDs and safe structured errors.
- Use structured request-aware logs, health/readiness checks, metrics and tracing as operational needs mature. Confirm backup, restore and incident ownership before production.
- Do not implement video transport, payment processing, medication-interaction checking or automated diagnosis without approved service providers, licensed data sources and clinical/legal review.
- Design for intermittent mobile connectivity: make retries safe, prevent duplicate bookings and requests, and show current request status clearly.
- Confirm hosting support for Java, JDK version, database operations, backups, restore tests, monitoring and incident ownership before selecting production topology.
- Record approved stack, hosting and integration choices as Architecture Decision Records before implementation.

For the full component diagram, frontend/backend boundaries, deployment direction, testing approach and current Maven-versus-Gradle status, see the [Production Architecture Guide](PRODUCTION_ARCHITECTURE_GUIDE.md). The guide remains provisional and does not close OQ-50 or other product decisions.

**Framework references:** [Spring Boot system requirements](https://docs.spring.io/spring-boot/4.1/system-requirements.html) and [Quarkus overview](https://quarkus.io/about/). Check supported releases again when implementation begins.

### Naming Conventions and Short Names

The codebase should use short, consistent names for folders, packages, classes and files, but only when the short form is clear and repeated often. Not every domain name needs to be shortened. Terms like `patient`, `provider`, `record`, `vital`, `home`, `lab`, and `appointment` can remain fully written if they are already readable and not noisy. The rule is: shorten only the repeated, noisy names that are painful to read across many classes and folders.

#### Standard naming rules

- Do not abbreviate common domain names that are already clear and short enough: `patient`, `provider`, `record`, `vital`, `home`, `lab`, `document`, `schedule`.
- Abbreviate only where the full term is long, repeated, or awkward in code: `auth`, `appt`, `msg`, `med`, `notif`, `admin`, `dto`, `svc`, `repo`, `cfg`, `util`, `aud`, `evt`, `sec`, `api`, `dom`.
- Use lowercase package names and kebab-case folder names for non-Java resource boundaries.
- Use UpperCamelCase for Java classes and lowerCamelCase for methods and variables.
- Keep each abbreviation explicit in the naming table and in the project documentation.
- When in doubt, prefer the full term over a vague or overloaded abbreviation.

#### Short-name map for MedNet

| Short name | Full meaning                      | When used                               |
| ---------- | --------------------------------- | --------------------------------------- |
| `auth`     | authentication and authorization  | login, session, roles, access control   |
| `appt`     | appointment                       | scheduling and booking flows            |
| `msg`      | message                           | conversations, chat, message events     |
| `med`      | medication                        | prescriptions, adherence, reminders     |
| `notif`    | notification                      | alerts, reminders, delivery status      |
| `admin`    | administration                    | admin dashboard, approvals, reporting   |
| `dto`      | data transfer object              | request and response payloads           |
| `svc`      | service                           | application service layer               |
| `repo`     | repository                        | persistence access layer                |
| `cfg`      | configuration                     | config classes and environment setup    |
| `util`     | utility                           | shared helpers and common logic         |
| `aud`      | audit                             | audit logs and change history           |
| `evt`      | event                             | domain events and handlers              |
| `sec`      | security                          | security filters, handlers, policy code |
| `api`      | application programming interface | controller and contract definitions     |
| `dom`      | domain                            | shared domain model and rules           |

#### Terms that should stay full

| Term       | Reason                                                                                         |
| ---------- | ---------------------------------------------------------------------------------------------- |
| `patient`  | Already clear and natural in code and APIs                                                     |
| `provider` | Clear and not noisy enough to justify abbreviation                                             |
| `record`   | Common domain concept; full name reads better                                                  |
| `vital`    | Full term is readable and specific                                                             |
| `home`     | Usually clear enough in context                                                                |
| `lab`      | Acceptable only if the codebase is very busy; otherwise prefer `laboratory` in business layers |
| `document` | Full name is clear and easier to read                                                          |
| `schedule` | Full name is preferred unless repeated heavily                                                 |

#### Recommended folder and package standard

| Area              | Folder / package                                                         | Meaning                                      |
| ----------------- | ------------------------------------------------------------------------ | -------------------------------------------- |
| API layer         | `api/controller`, `api/dto`, `api/mapper`, `api/validation`              | HTTP-facing contracts and translation        |
| Application layer | `app/service`, `app/facade`, `app/event`                                 | business orchestration and use cases         |
| Domain layer      | `domain/auth`, `domain/patient`, `domain/provider`, `domain/appointment` | core business logic and entities             |
| Persistence layer | `infra/repository`, `infra/entity`, `infra/persistence`                  | JPA entities, repositories, schema access    |
| Security          | `infra/security`, `infra/security/filter`, `infra/security/provider`     | auth, JWT, RBAC, access checks               |
| Configuration     | `infra/config`                                                           | database, OpenAPI, cache, event, mail config |
| Shared code       | `common/exception`, `common/util`, `common/enums`, `common/constants`    | cross-cutting helpers                        |
| Testing           | `src/test/java/com/mednet/...`                                           | integration and unit tests                   |

#### Examples

- Prefer `patient` over `pat` unless the codebase repeatedly becomes unreadable.
- Prefer `provider` over `prov` in business-domain packages.
- Prefer `record` over `rec` in clinical models where the full meaning matters.
- Prefer `appointment` over `appt` in domain-level model names and API contracts.
- Prefer `medication` over `med` in patient-facing and clinical descriptions, but `med` is acceptable in a service or repository layer if repeated often.
- Prefer `notification` over `notif` in user-facing text, but `notif` is acceptable in backend event and delivery code.

This convention keeps names readable, standard and production-safe. It also reduces the risk of creating unclear, scattered abbreviations across the system.

Related planning docs: [backend-api-plan.md](backend-api-plan.md) and [frontend-plan.md](frontend-plan.md).

### API Design Direction (Provisional)

This is a working design direction for the backend, not a client-approved contract. It follows the workspace guidance for API-first design, modular backend services, and explicit authorization boundaries while staying compatible with the MedNet requirements in Sections 8–18.

#### Recommended API pattern

- Use a versioned REST API under `/api/v1`.
- Keep routes resource-based and nouns-first: `/patients`, `/providers`, `/appointments`, `/messages`, `/records`, `/medications`, `/vitals`, `/home-care-requests`, `/lab-requests`, `/notifications`, `/admin`.
- Use standard HTTP methods with consistent envelopes: `success: true/false`, `data`, `meta`, and `error` payloads.
- Return structured validation and authorization errors instead of raw stack traces.
- Put business logic in domain services, not in controllers/routes.
- Separate read, write, and admin endpoints to keep authorization explicit and auditable.

#### Domain modules for the first backend

| Domain              | Main resources                                                   | Notes                                                                                |
| ------------------- | ---------------------------------------------------------------- | ------------------------------------------------------------------------------------ |
| Identity and access | `patients`, `providers`, `sessions`, `roles`, `audit-logs`       | Patient and provider account lifecycle, access control, login and password recovery. |
| Care coordination   | `appointments`, `messages`, `consultations`                      | Booking, acceptance, rescheduling, messaging, consultation records.                  |
| Clinical records    | `medical-records`, `medication-schedules`, `vitals`, `documents` | Persistent patient history and record continuity.                                    |
| Service requests    | `home-care-requests`, `lab-requests`                             | Patient-initiated requests, routing, fulfilment status, result processing.           |
| Notifications       | `notifications`, `notification-preferences`                      | In-app notifications and outbound delivery integrations.                             |
| Administration      | `admin/dashboard`, `audit-events`, `provider-approvals`          | Admin-only oversight, provider verification and operational monitoring.              |

#### API contract guardrails

- Use `GET /api/v1/...` for reads; `POST` for create actions; `PATCH` for partial updates; `DELETE` only where the product explicitly requires it.
- For list endpoints, return paginated results with `page`, `limit`, `total`, and `hasNext` metadata.
- Treat patient identity as a protected boundary: every clinical read and write must verify both role and patient relationship.
- Every state-changing event should emit an audit record and a notification event where appropriate.
- Keep the API contract independent from the eventual client platform; the same contract should serve web and mobile clients.

#### Recommended v1 endpoints

| Area          | Example endpoint shape                                                                                   |
| ------------- | -------------------------------------------------------------------------------------------------------- |
| Auth          | `POST /api/v1/auth/register`, `POST /api/v1/auth/login`, `POST /api/v1/auth/forgot-password`             |
| Patients      | `GET /api/v1/patients/{id}`, `PATCH /api/v1/patients/{id}/profile`                                       |
| Providers     | `GET /api/v1/providers`, `POST /api/v1/providers/applications`, `PATCH /api/v1/providers/{id}/approval`  |
| Appointments  | `POST /api/v1/appointments`, `GET /api/v1/patients/{id}/appointments`, `PATCH /api/v1/appointments/{id}` |
| Messages      | `GET /api/v1/conversations/{id}/messages`, `POST /api/v1/conversations/{id}/messages`                    |
| Records       | `GET /api/v1/patients/{id}/records`, `POST /api/v1/patients/{id}/records`                                |
| Medications   | `GET /api/v1/patients/{id}/medications`, `POST /api/v1/patients/{id}/medications`                        |
| Vitals        | `POST /api/v1/patients/{id}/vitals`, `GET /api/v1/patients/{id}/vitals`                                  |
| Home care     | `POST /api/v1/home-care-requests`, `PATCH /api/v1/home-care-requests/{id}`                               |
| Labs          | `POST /api/v1/lab-requests`, `GET /api/v1/lab-requests/{id}`                                             |
| Notifications | `GET /api/v1/notifications`, `PATCH /api/v1/notifications/{id}/read`                                     |
| Admin         | `GET /api/v1/admin/reports/summary`, `GET /api/v1/admin/providers/pending`                               |

#### API security model

- Require authentication on all protected routes.
- Use role-based access plus patient-care relationship checks for patient-specific data.
- Do not trust client-supplied user IDs or patient IDs; canonicalize them from the verified session.
- Log only metadata and outcome, never raw credentials, tokens, or full sensitive payloads.
- Store server-side secret material only in environment variables; do not commit values to the repository.

#### Configuration placeholders for backend setup

The repository should keep only variable names, not secrets. The current MedNet environment file contains placeholders for:

- `DATABASE_URL`
- `PRODUCTION_URL`
- `PUBLIC_URL`
- `NEXTAUTH_URL`
- `NEXTAUTH_SECRET`
- `GOOGLE_CLIENT_ID`
- `GOOGLE_CLIENT_SECRET`
- `EKDSend_API_URL`
- `EKDSend_API_KEY`
- `FROM_EMAIL`

These names should live in `.env.example` and the deployment environment; actual values remain in local or Launchpad secret storage. This is consistent with the workspace security guidance and avoids writing real credentials into the repo.

### Frontend Experience Direction (Provisional)

This is the primary frontend plan for MedNet. It is intentionally provisional because the platform decision is still open under OQ-50, but it aligns with the workspace guidance for a responsive, low-data, accessible patient-facing web experience and an admin dashboard.

#### Recommended frontend direction

1. Ship a mobile-first, responsive web app as the default patient and provider experience.
2. Consider a progressive web app or lightweight native shell later, but only after OQ-50 and device support requirements are confirmed.
3. Use a single shared backend API contract for both web browsers and future mobile clients.
4. Build the UI around plain language, accessibility, and low-bandwidth use in a resource-constrained environment.

#### Suggested stack

- Next.js App Router + TypeScript + Tailwind CSS.
- Shared design tokens for theme and layout, with light/dark mode support and strong color contrast.
- Reusable feature components for auth, provider search, records, forms, dashboards and alerts.
- Server-side access checks and route-level auth for protected pages.

#### Route layout structure

| Area              | Suggested shell       | Purpose                                                                             |
| ----------------- | --------------------- | ----------------------------------------------------------------------------------- |
| Public marketing  | `(main)` / `(public)` | Product landing page, trust and support information, provider information.          |
| Authentication    | `(auth)`              | Registration, login, password reset, provider onboarding.                           |
| Patient app       | `(patient)`           | Dashboard, appointments, messaging, records, vitals, lab and home care requests.    |
| Provider app      | `(provider)`          | Availability, appointment queue, messages, consultation notes, patient record view. |
| Administrator app | `(admin)`             | Provider approvals, audit trail, reports, service status.                           |

#### Core user journeys for the frontend

- Patient registration and sign-in.
- Provider search and selection.
- Appointment booking and status tracking.
- Patient–provider messaging with notification banners and unread counts.
- Medication reminders and vitals capture.
- Home care and laboratory request submission and progress tracking.
- Provider approval flow and admin dashboard operations.

#### UX requirements from the prompt library

- Use a clear layout system: loading, empty, error, and success states on every flow.
- Prefer simple, accessible forms and one-step tasks over dense forms with hidden logic.
- Keep touch targets large enough for low-end phones and reduced literacy users.
- Minimise data consumption by avoiding oversized assets and repeated downloads.
- Show API errors and form field errors in a consistent, plain-language manner.
- Put operational actions behind confirmation modals to prevent costly mistakes.

#### Frontend data handling

- Fetch data from the backend API using typed client wrappers and response envelopes.
- Use server components where possible; only move interactivity to the client when needed.
- Keep patient, provider, and admin data separated by route group and authorization rules.
- For mobile/slow network conditions, support background retries and safe state rehydration.

#### Frontend security and trust boundaries

- Never render patient health data on a page without the session and authorization check.
- Do not expose tokens or full secrets in client code or in client-side environment variables.
- Use clear role separation between patient, provider, and admin views.
- Ensure all mutating actions are protected by server-side validation and authorization.

#### Recommended prioritisation

- Phase 1: patient auth, provider directory, appointment flows, medical record shell, notification and admin basics.
- Phase 2: messaging, medication, vitals, home care and laboratory workflows.
- Phase 3: video/voice consultations and advanced clinical review workflows, gated by OQ-20, OQ-21, OQ-23 and OQ-54.

This should be treated as the recommended product UX direction, not the final client decision. It is intentionally conservative and intentionally separate from the clinical and regulatory decisions still awaiting the client's answer.

## 15. Non-Functional Requirements

The source document does not state non-functional requirements. The requirements below are derived from the client's stated intent that MedNet be reliable, accessible and convenient, and from the Liberian operating context the source document describes. Each requires client confirmation before it becomes a target.

| Ref    | Area                       | Requirement                                                                                                                                                               | Status                     |
| ------ | -------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------- |
| NFR-01 | Reliability                | MedNet should be available to patients and providers at all times, since healthcare needs are not confined to office hours. A target availability level should be agreed. | Confirm                    |
| NFR-02 | Low-bandwidth performance  | MedNet should remain usable on slow or intermittent mobile connections, which are common across much of Liberia.                                                          | Confirm                    |
| NFR-03 | Data efficiency            | The platform should minimise the data cost of ordinary use, since data cost is a real barrier to adoption.                                                                | Confirm                    |
| NFR-04 | Device support             | The platform should work on the entry-level and mid-range smartphones that most patients use.                                                                             | Confirm — depends on OQ-52 |
| NFR-05 | Usability                  | The platform should be usable by patients with limited digital experience, using plain language and simple journeys.                                                      | Confirm                    |
| NFR-06 | Accessibility and language | Consideration should be given to users with low literacy or limited English.                                                                                              | Confirm — OQ-53            |
| NFR-07 | Scalability                | The platform should accommodate growth in patients, providers and facilities without redesign.                                                                            | Confirm                    |
| NFR-08 | Data integrity             | Medical information must not be lost or corrupted, and must be backed up and recoverable.                                                                                 | Must                       |
| NFR-09 | Maintainability            | The platform should be structured so that new services and provider types can be added over time.                                                                         | Confirm                    |

## 16. Security and Privacy Requirements

The source document does not address security or privacy. Because MedNet holds patient health information, the following requirements are stated as mandatory regardless, and the associated policy questions must be answered by the client.

| Ref    | Requirement                                                                                                               | Status                             |
| ------ | ------------------------------------------------------------------------------------------------------------------------- | ---------------------------------- |
| SEC-01 | All access to MedNet must require authentication appropriate to the user's role.                                          | Must                               |
| SEC-02 | A user must only be able to see information that their role and relationship to the patient entitles them to see.         | Must                               |
| SEC-03 | Patient health information must be protected both while stored and while being transmitted.                               | Must                               |
| SEC-04 | Access to and changes to patient records must be recorded so that they can be audited.                                    | Must                               |
| SEC-05 | Administrative accounts must be individually identifiable, so that every administrative action can be traced to a person. | Must                               |
| SEC-06 | Messages, consultations and results must be accessible only to the parties concerned.                                     | Must                               |
| SEC-07 | Patients must be informed how their health information is used and stored, and must consent to it.                        | Must                               |
| SEC-08 | The platform must comply with applicable Liberian law and Ministry of Health requirements governing patient data.         | Must — requires legal input, OQ-54 |

OPEN. The client should confirm the regulatory framework MedNet must satisfy, including any Ministry of Health or Liberia Medical and Dental Council requirements governing telemedicine, prescribing at a distance, and the holding of patient records. These may impose requirements that change the product (OQ-54, OQ-23).

## 17. Reporting and Dashboard Requirements

The source document names an admin dashboard but does not state what it should show. The following is a proposed minimum set for client confirmation.

| Ref    | Reporting item                                                      | Status  |
| ------ | ------------------------------------------------------------------- | ------- |
| RPT-01 | Number of registered patients, and new registrations over a period. | Confirm |
| RPT-02 | Number of providers by status: pending, approved, suspended.        | Confirm |
| RPT-03 | Appointments booked, completed, cancelled and missed over a period. | Confirm |
| RPT-04 | Virtual consultations completed over a period.                      | Confirm |
| RPT-05 | Home healthcare requests by status.                                 | Confirm |
| RPT-06 | Laboratory requests and results by status.                          | Confirm |
| RPT-07 | Requests that are overdue or awaiting action.                       | Confirm |
| RPT-08 | Ability to export reporting data.                                   | Confirm |

Any reporting must present aggregate figures rather than identifiable patient clinical information, unless the client confirms otherwise under OQ-46.

## 18. Assumptions and Dependencies

18.1 Assumptions

The following assumptions have been made in preparing this document. If any is incorrect, the affected requirements must be revised.

| Ref   | Assumption                                                                                                                  | Affected areas          |
| ----- | --------------------------------------------------------------------------------------------------------------------------- | ----------------------- |
| AS-01 | The twelve source-attributed capabilities are candidate scope; the sponsor will approve the first-release subset and order. | Section 8, OQ-01, OQ-03 |
| AS-02 | Patients access MedNet themselves, from their own device.                                                                   | M-01, NFR-04            |
| AS-03 | Providers are individual healthcare professionals who hold their own account.                                               | M-02                    |
| AS-04 | Providers must be verified by MedNet before treating patients on the platform.                                              | M-02, M-12              |
| AS-05 | The patient's medical record is a single record that spans all providers they see through MedNet.                           | M-06                    |
| AS-06 | MedNet is an initial launch and does not need to replace or connect to an existing system.                                  | Section 14              |
| AS-07 | MedNet operates within Liberia in the first release.                                                                        | Section 15              |
| AS-08 | MedNet facilitates care but does not itself provide clinical advice or diagnosis.                                           | BR-10                   |

18.2 Dependencies

- Client decisions on the open questions in Section 19 — design cannot be completed and effort cannot be reliably estimated until these are answered.
- Availability of licensed healthcare providers willing to join the platform; MedNet has no value to patients without them.
- Participation of laboratories and home healthcare providers, without which M-09 and M-10 cannot operate.
- Confirmation of the regulatory position on telemedicine and patient data in Liberia.
- Availability of third-party services for consultations and notifications, once selected.

## 19. Open Questions Requiring Client Confirmation

This section consolidates every point on which the source document is silent. Questions marked "Blocking" prevent design or estimation from proceeding in the affected area.

| Ref   | Area          | Question                                                                                                                                                       | Urgency  |
| ----- | ------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------- |
| OQ-01 | Scope         | The feature list ends with "etc." — are the twelve capabilities the complete scope of the first release, or were others intended?                              | Blocking |
| OQ-02 | Roles         | Do hospitals, laboratories and home healthcare workers hold their own accounts and log in to MedNet, or is their work coordinated by the MedNet administrator? | Blocking |
| OQ-03 | Scope         | Must all twelve capabilities launch together, or should delivery be phased? If phased, in what priority order?                                                 | Blocking |
| OQ-04 | Registration  | What details must a patient provide at registration?                                                                                                           | Blocking |
| OQ-05 | Registration  | Is the primary sign-in credential a phone number or an email address?                                                                                          | Blocking |
| OQ-06 | Registration  | Must a patient's contact detail or identity be verified before the account is active?                                                                          | High     |
| OQ-07 | Records       | Which providers may see which parts of a patient's medical record, and does the patient control that access?                                                   | Blocking |
| OQ-08 | Registration  | May one account manage dependants, such as a parent managing a child's care?                                                                                   | High     |
| OQ-09 | Providers     | What professional credentials must a provider supply, and how are they verified?                                                                               | Blocking |
| OQ-10 | Providers     | Are providers onboarded individually, or through their hospital or facility?                                                                                   | Blocking |
| OQ-11 | Providers     | What information does a provider profile show to patients?                                                                                                     | High     |
| OQ-12 | Providers     | Can patients rate or review providers?                                                                                                                         | Medium   |
| OQ-13 | Appointments  | Is a booking confirmed automatically, or only once the provider accepts it?                                                                                    | Blocking |
| OQ-14 | Appointments  | Do appointments distinguish between in-person and virtual attendance?                                                                                          | High     |
| OQ-15 | Commercial    | Are any MedNet services chargeable — appointments, consultations, home healthcare, laboratory services? If so, how and when is payment taken?                  | Blocking |
| OQ-16 | Appointments  | Do cancellation windows or no-show rules apply?                                                                                                                | Medium   |
| OQ-17 | Messaging     | May a patient message any approved provider, or only one with whom they have an appointment or care relationship?                                              | High     |
| OQ-18 | Messaging     | May messages include attachments such as photographs?                                                                                                          | Medium   |
| OQ-19 | Messaging     | Is a response time expected of providers, and how are urgent messages handled?                                                                                 | High     |
| OQ-20 | Consultations | Are consultations by video, voice, or text? This is material given bandwidth and data cost.                                                                    | Blocking |
| OQ-21 | Consultations | Are consultations recorded, and if so who may access the recording?                                                                                            | High     |
| OQ-22 | Consultations | What happens if the connection fails during a consultation?                                                                                                    | High     |
| OQ-23 | Regulatory    | May a doctor prescribe during a virtual consultation, and is this permitted under Liberian regulation?                                                         | Blocking |
| OQ-24 | Records       | What information does a medical record contain — diagnoses, prescriptions, allergies, history, documents?                                                      | Blocking |
| OQ-25 | Records       | May a patient upload their own historical records or documents?                                                                                                | Medium   |
| OQ-26 | Records       | Must records held by existing hospitals or clinics be imported into MedNet?                                                                                    | High     |
| OQ-27 | Records       | How long must medical records be retained?                                                                                                                     | High     |
| OQ-28 | Medication    | May a provider create or prescribe a medication schedule for a patient, or does the patient enter it themselves?                                               | Blocking |
| OQ-29 | Medication    | Does MedNet hold a drug catalogue, or does the patient enter medication names freely?                                                                          | High     |
| OQ-30 | Medication    | Should MedNet warn of drug interactions? This carries clinical risk and requires a licensed data source.                                                       | High     |
| OQ-31 | Medication    | Is adherence information reported back to the provider?                                                                                                        | Medium   |
| OQ-32 | Vitals        | Which vital signs are supported?                                                                                                                               | Blocking |
| OQ-33 | Vitals        | Are readings entered manually only, or are connected devices in scope?                                                                                         | High     |
| OQ-34 | Vitals        | Should out-of-range readings raise an alert, and if so to whom?                                                                                                | High     |
| OQ-35 | Home care     | Which services can be requested at home?                                                                                                                       | Blocking |
| OQ-36 | Home care     | Who fulfils home healthcare requests — MedNet staff, partner organisations, or individual providers?                                                           | Blocking |
| OQ-37 | Home care     | How is the patient's location captured, given limited formal street addressing?                                                                                | High     |
| OQ-38 | Home care     | Which geographic areas are served in the first release?                                                                                                        | High     |
| OQ-39 | Laboratory    | Does a laboratory request require a doctor's order, or may a patient initiate it alone?                                                                        | Blocking |
| OQ-40 | Laboratory    | Are samples collected at a laboratory or at the patient's home?                                                                                                | High     |
| OQ-41 | Laboratory    | How are results entered — uploaded by the laboratory, or entered by an administrator?                                                                          | Blocking |
| OQ-42 | Laboratory    | Should a clinician review a result before the patient sees it?                                                                                                 | High     |
| OQ-43 | Notifications | Which notification channels are used — in-app, SMS, email, push?                                                                                               | Blocking |
| OQ-44 | Notifications | Is SMS required for patients without smartphones or reliable data? This carries an ongoing cost.                                                               | High     |
| OQ-45 | Notifications | May users choose which notifications they receive?                                                                                                             | Medium   |
| OQ-46 | Admin         | May administrators view patient clinical information?                                                                                                          | High     |
| OQ-47 | Admin         | Is there more than one level of administrator?                                                                                                                 | Medium   |
| OQ-48 | Admin         | Which figures should the admin dashboard display?                                                                                                              | High     |
| OQ-49 | Safety        | What should MedNet do when a patient reports an emergency or life-threatening situation?                                                                       | Blocking |
| OQ-50 | Platform      | Is MedNet delivered as a mobile application, a web application, or both, and which is the priority?                                                            | Blocking |
| OQ-51 | Integration   | Must MedNet exchange data with any system already in use by hospitals or laboratories?                                                                         | High     |
| OQ-52 | Platform      | Which devices and operating system versions must be supported?                                                                                                 | High     |
| OQ-53 | Accessibility | Must MedNet support languages other than English, or accommodate users with low literacy?                                                                      | High     |
| OQ-54 | Regulatory    | What Liberian legal and Ministry of Health requirements govern patient data and telemedicine on MedNet?                                                        | Blocking |

## 20. Out of Scope

The following are not part of MedNet as described in the source document. They are listed so that expectations are explicit. Any of them may be brought into scope by the client, but each would represent an addition to the agreed requirements.

- Any capability not listed in Section 8, subject to the client's answer to OQ-01.
- Clinical decision-making, diagnosis, or medical advice generated by the platform itself.
- Emergency and ambulance response services.
- Pharmacy dispensing, medicine ordering, or delivery.
- Health insurance administration or claims.
- Hospital administration functions such as bed management, staffing or billing.
- Replacement of hospital or laboratory systems already in use.
- Operation outside Liberia.
  Note: payment handling is not listed as out of scope because the source document is silent on it and it may be intended. It is raised as OQ-15 and must be settled explicitly.

## 21. Acceptance Criteria

Detailed acceptance criteria for each module are given in Section 9. MedNet will be considered ready for client acceptance when the following are all true.

| Ref   | Acceptance criterion                                                                                                        | Related module |
| ----- | --------------------------------------------------------------------------------------------------------------------------- | -------------- |
| AC-01 | A new patient can register, sign in, and recover access to their account.                                                   | M-01           |
| AC-02 | A provider can apply for an account, be approved by an administrator, and become visible to patients.                       | M-02, M-12     |
| AC-03 | A patient can find a provider, book an appointment, and receive confirmation; the provider sees and can act on the request. | M-03           |
| AC-04 | A patient and a provider can exchange messages, and each is notified of the other's message.                                | M-04           |
| AC-05 | A patient and a provider can hold a virtual consultation, and its outcome is recorded to the patient's record.              | M-05           |
| AC-06 | A patient's medical record shows entries from more than one source in one place, each with its author and date.             | M-06           |
| AC-07 | A patient can set a medication schedule and receives reminders at the scheduled times.                                      | M-07           |
| AC-08 | A patient can record vitals and view their history, and an authorised provider can see the same readings.                   | M-08           |
| AC-09 | A patient can request home healthcare, track its status, and be notified as it progresses.                                  | M-09           |
| AC-10 | A patient can request a laboratory service and receive the result in their medical record.                                  | M-10           |
| AC-11 | Every event in the notification catalogue reaches the correct recipient.                                                    | M-11           |
| AC-12 | An administrator can manage providers, patients, appointments and requests, and view platform activity.                     | M-12           |
| AC-13 | A user cannot access information belonging to a patient they are not entitled to see.                                       | Section 16     |
| AC-14 | The platform performs acceptably on a typical Liberian mobile connection and device.                                        | NFR-02, NFR-04 |

## 22. Future Enhancements

The source document does not identify future enhancements. It does, however, describe MedNet as a coordinated ecosystem including hospitals and "other healthcare service providers", which suggests the platform is expected to grow beyond its first release. The items below are noted only as candidates for later discussion; none is a requirement of this PRD and none should be assumed to be in scope.

- Direct participation of hospitals and additional provider types, if not included in the first release (see OQ-02).
- Additional service categories under "other healthcare service providers".
- Deeper integration with laboratory and hospital systems.
- Expanded reporting for health service planning.
- Geographic expansion beyond the first-release service areas.

## 23. Requirements Traceability

This section maps the content of this PRD back to the source document, so that the client can verify that nothing has been added and nothing has been lost.

| Source ref | Statement in the source document                                                                                                                          | Where it appears in this PRD       |
| ---------- | --------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------- |
| SRC-01     | Challenges of access to timely, convenient and coordinated quality healthcare in Liberia and Africa.                                                      | Section 4 (P1–P5), Section 5       |
| SRC-02     | Fragmented services, limited home-based healthcare and laboratory access, inadequate medication adherence and follow-up support.                          | Section 4 (P6–P10)                 |
| SRC-03     | Need for a platform connecting patients, professionals, hospitals, laboratories and other providers in one ecosystem.                                     | Sections 3, 6, 7, OBJ-04           |
| SRC-04     | Patients' registration / login                                                                                                                            | M-01, FR-001 to FR-005             |
| SRC-05     | Doctor / provider accounts                                                                                                                                | M-02, FR-006 to FR-011             |
| SRC-06     | Appointment booking                                                                                                                                       | M-03, FR-012 to FR-018             |
| SRC-07     | Patient-doctor messaging                                                                                                                                  | M-04, FR-019 to FR-023             |
| SRC-08     | Patient-doctor virtual consultations                                                                                                                      | M-05, FR-024 to FR-029             |
| SRC-09     | Medical records                                                                                                                                           | M-06, FR-030 to FR-035             |
| SRC-10     | Drugs scheduling / records                                                                                                                                | M-07, FR-036 to FR-041             |
| SRC-11     | Patients' vitals                                                                                                                                          | M-08, FR-042 to FR-046             |
| SRC-12     | Home healthcare requests                                                                                                                                  | M-09, FR-047 to FR-052             |
| SRC-13     | Laboratory services                                                                                                                                       | M-10, FR-053 to FR-059             |
| SRC-14     | Notifications                                                                                                                                             | M-11, FR-060 to FR-064, Section 12 |
| SRC-15     | Admin dashboard                                                                                                                                           | M-12, FR-065 to FR-072, Section 17 |
| SRC-16     | "etc." following the feature list                                                                                                                         | OQ-01                              |
| SRC-17     | Goal: bridge the gap between patients and healthcare services in Liberia through technology, making healthcare more accessible, connected and convenient. | Sections 3, 5                      |

Every capability named in the source document is represented above. All other content in this PRD is marked DERIVED or raised as an open question.

## 24. Review and Approval

The client is asked to review this document and to respond on three points:

- Confirm that Sections 3 to 8 accurately reflect the intent for MedNet.
- Confirm, amend or reject each requirement marked DERIVED in Section 9.
- Provide answers to the open questions in Section 19, beginning with those marked Blocking.
  Once these are settled, this document will be reissued as Version 1.0 for formal approval, after which it becomes the agreed basis for design, development and acceptance.

| Role                     | Name | Signature | Date |
| ------------------------ | ---- | --------- | ---- |
| Client / Product Sponsor |      |           |      |
| Product Owner            |      |           |      |
| Delivery Lead            |      |           |      |

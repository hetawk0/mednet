import type { Metadata } from "next";
import Link from "next/link";
import { SiteFooter } from "@/components/site-footer";
import { SiteHeader } from "@/components/site-header";

export const metadata: Metadata = {
  title: "Services",
  description:
    "Find healthcare providers, request appointments, communicate with care teams, and manage health information with MedNet.",
};

const services = [
  [
    "01",
    "Patient and provider accounts",
    "Manage patient accounts and provider profiles, including professional information and specialties.",
    "Accounts",
  ],
  [
    "02",
    "Find a provider",
    "Search provider profiles by specialty and services offered.",
    "Provider directory",
  ],
  [
    "03",
    "Appointments and messages",
    "Request appointments, follow updates, and communicate with providers.",
    "Care coordination",
  ],
  [
    "04",
    "Health records and medications",
    "Keep health information together and manage medication schedules and reminders.",
    "Care information",
  ],
  [
    "05",
    "Vitals and service requests",
    "Record vitals and request home healthcare or laboratory services.",
    "Care services",
  ],
  [
    "06",
    "Virtual consultations and updates",
    "Join virtual consultations and receive notifications about appointments and requests.",
    "Care coordination",
  ],
];

export default function ServicesPage() {
  return (
    <>
      <SiteHeader />
      <main id="main-content" className="page-wrap inner-page">
        <div className="inner-page-heading">
          <p className="eyebrow">MedNet services</p>
          <h1>Find providers and manage your care.</h1>
          <p className="inner-page-lede">
            Search provider profiles, request appointments, message your care
            team, and keep your health information together through MedNet.
          </p>
        </div>
        <div className="status-callout">
          <span className="status-callout-label">Care, coordinated</span>
          <p>
            Find a provider, arrange an appointment, and keep track of messages
            and health information in one place.
          </p>
        </div>
        <div className="roadmap-list">
          {services.map(([number, name, description, stage]) => (
            <article className="roadmap-item" key={number}>
              <span className="service-number">{number}</span>
              <div>
                <span className="roadmap-stage">{stage}</span>
                <h2>{name}</h2>
                <p>{description}</p>
              </div>
            </article>
          ))}
        </div>
        <div className="inner-page-bottom">
          <p>
            MedNet helps patients and providers keep appointments, messages, and
            health information organized throughout care.
          </p>
          <Link className="text-link" href="/about">
            How MedNet works <span aria-hidden="true">-&gt;</span>
          </Link>
        </div>
      </main>
      <SiteFooter />
    </>
  );
}

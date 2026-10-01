import type { Metadata } from "next";
import Link from "next/link";
import { SiteFooter } from "@/components/site-footer";
import { SiteHeader } from "@/components/site-header";

export const metadata: Metadata = {
  title: "Our approach",
  description: "How MedNet helps patients find providers, coordinate appointments, and protect health information.",
};

const principles = [
  ["01", "Patient record access is checked", "MedNet checks each protected request against the person's role and relationship to the patient before returning health information."],
  ["02", "The backend owns the rules", "Spring Boot handles healthcare business rules and protected data. The website makes MedNet easy to use; it does not grant access."],
  ["03", "The service works on mobile", "Use MedNet on a phone with clear language, accessible controls, and efficient data use."],
];

export default function AboutPage() {
  return (
    <><SiteHeader /><main id="main-content" className="page-wrap inner-page about-page">
      <div className="inner-page-heading"><p className="eyebrow">For patients and providers</p><h1>One place for the details of care.</h1><p className="inner-page-lede">Find healthcare providers, request appointments, communicate with care teams, and keep health information together through MedNet.</p></div>
      <div className="principle-list">{principles.map(([number, title, description]) => <article className="principle-item" key={number}><span className="service-number">{number}</span><h2>{title}</h2><p>{description}</p></article>)}</div>
      <section className="decision-note"><p className="eyebrow">Professional care, with support from MedNet</p><h2>Find your provider. Organize your next step.</h2><p>MedNet helps you search for a provider, request an appointment, and keep information from your care in one place. Diagnosis and treatment are provided by healthcare professionals.</p><Link className="text-link" href="/services">Explore MedNet services <span aria-hidden="true">-&gt;</span></Link></section>
    </main><SiteFooter /></>
  );
}
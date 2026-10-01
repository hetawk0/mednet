import Link from "next/link";
import { SiteFooter } from "@/components/site-footer";
import { SiteHeader } from "@/components/site-header";

const services = [
  ["01", "Find a provider", "Search healthcare providers by specialty and the services they offer."],
  ["02", "Request appointments", "Send appointment requests and follow updates in one place."],
  ["03", "Keep health information together", "View health information and keep track of care over time."],
  ["04", "Request home or lab services", "Request home healthcare or laboratory services and follow the response."],
];

export default function Home() {
  return (
    <>
      <a className="skip-link" href="#main-content">Skip to content</a>
      <SiteHeader />
      <main id="main-content">
        <section className="hero page-wrap">
          <div className="hero-copy">
            <p className="eyebrow">Patient and provider services in Liberia</p>
            <h1>Find a provider.</h1>
            <p className="hero-description">Search healthcare providers, request appointments, and keep information about your care together with MedNet.</p>
            <div className="hero-actions">
              <Link className="button button-primary" href="/services">Explore services <span aria-hidden="true">-&gt;</span></Link>
              <a className="text-link" href="#approach">How MedNet works</a>
            </div>
            <div className="hero-note"><span className="hero-note-mark" aria-hidden="true">M</span><p><strong>For patients and providers.</strong><span>Provider search, appointments, and care information.</span></p></div>
          </div>
          <div className="hero-visual">
            <div className="hero-photo" role="img" aria-label="Healthcare professional speaking with a patient" />
            <div className="photo-caption"><span>MEDNET / PATIENT SERVICES</span><span>Tools for patients and providers</span></div>
            <div className="photo-stamp" aria-hidden="true"><span>Find</span><span>care</span></div>
          </div>
        </section>
        <section className="availability-band" id="availability">
          <div className="page-wrap availability-inner">
            <span className="availability-label">MedNet brings together</span>
            <p>Provider search, appointment requests, messages, and health information in one place.</p>
            <Link href="/services" className="availability-link">Explore services <span aria-hidden="true">-&gt;</span></Link>
          </div>
        </section>
        <section className="services-section page-wrap" id="services">
          <div className="section-heading">
            <div><p className="eyebrow">MedNet services</p><h2>Find providers. Request visits. Keep your records together.</h2></div>
            <p className="section-intro">From provider search and appointment requests to messages and health information, MedNet keeps common care tasks in one place.</p>
          </div>
          <div className="service-list">
            {services.map(([number, title, description]) => (
              <article className="service-item" key={number}>
                <span className="service-number">{number}</span><h3>{title}</h3><p>{description}</p>
              </article>
            ))}
          </div>
          <Link className="text-link service-more" href="/services">Explore all services <span aria-hidden="true">-&gt;</span></Link>
        </section>
        <section className="approach-section" id="approach">
          <div className="page-wrap approach-inner">
            <div className="approach-index"><span>YOUR PRIVACY</span><span>03 / 03</span></div>
            <div className="approach-copy">
              <h2>Your health information stays protected.</h2>
              <p>MedNet checks access in its backend. Providers only see patient information permitted by their role and care relationship; the website itself never decides who can open a record.</p>
              <Link className="approach-link" href="/about">How MedNet works <span aria-hidden="true">-&gt;</span></Link>
            </div>
            <div className="approach-aside"><span className="approach-aside-number">01</span><p>Check who is requesting access</p><span className="approach-aside-rule" /><span className="approach-aside-number approach-aside-word">02</span><p>Apply access rules in the backend</p></div>
          </div>
        </section>
        <section className="closing-section page-wrap">
          <div><p className="eyebrow">For patients and providers</p><h2>One place for the steps around care.</h2></div>
          <p>Find providers, request appointments, and keep important details together so patients and providers can follow care with less back-and-forth.</p>
        </section>
      </main>
      <SiteFooter />
    </>
  );
}

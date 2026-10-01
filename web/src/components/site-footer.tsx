import Link from "next/link";

export function SiteFooter() {
  return (
    <footer className="site-footer">
      <div className="page-wrap footer-main">
        <div>
          <Link className="wordmark footer-wordmark" href="/"><span className="wordmark-icon" aria-hidden="true">M</span><span>MedNet</span></Link>
          <p className="footer-summary">MedNet helps patients find providers, request appointments, and keep their health information together.</p>
        </div>
        <div className="footer-links"><Link href="/services">Services</Link><Link href="/about">How it works</Link><Link href="/services">Find a provider</Link></div>
      </div>
      <div className="page-wrap footer-bottom"><span>MedNet &middot; For patients and providers in Liberia</span><span>Medical advice and treatment come from healthcare professionals.</span></div>
    </footer>
  );
}
import Link from "next/link";

export function SiteHeader() {
  return (
    <header className="site-header">
      <div className="page-wrap header-inner">
        <Link className="wordmark" href="/" aria-label="MedNet home"><span className="wordmark-icon" aria-hidden="true">M</span><span>MedNet</span></Link>
        <nav className="primary-nav" aria-label="Main navigation">
          <Link href="/services">Services</Link><Link href="/about">How it works</Link>
        </nav>
        <Link className="header-status" href="/services">Explore services</Link>
      </div>
    </header>
  );
}
import { useUser, UserButton, Protect } from "@clerk/nextjs";
import Link from "next/link";
import { useRouter } from "next/router";
import { ReactNode, useState } from "react";
import PageTransition from "./PageTransition";

interface LayoutProps {
  children: ReactNode;
}

const navLinks = [
  { href: "/dashboard", label: "Dashboard" },
  { href: "/accounts", label: "Accounts" },
  { href: "/advisor-team", label: "Advisor Team" },
  { href: "/analysis", label: "Analysis" },
];

export default function Layout({ children }: LayoutProps) {
  const { user } = useUser();
  const router = useRouter();
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

  const isActive = (path: string) => router.pathname === path;

  return (
    <Protect fallback={
      <div className="min-h-screen flex items-center justify-center bg-surface">
        <div className="text-center">
          <p className="text-gray-600">Redirecting to sign in...</p>
        </div>
      </div>
    }>
      <div className="min-h-screen bg-surface flex flex-col">
        {/* Sticky Navigation */}
        <nav className="sticky top-0 z-40 bg-white/90 backdrop-blur-sm border-b border-border">
          <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
            <div className="flex justify-between items-center h-16">
              {/* Brand */}
              <Link href="/dashboard" className="flex items-center gap-2">
                <div className="w-8 h-8 rounded-lg bg-primary flex items-center justify-center">
                  <span className="text-white font-bold text-sm">A</span>
                </div>
                <span className="text-lg font-bold text-dark">
                  Alex <span className="text-primary">AI</span>
                </span>
              </Link>

              {/* Desktop Links */}
              <div className="hidden md:flex items-center gap-1">
                {navLinks.map(link => (
                  <Link
                    key={link.href}
                    href={link.href}
                    className={`relative px-4 py-2 text-sm font-medium rounded-lg transition-colors ${
                      isActive(link.href)
                        ? 'text-primary bg-primary-light'
                        : 'text-gray-600 hover:text-primary hover:bg-gray-50'
                    }`}
                  >
                    {link.label}
                    {isActive(link.href) && (
                      <span className="absolute bottom-0 left-2 right-2 h-0.5 bg-primary rounded-full" />
                    )}
                  </Link>
                ))}
              </div>

              {/* User Section */}
              <div className="flex items-center gap-3">
                <span className="hidden sm:inline text-sm text-gray-500">
                  {user?.firstName || user?.emailAddresses[0]?.emailAddress}
                </span>
                <UserButton afterSignOutUrl="/" />

                {/* Mobile hamburger */}
                <button
                  onClick={() => setMobileMenuOpen(o => !o)}
                  className="md:hidden p-2 rounded-lg hover:bg-gray-100"
                  aria-label="Toggle navigation menu"
                  aria-expanded={mobileMenuOpen}
                >
                  <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                      d={mobileMenuOpen ? 'M6 18L18 6M6 6l12 12' : 'M4 6h16M4 12h16M4 18h16'} />
                  </svg>
                </button>
              </div>
            </div>
          </div>

          {/* Mobile slide-down drawer */}
          {mobileMenuOpen && (
            <div className="md:hidden border-t border-border bg-white px-4 py-3 space-y-1">
              {navLinks.map(link => (
                <Link
                  key={link.href}
                  href={link.href}
                  onClick={() => setMobileMenuOpen(false)}
                  className={`block px-4 py-3 text-sm font-medium rounded-lg ${
                    isActive(link.href)
                      ? 'text-primary bg-primary-light'
                      : 'text-gray-600 hover:bg-gray-50'
                  }`}
                >
                  {link.label}
                </Link>
              ))}
            </div>
          )}
        </nav>

        {/* Main Content */}
        <main id="main-content" className="flex-1">
          <PageTransition>
            {children}
          </PageTransition>
        </main>

        {/* Footer */}
        <footer className="bg-white border-t border-border mt-auto">
          <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
            <div className="grid grid-cols-1 md:grid-cols-3 gap-6 items-start">
              {/* Brand */}
              <div>
                <div className="flex items-center gap-2 mb-2">
                  <div className="w-6 h-6 rounded bg-primary flex items-center justify-center">
                    <span className="text-white font-bold text-xs">A</span>
                  </div>
                  <span className="font-bold text-dark">Alex AI</span>
                </div>
                <p className="text-xs text-gray-500">
                  AI-powered portfolio analysis and retirement planning.
                </p>
              </div>

              {/* Links */}
              <div>
                <h4 className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-2">Platform</h4>
                <ul className="space-y-1.5 text-sm text-gray-600">
                  <li><Link href="/dashboard" className="hover:text-primary transition-colors">Dashboard</Link></li>
                  <li><Link href="/accounts" className="hover:text-primary transition-colors">Accounts</Link></li>
                  <li><Link href="/advisor-team" className="hover:text-primary transition-colors">Advisor Team</Link></li>
                </ul>
              </div>

              {/* Disclaimer */}
              <div className="bg-warning-light border border-yellow-200 rounded-lg p-3">
                <p className="text-xs font-semibold text-warning mb-1">Disclaimer</p>
                <p className="text-xs text-gray-600 leading-relaxed">
                  AI-generated advice. Not vetted by a licensed financial advisor.
                  For informational purposes only.
                </p>
              </div>
            </div>

            <div className="mt-6 pt-4 border-t border-border">
              <p className="text-xs text-gray-400 text-center">
                © {new Date().getFullYear()} Alex AI Financial Advisor. Built with care.
              </p>
            </div>
          </div>
        </footer>
      </div>
    </Protect>
  );
}
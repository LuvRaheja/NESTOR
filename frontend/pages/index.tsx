import { SignInButton, SignUpButton, SignedIn, SignedOut, UserButton } from "@clerk/nextjs";
import Link from "next/link";
import Head from "next/head";
import Button from "../components/ui/Button";
import Card from "../components/ui/Card";
import AgentIcon from "../components/ui/AgentIcon";

export default function Home() {
  return (
    <>
      <Head>
        <title>Alex AI Financial Advisor - Intelligent Portfolio Management</title>
      </Head>
    <div className="min-h-screen bg-surface">
      {/* Navigation */}
      <nav className="sticky top-0 z-40 bg-white/90 backdrop-blur-sm border-b border-border px-4 sm:px-8">
        <div className="max-w-7xl mx-auto flex justify-between items-center h-16">
          <Link href="/" className="flex items-center gap-2">
            <div className="w-8 h-8 rounded-lg bg-primary flex items-center justify-center">
              <span className="text-white font-bold text-sm">A</span>
            </div>
            <span className="text-lg font-bold text-dark">
              Alex <span className="text-primary">AI</span>
            </span>
          </Link>
          <div className="flex gap-3">
            <SignedOut>
              <SignInButton mode="modal">
                <Button variant="secondary" size="sm">Sign In</Button>
              </SignInButton>
              <SignUpButton mode="modal">
                <Button variant="primary" size="sm">Get Started</Button>
              </SignUpButton>
            </SignedOut>
            <SignedIn>
              <div className="flex items-center gap-4">
                <Link href="/dashboard">
                  <Button variant="primary" size="sm">Go to Dashboard</Button>
                </Link>
                <UserButton afterSignOutUrl="/" />
              </div>
            </SignedIn>
          </div>
        </div>
      </nav>

      {/* Hero Section */}
      <section className="relative overflow-hidden px-4 sm:px-8 py-24 lg:py-32">
        {/* Background decoration */}
        <div className="absolute inset-0 -z-10">
          <div className="absolute top-0 right-0 w-96 h-96 bg-primary/5 rounded-full blur-3xl" />
          <div className="absolute bottom-0 left-0 w-72 h-72 bg-ai-accent/5 rounded-full blur-3xl" />
        </div>

        <div className="max-w-5xl mx-auto text-center">
          {/* Eyebrow */}
          <div className="inline-flex items-center gap-2 px-4 py-1.5 rounded-full bg-ai-accent-light text-ai-accent text-sm font-semibold mb-6">
            <span className="w-2 h-2 rounded-full bg-ai-accent animate-ring-expand" />
            Powered by 4 Autonomous AI Agents
          </div>

          <h1 className="text-4xl sm:text-5xl lg:text-6xl font-extrabold text-dark leading-tight mb-6">
            Your AI-Powered<br />
            <span className="text-transparent bg-clip-text bg-gradient-to-r from-primary to-ai-accent">
              Financial Future
            </span>
          </h1>

          <p className="text-lg sm:text-xl text-gray-600 max-w-2xl mx-auto mb-10 leading-relaxed">
            Autonomous AI agents collaborate in real time to analyze your portfolio,
            project your retirement, and visualize your financial strategy.
          </p>

          <div className="flex flex-col sm:flex-row gap-4 justify-center">
            <SignedOut>
              <SignUpButton mode="modal">
                <Button size="lg" variant="primary">Start Your Analysis</Button>
              </SignUpButton>
            </SignedOut>
            <SignedIn>
              <Link href="/dashboard">
                <Button size="lg" variant="primary">Open Dashboard</Button>
              </Link>
            </SignedIn>
            <Button size="lg" variant="secondary">Watch Demo</Button>
          </div>
        </div>
      </section>

      {/* Features Section */}
      <section className="px-4 sm:px-8 py-20 bg-white">
        <div className="max-w-7xl mx-auto">
          <h2 className="text-3xl font-bold text-center text-dark mb-12">
            Meet Your AI Advisory Team
          </h2>
          <div className="grid md:grid-cols-2 lg:grid-cols-4 gap-6">
            {[
              { role: 'Orchestrator', name: 'Financial Planner', color: 'text-ai-accent', bgColor: 'bg-ai-accent', desc: 'Coordinates your complete financial analysis with intelligent orchestration' },
              { role: 'Reporter', name: 'Portfolio Analyst', color: 'text-primary', bgColor: 'bg-primary', desc: 'Deep analysis of holdings, performance metrics, and risk assessment' },
              { role: 'Charter', name: 'Chart Specialist', color: 'text-success', bgColor: 'bg-success', desc: 'Visualizes your portfolio composition with interactive charts' },
              { role: 'Retirement', name: 'Retirement Planner', color: 'text-accent', bgColor: 'bg-accent', desc: 'Projects your retirement readiness with Monte Carlo simulations' },
            ].map((agent) => (
              <Card key={agent.name} tier="raised" className="hover:shadow-lg transition-shadow">
                <div className="flex items-start gap-3">
                  <div className={`w-10 h-10 rounded-xl ${bgColorMap(agent.bgColor)} flex items-center justify-center shrink-0`}>
                    <AgentIcon name={agent.role} className="w-5 h-5 text-white" />
                  </div>
                  <div>
                    <h3 className={`text-lg font-semibold ${agent.color} mb-1`}>{agent.name}</h3>
                    <p className="text-sm text-gray-600">{agent.desc}</p>
                  </div>
                </div>
              </Card>
            ))}
          </div>
        </div>
      </section>

      {/* Benefits Section */}
      <section className="px-4 sm:px-8 py-20 bg-gradient-to-r from-primary/5 to-ai-accent/5">
        <div className="max-w-7xl mx-auto">
          <h2 className="text-3xl font-bold text-center text-dark mb-12">
            Enterprise-Grade AI Advisory
          </h2>
          <div className="grid md:grid-cols-3 gap-6">
            <Card tier="elevated">
              <div className="flex items-center gap-3 mb-3">
                <div className="w-10 h-10 rounded-lg bg-primary-light flex items-center justify-center">
                  <svg className="w-5 h-5 text-primary" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2">
                    <path strokeLinecap="round" strokeLinejoin="round" d="M13 10V3L4 14h7v7l9-11h-7z" />
                  </svg>
                </div>
                <h3 className="text-lg font-semibold text-dark">Real-Time Analysis</h3>
              </div>
              <p className="text-gray-600 text-sm">Watch AI agents collaborate in parallel to analyze your complete financial picture</p>
            </Card>
            <Card tier="elevated">
              <div className="flex items-center gap-3 mb-3">
                <div className="w-10 h-10 rounded-lg bg-primary-light flex items-center justify-center">
                  <svg className="w-5 h-5 text-primary" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2">
                    <path strokeLinecap="round" strokeLinejoin="round" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
                  </svg>
                </div>
                <h3 className="text-lg font-semibold text-dark">Bank-Level Security</h3>
              </div>
              <p className="text-gray-600 text-sm">Your data is protected with enterprise security and row-level access controls</p>
            </Card>
            <Card tier="elevated">
              <div className="flex items-center gap-3 mb-3">
                <div className="w-10 h-10 rounded-lg bg-primary-light flex items-center justify-center">
                  <svg className="w-5 h-5 text-primary" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2">
                    <path strokeLinecap="round" strokeLinejoin="round" d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
                  </svg>
                </div>
                <h3 className="text-lg font-semibold text-dark">Comprehensive Reports</h3>
              </div>
              <p className="text-gray-600 text-sm">Detailed markdown reports with interactive charts and retirement projections</p>
            </Card>
          </div>
        </div>
      </section>

      {/* CTA Section */}
      <section className="px-4 sm:px-8 py-20 bg-dark text-white">
        <div className="max-w-4xl mx-auto text-center">
          <h2 className="text-3xl font-bold mb-6">
            Ready to Transform Your Financial Future?
          </h2>
          <p className="text-xl mb-8 opacity-90">
            Join thousands of investors using AI to optimize their portfolios
          </p>
          <SignUpButton mode="modal">
            <Button size="lg" className="bg-accent-fill text-dark font-semibold hover:bg-yellow-500">
              Get Started Free
            </Button>
          </SignUpButton>
        </div>
      </section>

      {/* Footer */}
      <footer className="px-4 sm:px-8 py-6 bg-gray-900 text-gray-400 text-center text-sm">
        <p>© {new Date().getFullYear()} Alex AI Financial Advisor. All rights reserved.</p>
        <p className="mt-2 text-xs">
          This AI-generated advice has not been vetted by a qualified financial advisor and should not be used for trading decisions.
          For informational purposes only.
        </p>
      </footer>
    </div>
    </>
  );
}

function bgColorMap(bg: string): string {
  // Map semantic token names to actual Tailwind classes for the icon containers
  const map: Record<string, string> = {
    'bg-ai-accent': 'bg-ai-accent',
    'bg-primary': 'bg-primary',
    'bg-success': 'bg-success',
    'bg-accent': 'bg-accent',
  };
  return map[bg] || bg;
}
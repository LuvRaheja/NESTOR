#!/usr/bin/env python3
"""
Run both frontend and backend locally for development.
This script starts the NextJS frontend and the NESTOR Java API backend locally.

For local development, the Java API should be running separately (e.g. via Spring Boot),
or you can point the frontend at the deployed NESTOR API Gateway URL.
"""

import os
import sys
import subprocess
import signal
import time
from pathlib import Path

processes = []

def cleanup(signum=None, frame=None):
    """Clean up all subprocess on exit"""
    print("\n🛑 Shutting down services...")
    for proc in processes:
        try:
            proc.terminate()
            proc.wait(timeout=5)
        except:
            proc.kill()
    sys.exit(0)

signal.signal(signal.SIGINT, cleanup)
signal.signal(signal.SIGTERM, cleanup)

def check_requirements():
    """Check if required tools are installed"""
    checks = []

    try:
        result = subprocess.run(["node", "--version"], capture_output=True, text=True)
        checks.append(f"✅ Node.js: {result.stdout.strip()}")
    except FileNotFoundError:
        checks.append("❌ Node.js not found - please install Node.js")

    try:
        result = subprocess.run(["npm", "--version"], capture_output=True, text=True, shell=True)
        checks.append(f"✅ npm: {result.stdout.strip()}")
    except FileNotFoundError:
        checks.append("❌ npm not found - please install npm")

    print("\n📋 Prerequisites Check:")
    for check in checks:
        print(f"  {check}")

    if any("❌" in check for check in checks):
        print("\n⚠️  Please install missing dependencies and try again.")
        sys.exit(1)

def check_env_files():
    """Check if environment files exist"""
    project_root = Path(__file__).parent.parent

    root_env = project_root / ".env"
    frontend_env = project_root / "frontend" / ".env.local"

    missing = []

    if not root_env.exists():
        missing.append("NESTOR/.env (root project file)")
    if not frontend_env.exists():
        missing.append("NESTOR/frontend/.env.local")

    if missing:
        print("\n⚠️  Missing environment files:")
        for file in missing:
            print(f"  - {file}")
        print("\nPlease create these files with the required configuration.")
        sys.exit(1)

    print("✅ Environment files found")

def start_frontend():
    """Start the NextJS frontend"""
    frontend_dir = Path(__file__).parent.parent / "frontend"

    print("\n🚀 Starting NextJS frontend...")

    if not (frontend_dir / "node_modules").exists():
        print("  Installing frontend dependencies...")
        subprocess.run(["npm", "install"], cwd=frontend_dir, check=True, shell=True)

    proc = subprocess.Popen(
        ["npm", "run", "dev"],
        cwd=frontend_dir,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        bufsize=1,
        shell=True
    )
    processes.append(proc)

    print("  Waiting for frontend to start...")
    import httpx

    for i in range(30):
        if proc.poll() is not None:
            print("  ❌ Frontend process exited unexpectedly")
            break

        if i > 3:
            try:
                response = httpx.get("http://localhost:3000", timeout=1)
                print("  ✅ Frontend running at http://localhost:3000")
                return proc
            except httpx.ConnectError:
                pass
            except:
                print("  ✅ Frontend running at http://localhost:3000")
                return proc

        time.sleep(1)

    print("  ❌ Frontend failed to start")
    cleanup()

def monitor_processes():
    """Monitor running processes and show their output"""
    print("\n" + "="*60)
    print("🎯 NESTOR Financial Advisor - Local Development")
    print("="*60)
    print("\n📍 Services:")
    print("  Frontend: http://localhost:3000")
    print(f"\n📝 Note: The frontend will connect to the API URL configured in")
    print(f"   NESTOR/frontend/.env.local (NEXT_PUBLIC_API_URL)")
    print(f"\n   For local Java API, run Spring Boot separately and set")
    print(f"   NEXT_PUBLIC_API_URL=http://localhost:8080 in .env.local")
    print(f"\n   For deployed API, set NEXT_PUBLIC_API_URL to your API Gateway URL")
    print("\n📝 Logs will appear below. Press Ctrl+C to stop.\n")
    print("="*60 + "\n")

    while True:
        for proc in processes:
            if proc.poll() is not None:
                print(f"\n⚠️  Frontend process has stopped unexpectedly! (exit code: {proc.returncode})")
                if proc.stdout:
                    remaining = proc.stdout.read()
                    if remaining:
                        print(remaining)
                cleanup()

            try:
                line = proc.stdout.readline()
                if line:
                    print(f"[Frontend] {line.strip()}")
            except:
                pass

        time.sleep(0.1)

def main():
    """Main entry point"""
    print("\n🔧 NESTOR Financial Advisor - Local Development Setup")
    print("="*50)

    check_requirements()
    check_env_files()

    try:
        import httpx
    except ImportError:
        print("\n📦 Installing httpx for health checks...")
        subprocess.run(["uv", "add", "httpx"], check=True)

    frontend_proc = start_frontend()

    try:
        monitor_processes()
    except KeyboardInterrupt:
        cleanup()

if __name__ == "__main__":
    main()

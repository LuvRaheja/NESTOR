#!/usr/bin/env python3
"""
Deploy the NESTOR Financial Advisor Part 7 infrastructure.
This script:
1. Builds and pushes the Java API container image to ECR
2. Deploys infrastructure with Terraform to get API URL
3. Builds the NextJS frontend with production API URL
4. Uploads frontend files to S3
5. Invalidates CloudFront cache

NOTE: This script uses .env.production for deployment and does NOT modify .env.local
"""

import subprocess
import sys
import os
import json
import time
from pathlib import Path


def run_command(cmd, cwd=None, check=True, capture_output=False, env=None):
    """Run a command and optionally capture output."""
    print(f"Running: {' '.join(cmd) if isinstance(cmd, list) else cmd}")

    if capture_output:
        result = subprocess.run(cmd, cwd=cwd, capture_output=True, text=True, shell=isinstance(cmd, str), env=env)
        if check and result.returncode != 0:
            print(f"Error: {result.stderr}")
            sys.exit(1)
        return result.stdout.strip()
    else:
        result = subprocess.run(cmd, cwd=cwd, shell=isinstance(cmd, str), env=env)
        if check and result.returncode != 0:
            sys.exit(1)
        return None


def check_prerequisites():
    """Check that all required tools are installed."""
    print("🔍 Checking prerequisites...")

    tools = {
        "docker": "Docker is required for building container images",
        "terraform": "Terraform is required for infrastructure deployment",
        "npm": "npm is required for building the frontend",
        "aws": "AWS CLI is required for S3 sync and CloudFront invalidation",
        "mvn": "Maven is required for building the Java API"
    }

    for tool, message in tools.items():
        try:
            run_command([tool, "--version"], capture_output=True)
            print(f"  ✅ {tool} is installed")
        except (subprocess.CalledProcessError, FileNotFoundError):
            print(f"  ❌ {message}")
            sys.exit(1)

    # Check if Docker is running
    try:
        run_command(["docker", "info"], capture_output=True)
        print("  ✅ Docker is running")
    except subprocess.CalledProcessError:
        print("  ❌ Docker is not running. Please start Docker Desktop.")
        sys.exit(1)

    # Check AWS credentials
    try:
        run_command(["aws", "sts", "get-caller-identity"], capture_output=True)
        print("  ✅ AWS credentials configured")
    except subprocess.CalledProcessError:
        print("  ❌ AWS credentials not configured. Run 'aws configure'")
        sys.exit(1)


def get_aws_account_and_region():
    """Get the AWS account ID and region from the current credentials."""
    identity = run_command(
        ["aws", "sts", "get-caller-identity", "--output", "json"],
        capture_output=True
    )
    account_id = json.loads(identity)["Account"]

    region = run_command(
        ["aws", "configure", "get", "region"],
        capture_output=True,
        check=False
    )
    if not region:
        region = "us-east-1"

    return account_id, region


def build_and_push_api(account_id, region):
    """Build the Java API and push container image to ECR."""
    print("\n📦 Building Java API container image...")

    nestor_root = Path(__file__).parent.parent
    api_dir = nestor_root / "backend" / "api"

    if not api_dir.exists():
        print(f"  ❌ API directory not found: {api_dir}")
        sys.exit(1)

    # Build the JAR with Maven
    print("  Building JAR with Maven...")
    run_command(["mvn", "clean", "package", "-pl", "backend/api", "-am", "-DskipTests"], cwd=nestor_root)

    # Build Docker image
    print("  Building Docker image...")
    run_command([
        "docker", "build", "--platform", "linux/amd64", "--provenance=false",
        "-t", "nestor-api", "."
    ], cwd=api_dir)

    # ECR login
    ecr_registry = f"{account_id}.dkr.ecr.{region}.amazonaws.com"
    print(f"  Logging into ECR: {ecr_registry}...")
    password = run_command(
        ["aws", "ecr", "get-login-password", "--region", region],
        capture_output=True
    )
    run_command(
        f"echo {password} | docker login --username AWS --password-stdin {ecr_registry}",
        capture_output=True
    )

    # Tag and push
    ecr_uri = f"{ecr_registry}/nestor-api:latest"
    print(f"  Tagging and pushing to: {ecr_uri}...")
    run_command(["docker", "tag", "nestor-api:latest", ecr_uri])
    run_command(["docker", "push", ecr_uri])

    print(f"  ✅ API container image pushed to ECR")

    # Update Lambda function code
    print("  Updating Lambda function code...")
    run_command([
        "aws", "lambda", "update-function-code",
        "--function-name", "nestor-api",
        "--image-uri", ecr_uri,
        "--region", region
    ], capture_output=True, check=False)

    return ecr_uri


def build_frontend(api_url=None):
    """Build the NextJS frontend."""
    print("\n🎨 Building frontend...")

    frontend_dir = Path(__file__).parent.parent / "frontend"

    if not frontend_dir.exists():
        print(f"  ❌ Frontend directory not found: {frontend_dir}")
        sys.exit(1)

    # Install dependencies if needed
    node_modules = frontend_dir / "node_modules"
    if not node_modules.exists():
        print("  Installing dependencies...")
        run_command(["npm", "install"], cwd=frontend_dir)

    # If API URL is provided, create .env.production.local to override .env.local
    if api_url:
        print(f"  Creating .env.production.local with API URL: {api_url}")
        env_prod_local = frontend_dir / ".env.production.local"

        env_prod = frontend_dir / ".env.production"
        if env_prod.exists():
            with open(env_prod, "r") as f:
                lines = f.readlines()
        else:
            env_local = frontend_dir / ".env.local"
            if env_local.exists():
                with open(env_local, "r") as f:
                    lines = f.readlines()
            else:
                lines = []

        api_line_found = False
        for i, line in enumerate(lines):
            if line.startswith("NEXT_PUBLIC_API_URL="):
                lines[i] = f"NEXT_PUBLIC_API_URL={api_url}\n"
                api_line_found = True
                break

        if not api_line_found:
            lines.append(f"\nNEXT_PUBLIC_API_URL={api_url}\n")

        with open(env_prod_local, "w") as f:
            f.writelines(lines)
        print("  ✅ Created .env.production.local with API URL")

    # Build the frontend
    print("  Building NextJS app for production...")
    build_env = os.environ.copy()
    build_env["NODE_ENV"] = "production"
    run_command(["npm", "run", "build"], cwd=frontend_dir, env=build_env)

    out_dir = frontend_dir / "out"
    if not out_dir.exists():
        print(f"  ❌ Build output not found: {out_dir}")
        print("  Make sure next.config.ts has output: 'export'")
        sys.exit(1)

    print(f"  ✅ Frontend built successfully")


def deploy_terraform():
    """Deploy infrastructure with Terraform."""
    print("\n🏗️  Deploying infrastructure with Terraform...")

    terraform_dir = Path(__file__).parent.parent / "terraform" / "7_frontend"

    if not terraform_dir.exists():
        print(f"  ❌ Terraform directory not found: {terraform_dir}")
        sys.exit(1)

    if not (terraform_dir / ".terraform").exists():
        print("  Initializing Terraform...")
        run_command(["terraform", "init"], cwd=terraform_dir)

    print("  Planning deployment...")
    run_command(["terraform", "plan"], cwd=terraform_dir)

    print("\n  Applying deployment...")
    run_command(["terraform", "apply", "-auto-approve"], cwd=terraform_dir)

    print("\n  Getting outputs...")
    outputs = run_command(
        ["terraform", "output", "-json"],
        cwd=terraform_dir,
        capture_output=True
    )

    return json.loads(outputs)


def upload_frontend(bucket_name, cloudfront_id):
    """Upload frontend files to S3."""
    print(f"\n📤 Uploading frontend to S3 bucket: {bucket_name}")

    frontend_dir = Path(__file__).parent.parent / "frontend" / "out"

    if not frontend_dir.exists():
        print(f"  ❌ Frontend build not found: {frontend_dir}")
        sys.exit(1)

    print("  Clearing S3 bucket...")
    run_command(["aws", "s3", "rm", f"s3://{bucket_name}/", "--recursive"])

    print("  Uploading HTML files...")
    run_command([
        "aws", "s3", "cp", str(frontend_dir) + "/", f"s3://{bucket_name}/",
        "--recursive", "--exclude", "*", "--include", "*.html",
        "--content-type", "text/html",
        "--cache-control", "max-age=0,no-cache,no-store,must-revalidate"
    ])

    print("  Uploading CSS files...")
    run_command([
        "aws", "s3", "cp", str(frontend_dir) + "/", f"s3://{bucket_name}/",
        "--recursive", "--exclude", "*", "--include", "*.css",
        "--content-type", "text/css", "--cache-control", "max-age=31536000,public"
    ])

    print("  Uploading JavaScript files...")
    run_command([
        "aws", "s3", "cp", str(frontend_dir) + "/", f"s3://{bucket_name}/",
        "--recursive", "--exclude", "*", "--include", "*.js",
        "--content-type", "application/javascript", "--cache-control", "max-age=31536000,public"
    ])

    print("  Uploading JSON files...")
    run_command([
        "aws", "s3", "cp", str(frontend_dir) + "/", f"s3://{bucket_name}/",
        "--recursive", "--exclude", "*", "--include", "*.json",
        "--content-type", "application/json", "--cache-control", "max-age=31536000,public"
    ])

    for ext, content_type in [
        ("*.png", "image/png"), ("*.jpg", "image/jpeg"), ("*.jpeg", "image/jpeg"),
        ("*.gif", "image/gif"), ("*.svg", "image/svg+xml"), ("*.ico", "image/x-icon")
    ]:
        run_command([
            "aws", "s3", "cp", str(frontend_dir) + "/", f"s3://{bucket_name}/",
            "--recursive", "--exclude", "*", "--include", ext,
            "--content-type", content_type, "--cache-control", "max-age=31536000,public"
        ])

    print("  Uploading remaining files...")
    run_command([
        "aws", "s3", "sync", str(frontend_dir) + "/", f"s3://{bucket_name}/",
        "--cache-control", "max-age=31536000,public"
    ])

    print(f"  ✅ Frontend uploaded successfully")

    print(f"\n🔄 Invalidating CloudFront cache...")
    run_command([
        "aws", "cloudfront", "create-invalidation",
        "--distribution-id", cloudfront_id, "--paths", "/*"
    ], capture_output=True)

    print(f"  ✅ CloudFront invalidation created")


def display_deployment_info(outputs):
    """Display deployment information."""
    print("\n📝 Deployment Information")

    api_url = outputs["api_gateway_url"]["value"]
    cloudfront_url = outputs["cloudfront_url"]["value"]

    print(f"\n  ✅ Deployment successful!")
    print(f"\n  CloudFront URL: {cloudfront_url}")
    print(f"  API Gateway URL: {api_url}")
    print(f"\n  Note: Your local .env.local file remains unchanged.")
    print(f"  The production build uses .env.production with the AWS API URL.")


def main():
    """Main deployment function."""
    print("🚀 NESTOR Financial Advisor - Part 7 Deployment")
    print("=" * 50)

    check_prerequisites()

    account_id, region = get_aws_account_and_region()
    print(f"\n  AWS Account: {account_id}, Region: {region}")

    build_and_push_api(account_id, region)

    outputs = deploy_terraform()

    api_url = outputs["api_gateway_url"]["value"]

    build_frontend(api_url)

    cloudfront_url = outputs["cloudfront_url"]["value"]
    dist_id_output = run_command([
        "aws", "cloudfront", "list-distributions",
        "--query", f"DistributionList.Items[?DomainName=='{cloudfront_url.replace('https://', '')}'].Id",
        "--output", "text"
    ], capture_output=True)

    if not dist_id_output:
        print("  ⚠️  Could not find CloudFront distribution ID")
        cloudfront_id = None
    else:
        cloudfront_id = dist_id_output

    bucket_name = outputs["s3_bucket_name"]["value"]
    if cloudfront_id:
        upload_frontend(bucket_name, cloudfront_id)
    else:
        print("\n📤 Uploading frontend to S3...")
        run_command([
            "aws", "s3", "sync",
            str(Path(__file__).parent.parent / "frontend" / "out") + "/",
            f"s3://{bucket_name}/", "--delete"
        ])

    display_deployment_info(outputs)

    print("\n" + "=" * 50)
    print("✅ Deployment complete!")
    print(f"\n🌐 Your application is available at:")
    print(f"   {outputs['cloudfront_url']['value']}")
    print(f"\n📊 Monitor your Lambda function at:")
    print(f"   AWS Console > Lambda > {outputs['api_lambda_function_name']['value']}")
    print("\n⏳ Note: CloudFront distribution may take 5-10 minutes to fully propagate")


if __name__ == "__main__":
    main()
#!/usr/bin/env python3
"""
Deploy the Alex Financial Advisor Part 7 infrastructure.
This script:
1. Packages the Lambda function
2. Deploys infrastructure with Terraform to get API URL
3. Builds the NextJS frontend with production API URL
4. Uploads frontend files to S3
5. Invalidates CloudFront cache

NOTE: This script uses .env.production for deployment and does NOT modify .env.local
"""

import subprocess
import sys
import os
import json
import time
from pathlib import Path


def run_command(cmd, cwd=None, check=True, capture_output=False, env=None):
    """Run a command and optionally capture output."""
    print(f"Running: {' '.join(cmd) if isinstance(cmd, list) else cmd}")

    if capture_output:
        result = subprocess.run(cmd, cwd=cwd, capture_output=True, text=True, shell=isinstance(cmd, str), env=env)
        if check and result.returncode != 0:
            print(f"Error: {result.stderr}")
            sys.exit(1)
        return result.stdout.strip()
    else:
        result = subprocess.run(cmd, cwd=cwd, shell=isinstance(cmd, str), env=env)
        if check and result.returncode != 0:
            sys.exit(1)
        return None


def check_prerequisites():
    """Check that all required tools are installed."""
    print("🔍 Checking prerequisites...")

    # Check for required tools
    tools = {
        "docker": "Docker is required for Lambda packaging",
        "terraform": "Terraform is required for infrastructure deployment",
        "npm": "npm is required for building the frontend",
        "aws": "AWS CLI is required for S3 sync and CloudFront invalidation"
    }

    for tool, message in tools.items():
        try:
            run_command([tool, "--version"], capture_output=True)
            print(f"  ✅ {tool} is installed")
        except (subprocess.CalledProcessError, FileNotFoundError):
            print(f"  ❌ {message}")
            sys.exit(1)

    # Check if Docker is running
    try:
        run_command(["docker", "info"], capture_output=True)
        print("  ✅ Docker is running")
    except subprocess.CalledProcessError:
        print("  ❌ Docker is not running. Please start Docker Desktop.")
        sys.exit(1)

    # Check AWS credentials
    try:
        run_command(["aws", "sts", "get-caller-identity"], capture_output=True)
        print("  ✅ AWS credentials configured")
    except subprocess.CalledProcessError:
        print("  ❌ AWS credentials not configured. Run 'aws configure'")
        sys.exit(1)


def package_lambda():
    """Package the Lambda function using Docker."""
    print("\n📦 Packaging Lambda function...")

    api_dir = Path(__file__).parent.parent / "backend" / "api"

    if not api_dir.exists():
        print(f"  ❌ API directory not found: {api_dir}")
        sys.exit(1)

    # Run the packaging script
    run_command(["uv", "run", "package_docker.py"], cwd=api_dir)

    # Verify the package was created
    lambda_zip = api_dir / "api_lambda.zip"
    if not lambda_zip.exists():
        print(f"  ❌ Lambda package not created: {lambda_zip}")
        sys.exit(1)

    size_mb = lambda_zip.stat().st_size / (1024 * 1024)
    print(f"  ✅ Lambda package created: {lambda_zip} ({size_mb:.2f} MB)")


def build_frontend(api_url=None):
    """Build the NextJS frontend."""
    print("\n🎨 Building frontend...")

    frontend_dir = Path(__file__).parent.parent / "frontend"

    if not frontend_dir.exists():
        print(f"  ❌ Frontend directory not found: {frontend_dir}")
        sys.exit(1)

    # Install dependencies if needed
    node_modules = frontend_dir / "node_modules"
    if not node_modules.exists():
        print("  Installing dependencies...")
        run_command(["npm", "install"], cwd=frontend_dir)

    # If API URL is provided, create .env.production.local to override .env.local
    if api_url:
        print(f"  Creating .env.production.local with API URL: {api_url}")
        env_prod_local = frontend_dir / ".env.production.local"

        # Copy from .env.production as base
        env_prod = frontend_dir / ".env.production"
        if env_prod.exists():
            with open(env_prod, "r") as f:
                lines = f.readlines()
        else:
            # Fallback to .env.local if .env.production doesn't exist
            env_local = frontend_dir / ".env.local"
            if env_local.exists():
                with open(env_local, "r") as f:
                    lines = f.readlines()
            else:
                lines = []

        # Update the API URL
        api_line_found = False
        for i, line in enumerate(lines):
            if line.startswith("NEXT_PUBLIC_API_URL="):
                lines[i] = f"NEXT_PUBLIC_API_URL={api_url}\n"
                api_line_found = True
                break

        if not api_line_found:
            lines.append(f"\nNEXT_PUBLIC_API_URL={api_url}\n")

        # Write to .env.production.local (highest priority for production builds)
        with open(env_prod_local, "w") as f:
            f.writelines(lines)
        print("  ✅ Created .env.production.local with API URL")

    # Build the frontend - NextJS will automatically use .env.production for production builds
    print("  Building NextJS app for production...")
    # Set NODE_ENV to production to ensure .env.production is used
    build_env = os.environ.copy()
    build_env["NODE_ENV"] = "production"
    run_command(["npm", "run", "build"], cwd=frontend_dir, env=build_env)

    # Verify the build
    out_dir = frontend_dir / "out"
    if not out_dir.exists():
        print(f"  ❌ Build output not found: {out_dir}")
        print("  Make sure next.config.ts has output: 'export'")
        sys.exit(1)

    print(f"  ✅ Frontend built successfully")


def deploy_terraform():
    """Deploy infrastructure with Terraform."""
    print("\n🏗️  Deploying infrastructure with Terraform...")

    terraform_dir = Path(__file__).parent.parent / "terraform" / "7_frontend"

    if not terraform_dir.exists():
        print(f"  ❌ Terraform directory not found: {terraform_dir}")
        sys.exit(1)

    # Initialize Terraform if needed
    if not (terraform_dir / ".terraform").exists():
        print("  Initializing Terraform...")
        run_command(["terraform", "init"], cwd=terraform_dir)

    # Plan the deployment
    print("  Planning deployment...")
    run_command(["terraform", "plan"], cwd=terraform_dir)

    # Apply the deployment
    print("\n  Applying deployment...")
    print("  Creating AWS resources...")
    run_command(["terraform", "apply", "-auto-approve"], cwd=terraform_dir)

    # Get outputs
    print("\n  Getting outputs...")
    outputs = run_command(
        ["terraform", "output", "-json"],
        cwd=terraform_dir,
        capture_output=True
    )

    return json.loads(outputs)


def upload_frontend(bucket_name, cloudfront_id):
    """Upload frontend files to S3."""
    print(f"\n📤 Uploading frontend to S3 bucket: {bucket_name}")

    frontend_dir = Path(__file__).parent.parent / "frontend" / "out"

    if not frontend_dir.exists():
        print(f"  ❌ Frontend build not found: {frontend_dir}")
        sys.exit(1)

    # First, clear the bucket
    print("  Clearing S3 bucket...")
    run_command([
        "aws", "s3", "rm",
        f"s3://{bucket_name}/",
        "--recursive"
    ])

    # Upload HTML files with correct content type and no-cache
    print("  Uploading HTML files...")
    run_command([
        "aws", "s3", "cp",
        str(frontend_dir) + "/",
        f"s3://{bucket_name}/",
        "--recursive",
        "--exclude", "*",
        "--include", "*.html",
        "--content-type", "text/html",
        "--cache-control", "max-age=0,no-cache,no-store,must-revalidate"
    ])

    # Upload CSS files
    print("  Uploading CSS files...")
    run_command([
        "aws", "s3", "cp",
        str(frontend_dir) + "/",
        f"s3://{bucket_name}/",
        "--recursive",
        "--exclude", "*",
        "--include", "*.css",
        "--content-type", "text/css",
        "--cache-control", "max-age=31536000,public"
    ])

    # Upload JS files
    print("  Uploading JavaScript files...")
    run_command([
        "aws", "s3", "cp",
        str(frontend_dir) + "/",
        f"s3://{bucket_name}/",
        "--recursive",
        "--exclude", "*",
        "--include", "*.js",
        "--content-type", "application/javascript",
        "--cache-control", "max-age=31536000,public"
    ])

    # Upload JSON files
    print("  Uploading JSON files...")
    run_command([
        "aws", "s3", "cp",
        str(frontend_dir) + "/",
        f"s3://{bucket_name}/",
        "--recursive",
        "--exclude", "*",
        "--include", "*.json",
        "--content-type", "application/json",
        "--cache-control", "max-age=31536000,public"
    ])

    # Upload images
    for ext, content_type in [
        ("*.png", "image/png"),
        ("*.jpg", "image/jpeg"),
        ("*.jpeg", "image/jpeg"),
        ("*.gif", "image/gif"),
        ("*.svg", "image/svg+xml"),
        ("*.ico", "image/x-icon")
    ]:
        run_command([
            "aws", "s3", "cp",
            str(frontend_dir) + "/",
            f"s3://{bucket_name}/",
            "--recursive",
            "--exclude", "*",
            "--include", ext,
            "--content-type", content_type,
            "--cache-control", "max-age=31536000,public"
        ])

    # Upload any remaining files with generic content type
    print("  Uploading remaining files...")
    run_command([
        "aws", "s3", "sync",
        str(frontend_dir) + "/",
        f"s3://{bucket_name}/",
        "--cache-control", "max-age=31536000,public"
    ])

    print(f"  ✅ Frontend uploaded successfully")

    # Invalidate CloudFront cache
    print(f"\n🔄 Invalidating CloudFront cache...")
    result = run_command([
        "aws", "cloudfront", "create-invalidation",
        "--distribution-id", cloudfront_id,
        "--paths", "/*"
    ], capture_output=True)

    print(f"  ✅ CloudFront invalidation created")


def display_deployment_info(outputs):
    """Display deployment information without modifying local env files."""
    print("\n📝 Deployment Information")

    # Extract values from outputs
    api_url = outputs["api_gateway_url"]["value"]
    cloudfront_url = outputs["cloudfront_url"]["value"]

    print(f"\n  ✅ Deployment successful!")
    print(f"\n  CloudFront URL: {cloudfront_url}")
    print(f"  API Gateway URL: {api_url}")
    print(f"\n  Note: Your local .env.local file remains unchanged.")
    print(f"  The production build uses .env.production with the AWS API URL.")


def main():
    """Main deployment function."""
    print("🚀 Alex Financial Advisor - Part 7 Deployment")
    print("=" * 50)

    # Check prerequisites
    check_prerequisites()

    # Package Lambda
    package_lambda()

    # Deploy infrastructure first to get the API URL
    outputs = deploy_terraform()

    # Get the API URL from terraform outputs
    api_url = outputs["api_gateway_url"]["value"]

    # Build frontend with the production API URL
    build_frontend(api_url)

    # Extract CloudFront distribution ID
    cloudfront_url = outputs["cloudfront_url"]["value"]
    # Extract distribution ID from CloudFront URL
    dist_id_output = run_command([
        "aws", "cloudfront", "list-distributions",
        "--query", f"DistributionList.Items[?DomainName=='{cloudfront_url.replace('https://', '')}'].Id",
        "--output", "text"
    ], capture_output=True)

    if not dist_id_output:
        print("  ⚠️  Could not find CloudFront distribution ID")
        print("  You'll need to manually invalidate the cache")
        cloudfront_id = None
    else:
        cloudfront_id = dist_id_output

    # Upload frontend
    bucket_name = outputs["s3_bucket_name"]["value"]
    if cloudfront_id:
        upload_frontend(bucket_name, cloudfront_id)
    else:
        print("\n📤 Uploading frontend to S3...")
        run_command([
            "aws", "s3", "sync",
            str(Path(__file__).parent.parent / "frontend" / "out") + "/",
            f"s3://{bucket_name}/",
            "--delete"
        ])

    # Display deployment info (no longer modifies .env.local)
    display_deployment_info(outputs)

    print("\n" + "=" * 50)
    print("✅ Deployment complete!")
    print(f"\n🌐 Your application is available at:")
    print(f"   {outputs['cloudfront_url']['value']}")
    print(f"\n📊 Monitor your Lambda function at:")
    print(f"   AWS Console > Lambda > {outputs['lambda_function_name']['value']}")
    print("\n⏳ Note: CloudFront distribution may take 5-10 minutes to fully propagate")


if __name__ == "__main__":
    main()
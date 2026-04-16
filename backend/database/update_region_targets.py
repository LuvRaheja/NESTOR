"""Update region_targets for Indian users from north_america to india."""
import os
import boto3
from dotenv import load_dotenv

load_dotenv(override=True)

cluster_arn = os.environ.get("AURORA_CLUSTER_ARN")
secret_arn = os.environ.get("AURORA_SECRET_ARN")
database = os.environ.get("AURORA_DATABASE", "alex")
region = os.environ.get("DEFAULT_AWS_REGION", "us-east-1")

client = boto3.client("rds-data", region_name=region)

# Update all Indian users' region_targets: north_america -> india
sql = """
UPDATE users
SET region_targets = jsonb_set(
    region_targets - 'north_america',
    '{india}',
    COALESCE(region_targets->'north_america', '50')
)
WHERE country_code = 'IN'
  AND region_targets ? 'north_america'
"""

response = client.execute_statement(
    resourceArn=cluster_arn, secretArn=secret_arn, database=database, sql=sql
)
print(f"Updated {response['numberOfRecordsUpdated']} user(s)")

# Verify
sql2 = "SELECT clerk_user_id, country_code, region_targets, asset_class_targets FROM users WHERE country_code = 'IN'"
result = client.execute_statement(
    resourceArn=cluster_arn, secretArn=secret_arn, database=database, sql=sql2
)
for row in result.get("records", []):
    print(f"User: {row[0].get('stringValue')}")
    print(f"  Country: {row[1].get('stringValue')}")
    print(f"  Region targets: {row[2].get('stringValue')}")
    print(f"  Asset class targets: {row[3].get('stringValue')}")

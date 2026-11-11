terraform {
  required_version = ">= 1.10"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.72"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.9"
    }
  }

  # Partial configuration: bucket/key come from backend.hcl (see backend.hcl.example), so the
  # same code can target another account or environment. use_lockfile = S3-native locking.
  backend "s3" {}
}

provider "aws" {
  region = var.region
  default_tags {
    tags = { Project = "lms", Environment = var.environment, ManagedBy = "terraform" }
  }
}

# CloudFront certificates must be issued in us-east-1.
provider "aws" {
  alias  = "us_east_1"
  region = "us-east-1"
  default_tags {
    tags = { Project = "lms", Environment = var.environment, ManagedBy = "terraform" }
  }
}

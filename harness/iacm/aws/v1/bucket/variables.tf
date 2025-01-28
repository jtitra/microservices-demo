//Define Valid Variables

// AWS Environment
variable "aws_region" {
  type    = string
  default = "us-east-1"
}

// S3 Bucket
variable "bucket_name" {
  type = string
}

variable "bucket_owner" {
  type = string
}

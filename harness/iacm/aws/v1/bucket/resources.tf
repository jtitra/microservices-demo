// Define the resources to create
// Provisions the following into AWS: 
//    S3 Bucket

// S3 Bucket
resource "aws_s3_bucket" "se_bucket" {
  bucket = var.bucket_name

  tags = {
    Name        = var.bucket_name
    Owner       = var.bucket_owner
    Environment = "se-demo"
  }
}

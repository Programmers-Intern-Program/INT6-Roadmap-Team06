variable "aws_region" {
  description = "AWS region for the demo deployment."
  type        = string
  default     = "ap-northeast-2"
}

variable "project_name" {
  description = "Short project name used for resource names."
  type        = string
  default     = "coach"
}

variable "environment" {
  description = "Deployment environment label."
  type        = string
  default     = "demo"
}

variable "instance_type" {
  description = "EC2 instance type for the single-node demo deployment."
  type        = string
  default     = "t3.small"
}

variable "key_name" {
  description = "Existing EC2 key pair name for SSH access. Leave null when using SSM only."
  type        = string
  default     = null
}

variable "vpc_id" {
  description = "VPC id. Leave null to use the default VPC."
  type        = string
  default     = null
}

variable "subnet_id" {
  description = "Subnet id. Leave null to use the first subnet in the selected VPC."
  type        = string
  default     = null
}

variable "allowed_ssh_cidr" {
  description = "CIDR allowed to SSH into the EC2 instance."
  type        = string
  default     = "0.0.0.0/0"
}

variable "allowed_http_cidr" {
  description = "CIDR allowed to access demo app/API ports."
  type        = string
  default     = "0.0.0.0/0"
}

variable "app_blue_port" {
  description = "Host port for frontend blue container."
  type        = number
  default     = 3001
}

variable "app_green_port" {
  description = "Host port for frontend green container."
  type        = number
  default     = 3002
}

variable "api_blue_port" {
  description = "Host port for backend blue container."
  type        = number
  default     = 8081
}

variable "api_green_port" {
  description = "Host port for backend green container."
  type        = number
  default     = 8082
}

variable "ssh_port" {
  description = "SSH port exposed on the EC2 instance."
  type        = number
  default     = 22
}

variable "root_volume_size_gb" {
  description = "Root EBS volume size in GiB."
  type        = number
  default     = 30
}

variable "tags" {
  description = "Additional tags applied to all resources."
  type        = map(string)
  default     = {}
}

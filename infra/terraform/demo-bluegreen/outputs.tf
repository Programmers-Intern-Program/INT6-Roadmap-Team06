output "instance_id" {
  description = "EC2 instance id."
  value       = aws_instance.demo.id
}

output "elastic_ip" {
  description = "Elastic IP address for DNS connection."
  value       = aws_eip.demo.public_ip
}

output "ssh_host" {
  description = "SSH host command target."
  value       = "ubuntu@${aws_eip.demo.public_ip}"
}

output "frontend_blue_url" {
  description = "Frontend blue URL."
  value       = "http://${aws_eip.demo.public_ip}:${var.app_blue_port}"
}

output "frontend_green_url" {
  description = "Frontend green URL."
  value       = "http://${aws_eip.demo.public_ip}:${var.app_green_port}"
}

output "backend_blue_url" {
  description = "Backend blue URL."
  value       = "http://${aws_eip.demo.public_ip}:${var.api_blue_port}"
}

output "backend_green_url" {
  description = "Backend green URL."
  value       = "http://${aws_eip.demo.public_ip}:${var.api_green_port}"
}

output "deploy_directory" {
  description = "Directory prepared by user data for compose deployment files."
  value       = "/opt/coach"
}

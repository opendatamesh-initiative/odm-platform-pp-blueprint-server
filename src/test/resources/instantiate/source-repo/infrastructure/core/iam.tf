resource "null_resource" "iam" {
  triggers = {
    component = "iam"
  }
}

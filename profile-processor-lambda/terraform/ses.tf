# to be able to send emails a verified domain is required
resource "aws_ses_domain_identity" "my-domain" {
  domain = "mail.com"
}

resource "aws_ses_domain_identity_verification" "my-domain" {
  domain = aws_ses_domain_identity.my-domain.domain
}

resource "aws_ses_email_identity" "my-email" {
  email = "profile-processor@mail.com"
}

# Outgoing mail (assignment, reminder, completion) through SES with the domain as the sender.
# Domain identity + Easy DKIM: three CNAMEs prove the domain is ours and sign every message, so
# mail from no-reply@<domain> passes DKIM/DMARC instead of landing in spam.
#
# Not in code: a new SES account is in the sandbox (sends only to verified addresses, 200/day).
# For the demo, verify the interviewer-facing inboxes; for real use, request production access.

resource "aws_sesv2_email_identity" "domain" {
  email_identity = var.domain
}

resource "aws_route53_record" "ses_dkim" {
  count   = 3
  zone_id = data.aws_route53_zone.this.zone_id
  name    = "${aws_sesv2_email_identity.domain.dkim_signing_attributes[0].tokens[count.index]}._domainkey.${var.domain}"
  type    = "CNAME"
  ttl     = 1800
  records = ["${aws_sesv2_email_identity.domain.dkim_signing_attributes[0].tokens[count.index]}.dkim.amazonses.com"]
}

# Receivers that check DMARC see DKIM aligned with the From domain. p=none: report only, so a
# misconfiguration during the demo cannot make mail disappear.
resource "aws_route53_record" "dmarc" {
  zone_id = data.aws_route53_zone.this.zone_id
  name    = "_dmarc.${var.domain}"
  type    = "TXT"
  ttl     = 1800
  records = ["v=DMARC1; p=none"]
}

import unittest
from datetime import datetime, timezone
from unittest.mock import patch, MagicMock

from fb_event_inviter.email_invite import EmailInviter, build_ics


class BuildIcsTest(unittest.TestCase):
    def test_contains_expected_fields(self):
        start = datetime(2026, 9, 1, 18, 0, tzinfo=timezone.utc)
        end = datetime(2026, 9, 1, 21, 0, tzinfo=timezone.utc)
        ics = build_ics(
            "Launch Party", "Come celebrate", "123 Main St", start, end,
            "organizer@example.com", event_url="https://www.facebook.com/events/123/",
        )
        self.assertIn("SUMMARY:Launch Party", ics)
        self.assertIn("LOCATION:123 Main St", ics)
        self.assertIn("DTSTART:20260901T180000Z", ics)
        self.assertIn("DTEND:20260901T210000Z", ics)
        self.assertIn("ORGANIZER:mailto:organizer@example.com", ics)
        self.assertIn("facebook.com/events/123", ics)

    def test_escapes_special_characters(self):
        start = datetime(2026, 9, 1, 18, 0, tzinfo=timezone.utc)
        end = datetime(2026, 9, 1, 21, 0, tzinfo=timezone.utc)
        ics = build_ics("A, B; C", "line1\nline2", None, start, end, "organizer@example.com")
        self.assertIn("SUMMARY:A\\, B\\; C", ics)
        self.assertIn("line1\\nline2", ics)


class EmailInviterTest(unittest.TestCase):
    @patch("fb_event_inviter.email_invite.smtplib.SMTP")
    def test_send_invite_sends_to_all_recipients(self, mock_smtp_cls):
        mock_server = MagicMock()
        mock_smtp_cls.return_value.__enter__.return_value = mock_server

        inviter = EmailInviter("smtp.example.com", 587, "user@example.com", "password")
        start = datetime(2026, 9, 1, 18, 0, tzinfo=timezone.utc)
        end = datetime(2026, 9, 1, 21, 0, tzinfo=timezone.utc)

        results = inviter.send_invite(
            recipients=["a@example.com", "b@example.com"],
            subject="You're invited",
            event_name="Launch Party",
            description="Come celebrate",
            location="123 Main St",
            start=start,
            end=end,
            event_url="https://www.facebook.com/events/123/",
        )

        self.assertEqual(results, {"a@example.com": "sent", "b@example.com": "sent"})
        self.assertEqual(mock_server.sendmail.call_count, 2)
        mock_server.starttls.assert_called_once()
        mock_server.login.assert_called_once_with("user@example.com", "password")

    def test_send_invite_requires_recipients(self):
        inviter = EmailInviter("smtp.example.com", 587, "user@example.com", "password")
        start = datetime(2026, 9, 1, 18, 0, tzinfo=timezone.utc)
        end = datetime(2026, 9, 1, 21, 0, tzinfo=timezone.utc)
        with self.assertRaises(ValueError):
            inviter.send_invite(
                recipients=[], subject="s", event_name="e", description="",
                location=None, start=start, end=end,
            )


if __name__ == "__main__":
    unittest.main()

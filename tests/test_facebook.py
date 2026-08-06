import unittest
from unittest.mock import MagicMock

from fb_event_inviter.facebook import FacebookEventClient, FacebookAPIError


class FacebookEventClientTest(unittest.TestCase):
    def test_requires_page_id_and_token(self):
        with self.assertRaises(ValueError):
            FacebookEventClient("", "token")
        with self.assertRaises(ValueError):
            FacebookEventClient("page", "")

    def test_create_event_success(self):
        session = MagicMock()
        session.post.return_value = MagicMock(status_code=200, json=lambda: {"id": "123"})

        client = FacebookEventClient("page123", "token123", session=session)
        result = client.create_event(
            name="Launch Party",
            start_time="2026-09-01T18:00:00-0700",
            end_time="2026-09-01T21:00:00-0700",
            description="Come celebrate with us",
            location="123 Main St",
        )

        self.assertEqual(result, {"id": "123"})
        session.post.assert_called_once()
        _, kwargs = session.post.call_args
        self.assertEqual(kwargs["data"]["name"], "Launch Party")
        self.assertEqual(kwargs["data"]["access_token"], "token123")
        self.assertEqual(client.get_event_url("123"), "https://www.facebook.com/events/123/")

    def test_create_event_api_error(self):
        session = MagicMock()
        session.post.return_value = MagicMock(
            status_code=400,
            json=lambda: {"error": {"message": "Invalid OAuth access token", "code": 190}},
        )

        client = FacebookEventClient("page123", "token123", session=session)
        with self.assertRaises(FacebookAPIError):
            client.create_event(name="Launch Party", start_time="2026-09-01T18:00:00-0700")


if __name__ == "__main__":
    unittest.main()

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

    def test_invite_users_mixed_results(self):
        session = MagicMock()

        def post_side_effect(url, data=None, timeout=None):
            if url.endswith("/111"):
                return MagicMock(status_code=200, json=lambda: {"success": True})
            return MagicMock(
                status_code=403,
                json=lambda: {"error": {"message": "Invalid Scope: user_events", "code": 200}},
            )

        session.post.side_effect = post_side_effect

        client = FacebookEventClient("page123", "token123", session=session)
        results = client.invite_users("event999", ["111", "222"], "user-token-abc")

        self.assertTrue(results["111"]["success"])
        self.assertFalse(results["222"]["success"])
        self.assertIn("error", results["222"])
        self.assertEqual(session.post.call_count, 2)

    def test_invite_users_requires_token_and_ids(self):
        client = FacebookEventClient("page123", "token123", session=MagicMock())
        with self.assertRaises(ValueError):
            client.invite_users("event999", ["111"], "")
        with self.assertRaises(ValueError):
            client.invite_users("event999", [], "user-token-abc")


if __name__ == "__main__":
    unittest.main()

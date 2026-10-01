"""Exercise the HTTP boundary with a fake model, never a paid provider."""
import http.client
import json
import threading
import unittest
from unittest.mock import patch
from http.server import ThreadingHTTPServer
from server import Handler, SLOTS
from task_service import ServiceError


class HttpTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.server = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()
        cls.thread.join()

    def request(self, body='{}', content_type='application/json', path='/v1/task-drafts', method='POST'):
        connection = http.client.HTTPConnection(*self.server.server_address, timeout=3)
        try:
            connection.request(method, path, body, {'Content-Type': content_type})
            response = connection.getresponse()
            return response.status, json.loads(response.read())
        finally:
            connection.close()

    def test_invalid_http_input_never_reaches_model(self):
        with patch('server.create_draft') as model:
            self.assertEqual(415, self.request(content_type='text/plain')[0])
            self.assertEqual(400, self.request(body='{broken')[0])
            self.assertEqual(413, self.request(body='x' * 16001)[0])
            self.assertEqual(404, self.request(path='/other')[0])
            model.assert_not_called()

    def test_timeout_is_recoverable_and_releases_capacity(self):
        with patch('server.create_draft', side_effect=ServiceError('provider_timeout', 504)):
            self.assertEqual((504, {'error': 'provider_timeout'}), self.request())
        with patch('server.create_draft', return_value={'status': 'clarification'}) as model:
            self.assertEqual((200, {'status': 'clarification'}), self.request())
            model.assert_called_once_with({})

    def test_busy_and_health(self):
        SLOTS.acquire()
        try:
            with patch('server.create_draft') as model:
                self.assertEqual((429, {'error': 'busy'}), self.request())
                model.assert_not_called()
            self.assertEqual((200, {'status': 'ok'}), self.request(path='/health', method='GET'))
        finally:
            SLOTS.release()

    def test_action_responses_explicitly_close_and_allow_next_request(self):
        connection = http.client.HTTPConnection(*self.server.server_address, timeout=3)
        try:
            with patch('server.create_action', return_value={'status': 'draft'}) as model:
                for _ in range(3):
                    connection.request('POST', '/v1/action-drafts', '{}', {'Content-Type': 'application/json'})
                    response = connection.getresponse()
                    self.assertEqual(200, response.status)
                    self.assertEqual('close', response.getheader('Connection'))
                    self.assertEqual({'status': 'draft'}, json.loads(response.read()))
                self.assertEqual(3, model.call_count)
        finally:
            connection.close()


if __name__ == '__main__':
    unittest.main()

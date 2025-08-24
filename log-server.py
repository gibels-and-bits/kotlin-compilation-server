#!/usr/bin/env python3
"""
Simple HTTP server that serves the monitor HTML and provides log access
"""
import http.server
import socketserver
import json
import os
from urllib.parse import urlparse

class MonitorHandler(http.server.SimpleHTTPRequestHandler):
    def do_GET(self):
        parsed_path = urlparse(self.path)
        
        if parsed_path.path == '/api/logs':
            # Serve the last N lines of the log file
            self.send_response(200)
            self.send_header('Content-type', 'text/plain')
            self.send_header('Access-Control-Allow-Origin', '*')
            self.end_headers()
            
            log_file = '/tmp/kotlin-server.log'
            if os.path.exists(log_file):
                with open(log_file, 'r') as f:
                    # Get last 100 lines
                    lines = f.readlines()[-100:]
                    self.wfile.write(''.join(lines).encode())
            else:
                self.wfile.write(b'Log file not found')
        else:
            # Serve static files
            super().do_GET()

if __name__ == '__main__':
    PORT = 8888
    Handler = MonitorHandler
    
    with socketserver.TCPServer(("", PORT), Handler) as httpd:
        print(f"Monitor server running at http://localhost:{PORT}/monitor.html")
        httpd.serve_forever()
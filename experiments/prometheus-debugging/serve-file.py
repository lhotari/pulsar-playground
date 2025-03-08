#!/usr/bin/env -S uv run
import argparse
import http.server
import socketserver
import os
import datetime

class SingleFileHandler(http.server.BaseHTTPRequestHandler):
    """HTTP request handler that serves the same file for any request."""
    
    def __init__(self, *args, file_path=None, content_type=None, **kwargs):
        self.file_path = file_path
        self.content_type = content_type
        super().__init__(*args, **kwargs)
    
    def do_GET(self):
        """Handle GET requests by serving the specified file."""
        try:
            # Log request details
            client_address = self.client_address[0]
            request_method = self.command
            request_path = self.path
            request_headers = {k: v for k, v in self.headers.items()}
            
            print(f"\n--- Request Received ---")
            print(f"Time: {self.date_time_string()}")
            print(f"Client: {client_address}")
            print(f"Method: {request_method}")
            print(f"Path: {request_path}")
            print(f"Headers: {request_headers}")
            
            # Check if file exists
            if not os.path.isfile(self.file_path):
                self.send_error(404, f"File not found: {self.file_path}")
                print(f"Response: 404 File Not Found")
                return
                
            # Get file size
            file_size = os.path.getsize(self.file_path)
            
            # Send headers
            self.send_response(200)
            self.send_header('Content-Type', self.content_type)
            self.send_header('Content-Length', file_size)
            self.end_headers()
            
            # Send file content
            with open(self.file_path, 'rb') as file:
                self.wfile.write(file.read())
            
            print(f"Response: 200 OK (Served file: {self.file_path}, Content-Type: {self.content_type})")
                
        except Exception as e:
            self.send_error(500, f"Server error: {str(e)}")
            print(f"Response: 500 Server Error - {str(e)}")
    
    # Also handle POST, PUT, etc. with the same response
    do_POST = do_GET
    do_PUT = do_GET
    do_DELETE = do_GET
    do_HEAD = do_GET
    do_OPTIONS = do_GET

def create_handler_class(file_path, content_type):
    """Create a handler class with the specified file path and content type."""
    def handler(*args, **kwargs):
        return SingleFileHandler(*args, file_path=file_path, content_type=content_type, **kwargs)
    return handler

def run_server(port, file_path, content_type):
    """Run the HTTP server on the specified port."""
    handler_class = create_handler_class(file_path, content_type)
    
    with socketserver.TCPServer(("", port), handler_class) as httpd:
        print(f"Serving file '{file_path}' with content type '{content_type}' on port {port}...")
        print(f"Server URL: http://localhost:{port}")
        try:
            httpd.serve_forever()
        except KeyboardInterrupt:
            print("\nServer stopped.")

def main():
    """Parse command-line arguments and start the server."""
    parser = argparse.ArgumentParser(description='HTTP server that serves a single file for any request.')
    parser.add_argument('port', type=int, help='Port to listen on')
    parser.add_argument('file', type=str, help='File to serve')
    parser.add_argument('content_type', type=str, help='Content type for the file (e.g., text/html, application/json)')
    
    args = parser.parse_args()
    
    run_server(args.port, args.file, args.content_type)

if __name__ == '__main__':
    main()

import socket
import sys

# 1. Connect to Java Server (Port 7777)
s = socket.socket()
try:
    s.connect(('127.0.0.1', 7777))
    print("Connected to Java Server.")
except ConnectionRefusedError:
    print("Error: Could not connect. Is the Java Server running on port 7777?")
    sys.exit(1)

# 2. Construct a valid CONNECT frame
# Note: We use the credentials you inserted earlier (bob/pass)
connect_frame = (
    "CONNECT\n"
    "accept-version:1.2\n"
    "host:stomp.cs.bgu.ac.il\n"
    "login:bob\n"
    "passcode:pass\n"
    "\n"
    "\0"  # Critical: Null byte to end the frame
)

# 3. Send the frame
s.sendall(connect_frame.encode('utf-8'))
print("Sent CONNECT frame. Waiting for response...")

# 4. Receive response
response = s.recv(1024).decode('utf-8')
print("\n--- Server Response ---")
print(response)
print("-----------------------")
s.close()
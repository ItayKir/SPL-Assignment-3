import socket
import time
import sys
import threading

# ==========================================
# CONFIGURATION
# ==========================================
HOST = '127.0.0.1'
PORT = 7777

# Colors for pretty output
class Colors:
    HEADER = '\033[95m'
    OKBLUE = '\033[94m'
    OKGREEN = '\033[92m'
    WARNING = '\033[93m'
    FAIL = '\033[91m'
    ENDC = '\033[0m'
    BOLD = '\033[1m'

def print_status(msg, status="INFO"):
    if status == "PASS":
        print(f"{Colors.OKGREEN}[PASS] {msg}{Colors.ENDC}")
    elif status == "FAIL":
        print(f"{Colors.FAIL}[FAIL] {msg}{Colors.ENDC}")
    elif status == "INFO":
        print(f"{Colors.OKBLUE}[INFO] {msg}{Colors.ENDC}")
    elif status == "HEADER":
        print(f"\n{Colors.HEADER}{Colors.BOLD}=== {msg} ==={Colors.ENDC}")

# ==========================================
# HELPER CLASSES
# ==========================================
class StompClient:
    def __init__(self, name):
        self.name = name
        self.sock = None
        self.connected = False
        self.buf = ""
        self.sub_id_counter = 0
        self.receipt_id_counter = 0

    def connect(self):
        try:
            self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            self.sock.connect((HOST, PORT))
            print_status(f"{self.name} socket connected.", "INFO")
        except Exception as e:
            print_status(f"{self.name} connection failed: {e}", "FAIL")
            sys.exit(1)

    def send_frame(self, frame):
        if self.sock:
            # Ensure proper NULL termination exactly like the C++ client
            if not frame.endswith('\0'):
                frame += '\0'
            self.sock.sendall(frame.encode('utf-8'))

    def read_frame(self, timeout=3):
        if not self.sock:
            return None
        self.sock.settimeout(timeout)
        try:
            # Simple buffer reading mechanism to fetch one full STOMP frame (ending in \0)
            while '\0' not in self.buf:
                data = self.sock.recv(1024).decode('utf-8')
                if not data:
                    return None # Socket closed
                self.buf += data
            
            frame_end = self.buf.index('\0')
            frame = self.buf[:frame_end]
            self.buf = self.buf[frame_end+1:] # Keep leftover bytes
            
            # Clean up leading newlines (Keepalive/Heartbeat handling)
            frame = frame.lstrip()
            return frame
        except socket.timeout:
            return "TIMEOUT"
        except Exception as e:
            return f"ERROR: {e}"

    def close(self):
        if self.sock:
            self.sock.close()

    # --- COMMAND SIMULATIONS ---

    def login(self, username, password):
        frame = (
            f"CONNECT\n"
            f"accept-version:1.2\n"
            f"host:stomp.cs.bgu.ac.il\n"
            f"login:{username}\n"
            f"passcode:{password}\n"
            f"\n" # Empty line before body
        )
        self.send_frame(frame)
        return self.read_frame()

    def join(self, destination):
        # Matches C++ 'join' logic: Subscribe with ID and Receipt
        sub_id = self.sub_id_counter
        receipt_id = self.receipt_id_counter
        self.sub_id_counter += 1
        self.receipt_id_counter += 1

        frame = (
            f"SUBSCRIBE\n"
            f"destination:/{destination}\n" # C++ client adds '/' ? Let's assume input has it or not.
            f"id:{sub_id}\n"
            f"receipt:{receipt_id}\n"
            f"\n"
        )
        self.send_frame(frame)
        return self.read_frame()

    def add(self, destination, body_text):
        # Matches C++ 'add' logic: SEND frame
        frame = (
            f"SEND\n"
            f"destination:/{destination}\n"
            f"\n"
            f"{body_text}\n"
        )
        self.send_frame(frame)

    def report(self, destination, event_body):
        # Matches C++ 'report' logic: SEND frame with complex body
        frame = (
            f"SEND\n"
            f"destination:/{destination}\n"
            f"\n"
            f"{event_body}\n" # The body already contains newlines from the JSON parse
        )
        self.send_frame(frame)
    
    def logout(self):
        # Matches C++ 'logout' logic: DISCONNECT with receipt
        receipt_id = self.receipt_id_counter
        self.receipt_id_counter += 1
        
        frame = (
            f"DISCONNECT\n"
            f"receipt:{receipt_id}\n"
            f"\n"
        )
        self.send_frame(frame)
        return self.read_frame()

# ==========================================
# TESTS
# ==========================================

def test_login_flow():
    print_status("Checking Login/Logout & Errors", "HEADER")
    
    # 1. Valid Login
    c = StompClient("Alice")
    c.connect()
    resp = c.login("alice", "1234")
    if resp and "CONNECTED" in resp:
        print_status("Valid Login", "PASS")
    else:
        print_status(f"Valid Login failed. Got: {resp}", "FAIL")
    
    # 2. Logout
    resp = c.logout()
    if resp and "RECEIPT" in resp:
        print_status("Graceful Logout", "PASS")
    else:
        print_status(f"Logout failed. Got: {resp}", "FAIL")
    c.close()

    # 3. Invalid Password
    c = StompClient("BadPass")
    c.connect()
    resp = c.login("alice", "wrongpass")
    if resp and "ERROR" in resp:
        print_status("Invalid Password check", "PASS")
    else:
        print_status("Server allowed wrong password or crashed", "FAIL")
    c.close()

    # 4. Double Login (Same User)
    c1 = StompClient("Bob1")
    c1.connect()
    c1.login("bob", "1234")
    
    c2 = StompClient("Bob2")
    c2.connect()
    resp = c2.login("bob", "1234")
    
    if resp and "ERROR" in resp and "already logged in" in resp.lower():
        print_status("Double Login Prevention", "PASS")
    else:
        print_status(f"Double login failed detection. Got: {resp}", "FAIL")
    c1.logout()
    c1.close()
    c2.close()

def test_messaging_flow():
    print_status("Checking Messaging (Add/Join)", "HEADER")
    
    # Setup: Alice and Bob login
    alice = StompClient("Alice")
    alice.connect()
    alice.login("alice", "1234")

    bob = StompClient("Bob")
    bob.connect()
    bob.login("bob", "1234")

    # 1. Join (Subscribe)
    topic = "topic/germany_spain"
    
    resp_a = alice.join(topic)
    resp_b = bob.join(topic)

    if resp_a and "RECEIPT" in resp_a and resp_b and "RECEIPT" in resp_b:
        print_status("Both clients joined channel (Got Receipts)", "PASS")
    else:
        print_status("Join failed.", "FAIL")
        return

    # 2. Add (Send Message)
    msg_content = "Hello from Alice"
    alice.add(topic, msg_content)
    
    # Bob should receive it
    bob_msg = bob.read_frame()
    if bob_msg and "MESSAGE" in bob_msg and msg_content in bob_msg:
        print_status("Bob received Alice's message", "PASS")
    else:
        print_status(f"Bob missed message. Got: {bob_msg}", "FAIL")

    # 3. Send to non-subscribed topic (Edge Case)
    bob.add("topic/unknown_channel", "Intruder Alert")
    err_resp = bob.read_frame() # Expect ERROR or Disconnect
    
    if err_resp and "ERROR" in err_resp:
        print_status("Sending to unsubscribed topic blocked", "PASS")
    else:
        print_status(f"Server allowed sending to unsubscribed topic? Got: {err_resp}", "FAIL")

    alice.close()
    bob.close()

def test_report_simulation():
    print_status("Checking Report Command (Complex Body)", "HEADER")
    
    # Alice listens, Bob reports
    alice = StompClient("Alice")
    alice.connect()
    alice.login("alice", "1234")
    alice.join("topic/argentina_brazil")

    bob = StompClient("Bob")
    bob.connect()
    bob.login("bob", "1234")
    bob.join("topic/argentina_brazil")

    # Simulate the body generated by C++ 'report' command
    # It contains newlines, key-values, etc.
    report_body = (
        "user:bob\n"
        "team a:Argentina\n"
        "team b:Brazil\n"
        "event name:Goal\n"
        "time:15\n"
        "general game updates:\n"
        "active:true\n"
        "description:\n"
        "Messi scores a goal!"
    )

    bob.report("topic/argentina_brazil", report_body)
    
    # Alice receives
    msg = alice.read_frame()
    
    # Verification
    if msg and "MESSAGE" in msg:
        # Check integrity of the body
        if "Messi scores a goal!" in msg and "team a:Argentina" in msg:
             print_status("Report received intact (Body preserved)", "PASS")
        else:
             print_status("Report received but body corrupted/truncated", "FAIL")
             print(f"DEBUG: Received Body:\n---\n{msg}\n---")
    else:
        print_status("Report message not received", "FAIL")

    alice.close()
    bob.close()

def test_lifecycle_stress():
    print_status("Checking Lifecycle & Stress", "HEADER")
    
    # Login, Join, Exit, Logout loop
    c = StompClient("StressUser")
    c.connect()
    c.login("stress", "1234")
    
    # Join
    resp = c.join("topic/stress_test")
    if not resp or "RECEIPT" not in resp:
        print_status("Join failed during stress test", "FAIL")
        c.close()
        return

    # Exit (Unsubscribe) -> C++ client sends UNSUBSCRIBE id:X receipt:Y
    frame = (
        f"UNSUBSCRIBE\n"
        f"id:0\n" # Assuming ID 0 from previous join
        f"receipt:999\n"
        f"\n"
    )
    c.send_frame(frame)
    resp = c.read_frame()
    
    if resp and "RECEIPT" in resp and "receipt-id:999" in resp:
        print_status("Exit (Unsubscribe) successful", "PASS")
    else:
        print_status(f"Exit failed. Got: {resp}", "FAIL")

    # Logout
    c.logout()
    c.close()

# ==========================================
# MAIN EXECUTION
# ==========================================
if __name__ == "__main__":
    try:
        test_login_flow()
        test_messaging_flow()
        test_report_simulation()
        test_lifecycle_stress()
        print("\nAll Tests Completed.")
    except KeyboardInterrupt:
        print("\nTest interrupted.")
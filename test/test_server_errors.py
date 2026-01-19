import subprocess
import time
import threading
import queue
import sys
import os

# Configuration
CLIENT_EXECUTABLE = "../client/bin/StompWCIClient"  # Ensure this path is correct
HOST = "127.0.0.1"
PORT = 7777

class ClientWrapper:
    def __init__(self, name="Client"):
        self.name = name
        self.process = None
        self.output_queue = queue.Queue()
        self.stop_reading = False

    def start(self):
        """Starts the C++ client subprocess."""
        if not os.path.exists(CLIENT_EXECUTABLE):
            print(f"[{self.name}] Error: Executable '{CLIENT_EXECUTABLE}' not found. Did you run 'make'?")
            sys.exit(1)

        self.process = subprocess.Popen(
            [CLIENT_EXECUTABLE],
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            bufsize=1  # Line buffered
        )
        
        # Start a background thread to read output non-blockingly
        self.reader_thread = threading.Thread(target=self._read_output)
        self.reader_thread.daemon = True
        self.reader_thread.start()
        print(f"[{self.name}] Started.")

    def _read_output(self):
        """Reads stdout line by line and puts it in a queue."""
        try:
            for line in iter(self.process.stdout.readline, ''):
                if self.stop_reading: break
                if line:
                    clean_line = line.strip()
                    self.output_queue.put(clean_line)
                    # Uncomment below to see raw output in real-time
                    # print(f"[{self.name} RAW] {clean_line}")
        except ValueError:
            pass # File closed

    def send(self, command):
        """Sends a command to the client's stdin."""
        if self.process and self.process.stdin:
            print(f"[{self.name}] Sending: {command}")
            try:
                self.process.stdin.write(command + "\n")
                self.process.stdin.flush()
            except BrokenPipeError:
                print(f"[{self.name}] Error: Pipe broken (Process likely dead).")

    def expect(self, text, timeout=2):
        """Waits for specific text to appear in stdout."""
        start_time = time.time()
        buffer = []
        while time.time() - start_time < timeout:
            try:
                line = self.output_queue.get(timeout=0.1)
                buffer.append(line)
                if text in line:
                    print(f"[{self.name}] \033[92mSUCCESS\033[0m: Found '{text}'")
                    return True
            except queue.Empty:
                continue
        
        print(f"[{self.name}] \033[91mFAILURE\033[0m: Timed out waiting for '{text}'")
        print(f"[{self.name}] Recent Output: {buffer}")
        return False

    def close(self):
        """Terminates the process."""
        self.stop_reading = True
        if self.process:
            self.process.terminate()
            self.process.wait()
            print(f"[{self.name}] Closed.")

# =============================================================================
# TESTS
# =============================================================================

def test_login_wrong_password():
    print("\n--- TEST: Login with Wrong Password ---")
    # Intent: Verify Server sends ERROR frame and closes connection.
    client = ClientWrapper("Alice")
    client.start()
    
    # 1. Login with wrong password
    client.send(f"login {HOST} {PORT} alice wrongpass")
    
    # 2. Expect specific error from server (The assignment says 'Wrong password')
    # Depending on your server implementation, the error body might vary.
    # We look for the generic 'Error' prompt from the client or specific text.
    if client.expect("Login successful", timeout=1):
        print("FAILURE: Client logged in with wrong password!")
    else:
        # Check if we got the error frame print
        # Note: Your client code prints "Error received from server: ..."
        if client.expect("Error received", timeout=2) or client.expect("Wrong password", timeout=2):
            print("SUCCESS: Server rejected wrong password.")
        else:
            print("WARNING: Did not see explicit error message, but login failed.")

    client.close()

def test_double_login():
    print("\n--- TEST: Double Login (Same User) ---")
    # Intent: Verify Server rejects the second connection for the same user.
    
    # 1. Start Client A (Alice)
    alice1 = ClientWrapper("Alice1")
    alice1.start()
    alice1.send(f"login {HOST} {PORT} alice 123456")
    if not alice1.expect("Login successful"):
        print("ABORT: Alice1 failed to login.")
        alice1.close()
        return

    # 2. Start Client B (Alice) - SAME USERNAME
    alice2 = ClientWrapper("Alice2")
    alice2.start()
    time.sleep(1) 
    alice2.send(f"login {HOST} {PORT} alice 123456")

    # 3. Expect Error on Client B
    # Server should detect alice is already logged in
    if alice2.expect("User already logged in", timeout=2) or alice2.expect("Error received", timeout=2):
        print("SUCCESS: Server rejected double login.")
    elif alice2.expect("Login successful", timeout=1):
        print("FAILURE: Server allowed double login!")
    else:
        print("FAILURE: No response/timeout on double login attempt.")

    alice1.close()
    alice2.close()

def test_send_without_subscription():
    print("\n--- TEST: Send Message Without Subscription ---")
    # Intent: Verify Server sends ERROR if we write to a channel we didn't join.
    # Note: Your C++ client handles 'join' logic locally, blocking double joins.
    # BUT, 'add' (SEND) logic is NOT blocked locally in your code!
    # This effectively tests if the server enforces permissions.
    
    bob = ClientWrapper("Bob")
    bob.start()
    bob.send(f"login {HOST} {PORT} bob 123456")
    
    if bob.expect("Login successful"):
        # 1. Try to send to a channel we never joined
        bob.send("add game_of_thrones WinterIsComing")
        
        # 2. Expect Server Error
        # Common error: "User is not subscribed to topic..."
        if bob.expect("Error received", timeout=2) or bob.expect("subscribed", timeout=2):
            print("SUCCESS: Server caught unauthorized send.")
        else:
            print("FAILURE: Server apparently accepted the message (or silent fail).")
            
    bob.close()

def test_client_side_commands_before_login():
    print("\n--- TEST: Commands Before Login (Client Logic) ---")
    # Intent: Verify Client prevents sending protocol frames before handshake.
    
    eve = ClientWrapper("Eve")
    eve.start()
    
    # 1. Try join without login
    eve.send("join germany_spain")
    if eve.expect("Please login first"):
        print("SUCCESS: Client blocked 'join' before login.")
    else:
        print("FAILURE: Client did not block command.")
        
    eve.close()

def run_all_tests():
    print("========================================")
    print(f"Starting Tests on {HOST}:{PORT}")
    print("Ensure your Server is running!")
    print("========================================")
    
    try:
        test_client_side_commands_before_login()
        time.sleep(1)
        test_double_login()
        time.sleep(1)
        test_send_without_subscription()
        time.sleep(1)
        test_login_wrong_password()
    except KeyboardInterrupt:
        print("\nTests interrupted.")

if __name__ == "__main__":
    run_all_tests()
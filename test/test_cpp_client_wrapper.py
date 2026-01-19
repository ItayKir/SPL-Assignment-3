import subprocess
import time
import os
import sys

# Path to your compiled C++ client executable
CLIENT_EXE = "../client/bin/StompWCIClient"
TEST_DIR = "./test"
SUMMARY_FILE = os.path.join(TEST_DIR, "summary.txt")

class ClientInstance:
    def __init__(self, name):
        self.name = name
        self.process = None

    def start(self):
        """Starts the C++ Client subprocess."""
        try:
            # bufsize=0 (unbuffered) ensures we see output immediately
            self.process = subprocess.Popen(
                [CLIENT_EXE],
                stdin=subprocess.PIPE,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
                bufsize=0 
            )
            print(f"[{self.name}] Started StompWCIClient PID: {self.process.pid}")
        except FileNotFoundError:
            print(f"Error: Could not find {CLIENT_EXE}. Did you run 'make'?")
            sys.exit(1)

    def write(self, command):
        """Writes a command to the C++ Client's cin."""
        if self.process and self.process.poll() is None:
            print(f"[{self.name} INPUT] {command}")
            self.process.stdin.write(command + "\n")
            self.process.stdin.flush()
            time.sleep(0.2) # Delay to let C++ process input

    def expect(self, output_substring, timeout=5):
        """
        Reads stdout line-by-line looking for a substring.
        Returns True if found, False if timeout.
        """
        start_time = time.time()
        while time.time() - start_time < timeout:
            if self.process.poll() is not None:
                print(f"[{self.name}] Process died unexpectedly!")
                return False
            
            # Non-blocking read logic (using readline which blocks briefly is okay for this test)
            line = self.process.stdout.readline()
            if line:
                line = line.strip()
                # print(f"[{self.name} OUTPUT] {line}") # Uncomment to see all output
                if output_substring in line:
                    return True
            else:
                time.sleep(0.05)
        
        return False

    def close(self):
        """Closes the subprocess."""
        if self.process:
            self.process.terminate()
            self.process.wait()
            print(f"[{self.name}] Closed.")

def ensure_test_dir():
    if not os.path.exists(TEST_DIR):
        os.makedirs(TEST_DIR)
        print(f"Created directory: {TEST_DIR}")

def run_test():
    ensure_test_dir()
    print("=== Testing with REAL StompWCIClient (Report, Summary, Exit) ===")
    
    # ---------------------------------------------------------
    # SETUP: Alice and Bob Login
    # ---------------------------------------------------------
    alice = ClientInstance("Alice")
    alice.start()
    alice.write("login 127.0.0.1 7777 alice 1234")
    if not alice.expect("Login successful"):
        print("   [FAIL] Alice failed login.")
        return

    bob = ClientInstance("Bob")
    bob.start()
    bob.write("login 127.0.0.1 7777 bob 1234")
    if not bob.expect("Login successful"):
        print("   [FAIL] Bob failed login.")
        alice.close(); return

    # ---------------------------------------------------------
    # TEST 1: Join & Report
    # The JSON file events1_partial.json contains "Germany" vs "Japan".
    # The channel name derived from logic is usually "Germany_Japan".
    # ---------------------------------------------------------
    CHANNEL = "Germany_Japan"
    
    print(f"\n1. Both joining channel: {CHANNEL}...")
    alice.write(f"join {CHANNEL}")
    if not alice.expect(f"Joined channel {CHANNEL}"):
        print("   [FAIL] Alice failed to join.")
        return

    bob.write(f"join {CHANNEL}")
    if not bob.expect(f"Joined channel {CHANNEL}"):
        print("   [FAIL] Bob failed to join.")
        return

    print("\n2. Alice reporting from file '../client/data/events1_partial.json'...")
    # NOTE: Path must be relative to where you run this python script
    alice.write("report ../client/data/events1_partial.json")

    # Bob should receive the events.
    # Event 1: "kickoff" -> "The game has started!"
    # Event 2: "goal!!!!" -> "GOOOAAALLL!!!"
    
    print("   Waiting for Bob to receive game updates...")
    if bob.expect("The game has started!"):
        print("   [PASS] Bob received 'kickoff' event.")
    else:
        print("   [FAIL] Bob did not receive 'kickoff'.")

    if bob.expect("GOOOAAALLL!!!"):
        print("   [PASS] Bob received 'goal' event.")
    else:
        print("   [FAIL] Bob did not receive 'goal'.")

    # Alice should ALSO receive them (because she is subscribed). 
    # We need her to receive them to store data for 'summary'.
    print("   Waiting for Alice to receive her own updates (for summary storage)...")
    if alice.expect("The game has started!") and alice.expect("GOOOAAALLL!!!"):
        print("   [PASS] Alice received updates.")
    else:
        print("   [FAIL] Alice did not receive updates.")

    # ---------------------------------------------------------
    # TEST 2: Summary
    # Alice summarizes the game 'Germany_Japan' for user 'alice'
    # ---------------------------------------------------------
    print(f"\n3. Alice generating summary to {SUMMARY_FILE}...")
    # Usage: summary {game_name} {user} {file}
    alice.write(f"summary {CHANNEL} alice {SUMMARY_FILE}")

    # Check Stdout for summary header
    if alice.expect("Germany vs Japan"):
        print("   [PASS] Summary printed to stdout.")
    else:
        print("   [FAIL] Summary not found in stdout.")

    # Check File Existence
    if os.path.exists(SUMMARY_FILE):
        with open(SUMMARY_FILE, 'r') as f:
            content = f.read()
            if "Germany vs Japan" in content and "GOOOAAALLL!!!" in content:
                print("   [PASS] Summary file created and verified.")
            else:
                print(f"   [FAIL] Summary file content incorrect.\nContent: {content}")
    else:
        print(f"   [FAIL] Summary file {SUMMARY_FILE} was not created.")

    # ---------------------------------------------------------
    # TEST 3: Exit (Unsubscribe)
    # ---------------------------------------------------------
    print(f"\n4. Alice exiting channel {CHANNEL}...")
    alice.write(f"exit {CHANNEL}")
    
    # "Exited channel..." string comes from StompProtocol receipt handler
    if alice.expect(f"Exited channel {CHANNEL}"):
        print("   [PASS] Alice exited channel.")
    else:
        print("   [FAIL] Alice failed to exit (No Receipt).")

    # ---------------------------------------------------------
    # CLEANUP: Logout
    # ---------------------------------------------------------
    print("\n5. Logout...")
    alice.write("logout")
    alice.expect("Logged out")
    
    bob.write("logout")
    bob.expect("Logged out")

    alice.close()
    bob.close()
    print("\n=== All Tests Passed ===")

if __name__ == "__main__":
    run_test()
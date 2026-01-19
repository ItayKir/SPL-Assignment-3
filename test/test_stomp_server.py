import socket
import time
import sys
import threading

# Configuration
HOST = '127.0.0.1'
PORT = 7777

class TextColors:
    HEADER = '\033[95m'
    OKBLUE = '\033[94m'
    OKGREEN = '\033[92m'
    WARNING = '\033[93m'
    FAIL = '\033[91m'
    ENDC = '\033[0m'

def print_pass(message):
    print(f"{TextColors.OKGREEN}[PASS] {message}{TextColors.ENDC}")

def print_fail(message):
    print(f"{TextColors.FAIL}[FAIL] {message}{TextColors.ENDC}")

def connect_socket():
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.connect((HOST, PORT))
        return s
    except ConnectionRefusedError:
        print_fail("Could not connect to server. Is it running?")
        sys.exit(1)

def send_frame(sock, frame_str):
    sock.sendall(frame_str.encode('utf-8'))

def recv_frame(sock, timeout=2):
    sock.settimeout(timeout)
    try:
        data = sock.recv(4096).decode('utf-8')
        if not data:
            return None
        return data
    except socket.timeout:
        return "TIMEOUT"
    except ConnectionResetError:
        return "CONNECTION_RESET"

def build_connect(login, passcode, version="1.2"):
    return (
        f"CONNECT\n"
        f"accept-version:{version}\n"
        f"host:stomp.cs.bgu.ac.il\n"
        f"login:{login}\n"
        f"passcode:{passcode}\n"
        f"\n\0"
    )

# ==========================================
# TEST CASES
# ==========================================

def test_login_success():
    print(f"\n{TextColors.HEADER}--- Test 1: Successful Login ---{TextColors.ENDC}")
    s = connect_socket()
    send_frame(s, build_connect("testuser1", "1234"))
    resp = recv_frame(s)
    
    if resp and "CONNECTED" in resp and "version:1.2" in resp:
        print_pass("User logged in successfully and received CONNECTED frame.")
    else:
        print_fail(f"Login failed. Response: {resp}")
    s.close()

def test_login_wrong_password():
    print(f"\n{TextColors.HEADER}--- Test 2: Login Wrong Password ---{TextColors.ENDC}")
    # First create the user (if not exists)
    s = connect_socket()
    send_frame(s, build_connect("testuser2", "correctpass"))
    recv_frame(s)
    s.close()

    # Try wrong pass
    s = connect_socket()
    send_frame(s, build_connect("testuser2", "wrongpass"))
    resp = recv_frame(s)
    
    # Expect ERROR and connection close
    if resp and "ERROR" in resp:
        print_pass("Server returned ERROR for wrong password.")
        # Check if socket closed
        check = recv_frame(s, timeout=1)
        if check is None or check == "CONNECTION_RESET":
            print_pass("Connection closed after error.")
        else:
            print_fail("Connection remained open after login error.")
    else:
        print_fail(f"Expected ERROR frame, got: {resp}")
    s.close()

def test_login_bad_version():
    print(f"\n{TextColors.HEADER}--- Test 3: Invalid Version ---{TextColors.ENDC}")
    s = connect_socket()
    send_frame(s, build_connect("testuser3", "1234", version="1.0"))
    resp = recv_frame(s)
    
    if resp and "ERROR" in resp and "version" in resp.lower():
        print_pass("Server rejected version 1.0.")
    else:
        print_fail(f"Expected ERROR for bad version, got: {resp}")
    s.close()

def test_pub_sub_flow():
    print(f"\n{TextColors.HEADER}--- Test 4: Pub/Sub & Receipt ---{TextColors.ENDC}")
    # Alice (Listener)
    alice = connect_socket()
    send_frame(alice, build_connect("alice", "pass"))
    recv_frame(alice)

    # Alice Subscribes with Receipt
    sub_frame = (
        "SUBSCRIBE\n"
        "destination:/topic/cool_topic\n"
        "id:100\n"
        "receipt:77\n"
        "\n\0"
    )
    send_frame(alice, sub_frame)
    resp = recv_frame(alice)
    
    if resp and "RECEIPT" in resp and "receipt-id:77" in resp:
        print_pass("Alice subscribed and got RECEIPT.")
    else:
        print_fail(f"Alice did not get RECEIPT. Got: {resp}")
        return

    # Bob (Sender)
    bob = connect_socket()
    send_frame(bob, build_connect("bob", "pass"))
    recv_frame(bob)

    # Bob MUST subscribe first (per your implementation logic)
    send_frame(bob, "SUBSCRIBE\ndestination:/topic/cool_topic\nid:101\n\n\0")
    
    # Bob Sends Message
    msg_body = "Hello Alice!"
    send_frame(bob, f"SEND\ndestination:/topic/cool_topic\n\n{msg_body}\n\0")
    print("[*] Bob sent message.")

    # Check Alice
    alice_msg = recv_frame(alice)
    if alice_msg and "MESSAGE" in alice_msg and msg_body in alice_msg:
        print_pass("Alice received Bob's message.")
    else:
        print_fail(f"Alice did not receive message. Got: {alice_msg}")

    alice.close()
    bob.close()

def test_send_not_subscribed():
    print(f"\n{TextColors.HEADER}--- Test 5: Send without Subscribe ---{TextColors.ENDC}")
    s = connect_socket()
    send_frame(s, build_connect("bad_sender", "1234"))
    recv_frame(s)

    # Try to send without subscribing
    send_frame(s, "SEND\ndestination:/topic/restricted\n\nI am intruder\n\0")
    resp = recv_frame(s)

    if resp and "ERROR" in resp:
        print_pass("Server blocked sending to non-subscribed topic.")
    else:
        print_fail(f"Server allowed sending or gave wrong response: {resp}")
    s.close()

def test_send_empty_body():
    print(f"\n{TextColors.HEADER}--- Test 6: Send Empty Body ---{TextColors.ENDC}")
    s = connect_socket()
    send_frame(s, build_connect("empty_sender", "1234"))
    recv_frame(s)

    # Subscribe first
    send_frame(s, "SUBSCRIBE\ndestination:/topic/empty\nid:55\n\n\0")
    
    # Send empty body
    send_frame(s, "SEND\ndestination:/topic/empty\n\n\0")
    resp = recv_frame(s)

    # Depending on implementation, might receive the message or an error. 
    # Your code specifically checks for empty body -> ERROR.
    if resp and "ERROR" in resp:
        print_pass("Server correctly returned ERROR for empty message body.")
    else:
        print_fail(f"Expected ERROR for empty body, got: {resp}")
    s.close()

def test_unknown_command():
    print(f"\n{TextColors.HEADER}--- Test 7: Unknown Command (Crash Check) ---{TextColors.ENDC}")
    s = connect_socket()
    send_frame(s, build_connect("crasher", "1234"))
    recv_frame(s)

    # Send Garbage
    send_frame(s, "SENDdd\ndestination:/topic/a\n\nbody\n\0")
    resp = recv_frame(s)

    if resp and "ERROR" in resp:
        print_pass("Server handled unknown command gracefully with ERROR.")
    elif resp == "CONNECTION_RESET" or resp is None:
        print_pass("Server closed connection (Acceptable if ERROR was sent before close).")
    else:
        print_fail(f"Server response unexpected: {resp}")
    s.close()

def test_missing_headers():
    print(f"\n{TextColors.HEADER}--- Test 8: Missing Headers ---{TextColors.ENDC}")
    s = connect_socket()
    send_frame(s, build_connect("header_tester", "1234"))
    recv_frame(s)

    # Send SUBSCRIBE without ID
    send_frame(s, "SUBSCRIBE\ndestination:/topic/missing_id\n\n\0")
    resp = recv_frame(s)

    if resp and "ERROR" in resp and "missing" in resp.lower():
        print_pass("Server detected missing header.")
    else:
        print_fail(f"Expected ERROR for missing header, got: {resp}")
    s.close()

def test_double_login():
    print(f"\n{TextColors.HEADER}--- Test 9: Double Login (Same User) ---{TextColors.ENDC}")
    # Client 1
    s1 = connect_socket()
    send_frame(s1, build_connect("double_agent", "1234"))
    recv_frame(s1)

    # Client 2 (Same credentials)
    s2 = connect_socket()
    send_frame(s2, build_connect("double_agent", "1234"))
    resp = recv_frame(s2)

    if resp and "ERROR" in resp and "already logged in" in resp.lower():
        print_pass("Server prevented double login.")
    else:
        print_fail(f"Server allowed double login or gave wrong error: {resp}")
    
    s1.close()
    s2.close()

# ==========================================
# RUN ALL
# ==========================================
if __name__ == "__main__":
    print(f"{TextColors.HEADER}Starting Comprehensive STOMP Server Tests...{TextColors.ENDC}")
    
    test_login_success()
    test_login_wrong_password()
    test_login_bad_version()
    test_pub_sub_flow()
    test_send_not_subscribed()
    test_send_empty_body()
    test_unknown_command()
    test_missing_headers()
    test_double_login()

    print(f"\n{TextColors.HEADER}Tests Completed.{TextColors.ENDC}")
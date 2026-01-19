import socket
import sys

def connect_and_login(username, password):
    s = socket.socket()
    s.connect(('127.0.0.1', 7777))
    
    frame = (
        f"CONNECT\n"
        f"accept-version:1.2\n"
        f"host:stomp.cs.bgu.ac.il\n"
        f"login:{username}\n"
        f"passcode:{password}\n"
        f"\n\0"
    )
    s.sendall(frame.encode())
    response = s.recv(1024).decode()
    if "CONNECTED" not in response:
        print(f"[-] Failed to login as {username}. Response:\n{response}")
        sys.exit(1)
    print(f"[+] {username} logged in.")
    return s

# 1. Login Alice (The Listener)
alice = connect_and_login("alice", "1234")

# 2. Alice Subscribes
alice_sub_frame = (
    "SUBSCRIBE\n"
    "destination:/topic/germany_spain\n"
    "id:78\n"
    "receipt:101\n"
    "\n\0"
)
alice.sendall(alice_sub_frame.encode())
response = alice.recv(1024).decode()
if "RECEIPT" in response:
    print("[+] Alice is successfully subscribed.")

# 3. Login Bob (The Sender)
bob = connect_and_login("bob", "5678")

# --- FIX: Bob MUST Subscribe before sending ---
print("[*] Bob is subscribing...")
bob_sub_frame = (
    "SUBSCRIBE\n"
    "destination:/topic/germany_spain\n"
    "id:79\n"
    "receipt:102\n"
    "\n\0"
)
bob.sendall(bob_sub_frame.encode())
bob.recv(1024) # Consume Bob's Receipt
print("[+] Bob is successfully subscribed.")
# -----------------------------------------------

# 4. Bob sends a message
msg_frame = (
    "SEND\n"
    "destination:/topic/germany_spain\n"
    "\n"
    "GOAL!!!\n"
    "\0"
)
bob.sendall(msg_frame.encode())
print("[*] Bob sent a message.")

try:
    data = bob.recv(1024).decode()
    print(data)
except socket.timeout:
    print("\nFAILURE: Alice received nothing (Timeout).")

# Optional: Bob can check for errors (socket is non-blocking usually, but here we can peek)
# If the server rejected it, Bob would have data waiting.

# 5. Check if Alice received it
alice.settimeout(2)
try:
    data = alice.recv(1024).decode()
    if "MESSAGE" in data and "GOAL!!!" in data:
        print("\nSUCCESS! Alice received the message.")
        print("-" * 30)
        print(data)
        print("-" * 30)
    else:
        print("\nFAILURE: Alice received something else:\n", data)
except socket.timeout:
    print("\nFAILURE: Alice received nothing (Timeout).")

alice.close()
bob.close()
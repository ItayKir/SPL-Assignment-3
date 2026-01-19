import socket

s = socket.socket()
s.connect(('127.0.0.1', 7777))

# 1. Login
s.sendall(b"CONNECT\naccept-version:1.2\nhost:stomp.cs.bgu.ac.il\nlogin:dave\npasscode:pass\n\n\0")
s.recv(1024)

# 2. Disconnect
print("Sending DISCONNECT...")
s.sendall(b"DISCONNECT\nreceipt:77\n\n\0")

# 3. Expect Receipt then Close
response = s.recv(1024).decode()
if "RECEIPT" in response and "77" in response:
    print("Got RECEIPT. Waiting for server to close socket...")
    try:
        # Try to read again. If socket is closed, this returns empty bytes b''
        check = s.recv(1024)
        if check == b'':
            print("SUCCESS: Connection closed by server.")
        else:
            print("FAILURE: Server sent more data or kept connection open:", check)
    except:
        print("SUCCESS: Connection closed.")
else:
    print("FAILURE: No Receipt received.")
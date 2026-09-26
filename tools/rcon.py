"""极简 RCON 客户端（纯 python，用于测试服务器驱动）。"""
import socket, struct, sys, time

def pkt(id_, type_, body):
    data = struct.pack("<ii", id_, type_) + body.encode("utf-8") + b"\x00\x00"
    return struct.pack("<i", len(data)) + data

def read_pkt(sock):
    raw = sock.recv(4)
    if len(raw) < 4:
        return None
    (length,) = struct.unpack("<i", raw)
    data = b""
    while len(data) < length:
        chunk = sock.recv(length - len(data))
        if not chunk:
            break
        data += chunk
    id_, type_ = struct.unpack("<ii", data[:8])
    return id_, type_, data[8:-2].decode("utf-8", "replace")

def main():
    host, port, password = "127.0.0.1", int(sys.argv[1]), sys.argv[2]
    sock = socket.create_connection((host, port), timeout=5)
    sock.sendall(pkt(1, 3, password))
    resp = read_pkt(sock)
    if resp[0] == -1:
        print("AUTH FAILED"); return 1
    cmds = sys.argv[3:]
    multi = len(cmds) > 1 or any(";" in c for c in cmds)
    for i, cmd in enumerate(cmds):
        for c in cmd.split(";"):
            c = c.strip()
            if not c:
                continue
            sock.sendall(pkt(10 + i, 2, c))
            time.sleep(0.25)
            try:
                _, _, body = read_pkt(sock)
            except socket.timeout:
                body = ""
            print(f"> {c}\n{body}")
    sock.close()
    return 0

if __name__ == "__main__":
    sys.exit(main())

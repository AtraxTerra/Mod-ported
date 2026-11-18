#!/usr/bin/env python3
"""Minimal Minecraft RCON client: send commands, print one tab-separated result line each.

Usage: python3 -I rcon.py --port 25575 --password ci < commands.txt > results.tsv

Each output line is `<command>\t<response>` with newlines in the response flattened to spaces.
The protocol is Source RCON: little-endian int32 length, request id, type, then a NUL-terminated
body and one more NUL. Type 3 authenticates, type 2 runs a command, replies come back as type 0.
"""
import argparse
import socket
import struct
import sys
import time


def packet(request_id: int, kind: int, body: str) -> bytes:
    payload = body.encode("utf-8") + b"\x00\x00"
    return struct.pack("<iii", len(payload) + 8, request_id, kind) + payload


def read_exact(sock: socket.socket, n: int) -> bytes:
    data = b""
    while len(data) < n:
        chunk = sock.recv(n - len(data))
        if not chunk:
            raise ConnectionError("RCON connection closed")
        data += chunk
    return data


def read_packet(sock: socket.socket) -> tuple[int, int, str]:
    (length,) = struct.unpack("<i", read_exact(sock, 4))
    data = read_exact(sock, length)
    request_id, kind = struct.unpack("<ii", data[:8])
    return request_id, kind, data[8:-2].decode("utf-8", errors="replace")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--host", default="127.0.0.1")
    ap.add_argument("--port", type=int, default=25575)
    ap.add_argument("--password", required=True)
    ap.add_argument("--connect-timeout", type=float, default=60.0)
    opts = ap.parse_args()

    deadline = time.time() + opts.connect_timeout
    while True:
        try:
            sock = socket.create_connection((opts.host, opts.port), timeout=30)
            break
        except OSError:
            if time.time() > deadline:
                print("could not connect to RCON", file=sys.stderr)
                return 2
            time.sleep(1)

    with sock:
        sock.sendall(packet(1, 3, opts.password))
        request_id, _, _ = read_packet(sock)
        if request_id == -1:
            print("RCON authentication failed", file=sys.stderr)
            return 2
        for n, line in enumerate(sys.stdin, start=2):
            command = line.strip()
            if not command or command.startswith("#"):
                continue
            # Minecraft splits replies longer than 4096 bytes into several packets with the same id.
            # A packet of an unknown type is answered once, after them, so it marks the end.
            marker = n + 1_000_000
            sock.sendall(packet(n, 2, command) + packet(marker, 99, ""))
            body = ""
            while True:
                request_id, _, part = read_packet(sock)
                if request_id == marker:
                    break
                body += part
            print(f"{command}\t{' '.join(body.split())}", flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())

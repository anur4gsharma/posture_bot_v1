"""
ws_server.py — WebSocket server for Android app communication.
===============================================================
Runs an async WebSocket server in a background thread. The main
OpenCV loop (running on the main thread) calls broadcast() to push
posture data. The Android app connects and receives JSON updates.

Communication:
    Python → App:  {"type":"posture_update", "state":"GOOD", "metrics":{...}, ...}
    App → Python:  {"command":"START_CALIBRATION"}

The server uses a thread-safe queue so the main thread can read
commands sent by the app without blocking.

Usage:
    server = PostureWebSocketServer()
    server.start()                          # Starts in background thread
    server.broadcast({"state": "GOOD"})     # Send from main thread
    cmd = server.get_pending_command()       # Check for app commands
    server.stop()
"""

import asyncio
import json
import threading
import queue
import time

try:
    import websockets
    HAS_WEBSOCKETS = True
except ImportError:
    HAS_WEBSOCKETS = False

from posture_monitor import config


class PostureWebSocketServer:
    """Async WebSocket server that runs in a background thread."""

    def __init__(self, host=None, port=None):
        self.host = host or config.WS_HOST
        self.port = port or config.WS_PORT

        self._clients = set()           # Connected WebSocket clients
        self._command_queue = queue.Queue()  # Thread-safe command queue
        self._loop = None               # Asyncio event loop (in bg thread)
        self._thread = None             # Background thread
        self._running = False
        self._last_broadcast = 0

    def start(self):
        """Start the WebSocket server in a background thread."""
        if not HAS_WEBSOCKETS:
            print("[WARN] 'websockets' not installed. Run: pip install websockets")
            print("       Android app connection disabled.")
            return

        self._running = True
        self._thread = threading.Thread(target=self._run_loop, daemon=True)
        self._thread.start()
        print(f"[OK] WebSocket server starting on ws://{self.host}:{self.port}")

    def _run_loop(self):
        """Run the asyncio event loop in the background thread."""
        self._loop = asyncio.new_event_loop()
        asyncio.set_event_loop(self._loop)
        self._loop.run_until_complete(self._serve())

    async def _serve(self):
        """Start the WebSocket server and run forever."""
        try:
            async with websockets.serve(self._handle_client, self.host, self.port):
                print(f"[OK] WebSocket server listening on ws://{self.host}:{self.port}")
                while self._running:
                    await asyncio.sleep(0.1)
        except Exception as e:
            print(f"[ERROR] WebSocket server error: {e}")

    async def _handle_client(self, websocket):
        """Handle a single client connection (the Android app)."""
        client_addr = websocket.remote_address
        print(f"[WS] Client connected: {client_addr}")
        self._clients.add(websocket)

        try:
            async for message in websocket:
                # Parse incoming commands from the app
                try:
                    data = json.loads(message)
                    command = data.get("command", "")
                    if command:
                        self._command_queue.put(command)
                        print(f"[WS] Command received: {command}")
                except json.JSONDecodeError:
                    print(f"[WS] Invalid JSON from client: {message}")
        except websockets.ConnectionClosed:
            pass
        finally:
            self._clients.discard(websocket)
            print(f"[WS] Client disconnected: {client_addr}")

    def broadcast(self, data):
        """
        Send a message to all connected clients (thread-safe).
        Called from the main thread. Rate-limited by WS_BROADCAST_INTERVAL.

        Args:
            data: dict to be JSON-serialized and sent.
        """
        if not self._loop or not self._clients:
            return

        # Rate limit broadcasts
        now = time.time()
        if now - self._last_broadcast < config.WS_BROADCAST_INTERVAL:
            return
        self._last_broadcast = now

        message = json.dumps(data)

        # Schedule the broadcast on the async event loop
        asyncio.run_coroutine_threadsafe(
            self._async_broadcast(message), self._loop
        )

    async def _async_broadcast(self, message):
        """Send a message to all connected clients (async)."""
        if not self._clients:
            return

        # Send to all clients, remove any that have disconnected
        disconnected = set()
        for client in self._clients:
            try:
                await client.send(message)
            except websockets.ConnectionClosed:
                disconnected.add(client)

        self._clients -= disconnected

    def get_pending_command(self):
        """
        Get the next pending command from the app (non-blocking).
        Returns the command string, or None if no commands waiting.
        """
        try:
            return self._command_queue.get_nowait()
        except queue.Empty:
            return None

    @property
    def client_count(self):
        """Number of currently connected clients."""
        return len(self._clients)

    def stop(self):
        """Stop the WebSocket server."""
        self._running = False
        if self._loop:
            self._loop.call_soon_threadsafe(self._loop.stop)
        if self._thread:
            self._thread.join(timeout=2)
        print("[OK] WebSocket server stopped.")

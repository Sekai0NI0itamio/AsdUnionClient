# RouterTunnel Manual

RouterTunnel is a macOS-only TCP proxy that forces outbound connections to use a specific physical network interface (for example, `en0`), bypassing VPN routing rules. It provides three local listeners:

- A Minecraft tunnel and control port on `127.0.0.1:25560`
- An HTTP/HTTPS proxy on `127.0.0.1:25561`
- A SOCKS5 proxy on `127.0.0.1:1080`

This manual focuses on how to use Python 3 applications with RouterTunnel's proxy features to bypass VPN for specific connections.

**Important**: RouterTunnel binds only to localhost and has no authentication. Do not expose it to untrusted networks.

## Quick Start

1. Build the binary:

```bash
cd router_tunnel
./build.sh
```

2. Run the tunnel:

```bash
./router_tunnel
```

3. Test the HTTP proxy with Python:

```python
import requests
proxies = {
    "http": "http://127.0.0.1:25561",
    "https": "http://127.0.0.1:25561",
}
r = requests.get("https://example.com", proxies=proxies)
print(r.status_code)
```

## CLI Reference

```bash
./router_tunnel [OPTIONS]
```

Options:

- `--port PORT` Listen port for the Minecraft tunnel and control protocol. Default `25560`.
- `--http-proxy PORT` HTTP proxy port. Default `25561`.
- `--no-http-proxy` Disable the HTTP proxy listener.
- `--socks5-proxy PORT` SOCKS5 proxy port. Default `1080`.
- `--no-socks5-proxy` Disable the SOCKS5 proxy listener.
- `--socks5-user USER` SOCKS5 username for authentication (optional).
- `--socks5-pass PASS` SOCKS5 password for authentication (optional).
- `--interface IFACE` Force a specific interface, for example `en0`.
- `--always-route` Always install scoped default route at startup.
- `--skip-test` Skip the initial connectivity test.
- `--help` Show usage.

## How Routing Works

- RouterTunnel uses the macOS socket option `IP_BOUND_IF` to bind outbound connections to a specific interface by index.
- By default, RouterTunnel does **not** force a scoped default route at startup. It only attempts a scoped route fallback when a connectivity probe fails.
- Use `--always-route` if you explicitly want startup route installation.
- It performs an optional connectivity test by connecting to `1.1.1.1:443` through the bound interface.

If you see a connectivity failure, you may need to add a scoped route manually (see Troubleshooting).

## Ports And Protocols

### 1. Control + Minecraft Port (`127.0.0.1:25560`)

This port serves two purposes:

- Minecraft proxying (FDPClient uses this)
- A tiny control protocol for health checks and route refresh

The first byte determines the mode:

- `0x00` Health check. Returns a length-prefixed JSON status and closes.
- `0x01` Refresh. Re-detects gateway; with `--always-route`, also re-applies scoped route setup.
- Any other byte is treated as the start of a Minecraft handshake packet.

The Minecraft tunnel is not a generic TCP proxy. Use the HTTP proxy or SOCKS5 proxy for general web traffic.

### 2. HTTP/HTTPS Proxy (`127.0.0.1:25561`)

This is the recommended integration point for most applications. It supports:

- HTTP/1.x proxy requests with absolute-form or origin-form targets
- HTTPS via `CONNECT host:port`, then raw TCP tunneling

The proxy does not decrypt TLS. It simply tunnels bytes after the `CONNECT` handshake.

### 3. SOCKS5 Proxy (`127.0.0.1:1080`)

This is a standard SOCKS5 proxy (RFC 1928). It supports:

- **No authentication** — clients that do not require credentials
- **Username/Password authentication** (RFC 1929) — when `--socks5-user` and `--socks5-pass` are provided
- **TCP CONNECT** — resolves domain names and tunnels TCP traffic

UDP ASSOCIATE and BIND are not supported. Only TCP CONNECT.

## Using Python 3 with RouterTunnel

RouterTunnel can proxy Python 3 requests through three main approaches: environment variables, HTTP/SOCKS5 libraries, and direct socket programming.

### Method 1: Environment Variables (Most CLI Tools)

Many Python libraries and tools respect standard proxy environment variables:

```bash
export HTTP_PROXY=http://127.0.0.1:25561
export HTTPS_PROXY=http://127.0.0.1:25561
export NO_PROXY=localhost,127.0.0.1
```

**Python Example with Environment Variables:**

```python
import os
import requests

os.environ['HTTP_PROXY'] = 'http://127.0.0.1:25561'
os.environ['HTTPS_PROXY'] = 'http://127.0.0.1:25561'
os.environ['NO_PROXY'] = 'localhost,127.0.0.1'

r = requests.get("https://example.com")
print(r.status_code)
```

**Using urllib3:**

```python
import os
import urllib3

os.environ['HTTP_PROXY'] = 'http://127.0.0.1:25561'
os.environ['HTTPS_PROXY'] = 'http://127.0.0.1:25561'

http = urllib3.ProxyManager('http://127.0.0.1:25561')
r = http.request('GET', 'https://example.com')
print(r.status)
```

### Method 2: Explicit Proxy Configuration (Recommended)

This approach explicitly configures proxies without affecting system-wide settings.

**Python Requests Library:**

```python
import requests

proxies = {
    "http": "http://127.0.0.1:25561",
    "https": "http://127.0.0.1:25561",
}

r = requests.get("https://example.com", proxies=proxies, timeout=10)
print(r.status_code)
print(r.text[:200])
```

**Python Requests with HTTPS:**

```python
import requests
import json

proxies = {
    "http": "http://127.0.0.1:25561",
    "https": "http://127.0.0.1:25561",
}

try:
    response = requests.get(
        "https://api.github.com/users/octocat",
        proxies=proxies,
        timeout=10
    )
    print(f"Status: {response.status_code}")
    data = response.json()
    print(f"Name: {data.get('name', 'N/A')}")
    print(f"Location: {data.get('location', 'N/A')}")
except requests.exceptions.RequestException as e:
    print(f"Error: {e}")
```

**Session with Automatic Retries:**

```python
import requests
from requests.adapters import HTTPAdapter
from urllib3.util.retry import Retry

proxies = {
    "http": "http://127.0.0.1:25561",
    "https": "http://127.0.0.1:25561",
}

session = requests.Session()
session.proxies.update(proxies)

retry_strategy = Retry(
    total=3,
    backoff_factor=1,
    status_forcelist=[429, 500, 502, 503, 504],
)
adapter = HTTPAdapter(max_retries=retry_strategy)
session.mount("http://", adapter)
session.mount("https://", adapter)

r = session.get("https://httpbin.org/get")
print(r.json())
```

### Method 3: Using SOCKS5 Proxy

For applications that support SOCKS5 protocol:

**Basic SOCKS5 Connection:**

```python
import socket

sock = socket.create_connection(("127.0.0.1", 1080))

sock.sendall(b"\x05\x01\x00")
resp = sock.recv(2)
assert resp == b"\x05\x00"

host = b"example.com"
req = b"\x05\x01\x00\x03" + bytes([len(host)]) + host + b"\x00\x50"
sock.sendall(req)
resp = sock.recv(10)
assert resp[1] == 0x00

sock.sendall(b"GET / HTTP/1.1\r\nHost: example.com\r\n\r\n")
print(sock.recv(4096).decode())
sock.close()
```

**Using PySocks Library:**

```bash
pip install PySocks
```

```python
import socket
import socks

socks.set_default_proxy(socks.SOCKS5, "127.0.0.1", 1080)
socket.socket = socks.socksocket

import requests

r = requests.get("https://example.com")
print(r.status_code)
```

**SOCKS5 with Authentication:**

```python
import socket
import socks

socks.set_default_proxy(
    socks.SOCKS5,
    "127.0.0.1",
    1080,
    username="myuser",
    password="mypass"
)
socket.socket = socks.socksocket

import requests

r = requests.get("https://example.com", timeout=10)
print(r.status_code)
```

### Method 4: HTTP Proxy with urllib.request

Python's built-in `urllib.request` supports HTTP proxies:

```python
import urllib.request

proxy_handler = urllib.request.ProxyHandler({
    'http': 'http://127.0.0.1:25561',
    'https': 'http://127.0.0.1:25561',
})

opener = urllib.request.build_opener(proxy_handler)

try:
    response = opener.open('https://example.com', timeout=10)
    print(f"Status: {response.status}")
    html = response.read().decode('utf-8')
    print(f"Content length: {len(html)}")
except urllib.error.URLError as e:
    print(f"Error: {e.reason}")
```

### Method 5: Asynchronous HTTP with aiohttp

For async applications:

```bash
pip install aiohttp
```

```python
import aiohttp
import asyncio

async def fetch_with_proxy():
    proxy = "http://127.0.0.1:25561"
    
    async with aiohttp.ClientSession() as session:
        async with session.get(
            'https://example.com',
            proxy=proxy,
            timeout=aiohttp.ClientTimeout(total=10)
        ) as response:
            print(f"Status: {response.status}")
            content = await response.text()
            print(f"Length: {len(content)}")

asyncio.run(fetch_with_proxy())
```

**Multiple Concurrent Requests:**

```python
import aiohttp
import asyncio

async def fetch_url(session, url, proxy):
    try:
        async with session.get(url, proxy=proxy) as response:
            return url, response.status, await response.text()
    except Exception as e:
        return url, 'ERROR', str(e)

async def main():
    proxy = "http://127.0.0.1:25561"
    urls = [
        "https://example.com",
        "https://httpbin.org/ip",
        "https://api.github.com/users/octocat",
    ]
    
    async with aiohttp.ClientSession() as session:
        tasks = [fetch_url(session, url, proxy) for url in urls]
        results = await asyncio.gather(*tasks)
        
        for url, status, content in results:
            print(f"{url}: {status}")
            if status == 200:
                print(f"  Content preview: {content[:100]}")
            print()

asyncio.run(main())
```

### Method 6: Web Scraping with BeautifulSoup

```python
import requests
from bs4 import BeautifulSoup

proxies = {
    "http": "http://127.0.0.1:25561",
    "https": "http://127.0.0.1:25561",
}

try:
    response = requests.get(
        "https://news.ycombinator.com",
        proxies=proxies,
        timeout=15
    )
    soup = BeautifulSoup(response.text, 'html.parser')
    
    titles = soup.find_all('span', class_='titleline')
    for i, title in enumerate(titles[:10], 1):
        link = title.find('a')
        if link:
            print(f"{i}. {link.get_text()}")
            print(f"   URL: {link.get('href')}")
except requests.exceptions.RequestException as e:
    print(f"Error: {e}")
```

### Method 7: API Calls with Error Handling

```python
import requests
import json

def api_request(url, proxies, method='GET', data=None, headers=None):
    try:
        if method == 'GET':
            response = requests.get(
                url,
                proxies=proxies,
                timeout=10,
                headers=headers
            )
        elif method == 'POST':
            response = requests.post(
                url,
                json=data,
                proxies=proxies,
                timeout=10,
                headers=headers
            )
        else:
            raise ValueError(f"Unsupported method: {method}")
        
        response.raise_for_status()
        return response.json()
        
    except requests.exceptions.ProxyError as e:
        return {"error": "Proxy connection failed", "details": str(e)}
    except requests.exceptions.ConnectionError as e:
        return {"error": "Connection failed", "details": str(e)}
    except requests.exceptions.Timeout as e:
        return {"error": "Request timed out", "details": str(e)}
    except requests.exceptions.HTTPError as e:
        return {"error": f"HTTP {e.response.status_code}", "details": str(e)}
    except Exception as e:
        return {"error": "Unexpected error", "details": str(e)}

proxies = {
    "http": "http://127.0.0.1:25561",
    "https": "http://127.0.0.1:25561",
}

result = api_request(
    "https://api.github.com/repos/python/cpython",
    proxies
)
print(json.dumps(result, indent=2))
```

### Method 8: Downloading Files

```python
import requests
import os

proxies = {
    "http": "http://127.0.0.1:25561",
    "https": "http://127.0.0.1:25561",
}

def download_file(url, filename, proxies):
    try:
        response = requests.get(url, proxies=proxies, stream=True, timeout=30)
        response.raise_for_status()
        
        total_size = int(response.headers.get('content-length', 0))
        print(f"Downloading {filename} ({total_size:,} bytes)")
        
        downloaded = 0
        with open(filename, 'wb') as f:
            for chunk in response.iter_content(chunk_size=8192):
                if chunk:
                    f.write(chunk)
                    downloaded += len(chunk)
                    if total_size > 0:
                        percent = (downloaded / total_size) * 100
                        print(f"\rProgress: {percent:.1f}%", end='')
        
        print(f"\nDownloaded: {filename}")
        return True
        
    except Exception as e:
        print(f"Error downloading {url}: {e}")
        return False

download_file(
    "https://releases.ubuntu.com/22.04/ubuntu-22.04.3-live-server-amd64.iso",
    "ubuntu.iso",
    proxies
)
```

### Method 9: Testing Proxy Connectivity

```python
import requests
import socket
import json

def test_router_tunnel():
    results = {
        "control_port": False,
        "http_proxy": False,
        "socks5_proxy": False,
        "internet_via_http": False,
        "internet_via_socks5": False,
    }
    
    print("Testing RouterTunnel connectivity...")
    
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.settimeout(2)
        try:
            s.connect(("127.0.0.1", 25560))
            s.sendall(b"\x00")
            n = s.recv(1)[0]
            data = s.recv(n)
            status = json.loads(data.decode("utf-8"))
            results["control_port"] = True
            print(f"✓ Control port: {status}")
        except Exception as e:
            print(f"✗ Control port failed: {e}")
    
    proxies = {
        "http": "http://127.0.0.1:25561",
        "https": "http://127.0.0.1:25561",
    }
    
    try:
        r = requests.get("https://example.com", proxies=proxies, timeout=5)
        results["http_proxy"] = True
        print(f"✓ HTTP proxy: status {r.status_code}")
    except Exception as e:
        print(f"✗ HTTP proxy failed: {e}")
    
    try:
        r = requests.get("https://httpbin.org/ip", proxies=proxies, timeout=5)
        results["internet_via_http"] = True
        print(f"✓ Internet via HTTP proxy: {r.json()}")
    except Exception as e:
        print(f"✗ Internet via HTTP proxy failed: {e}")
    
    try:
        import socks
        socks.set_default_proxy(socks.SOCKS5, "127.0.0.1", 1080)
        socket.socket = socks.socksocket
        
        r = requests.get("https://example.com", timeout=5)
        results["socks5_proxy"] = True
        results["internet_via_socks5"] = True
        print(f"✓ SOCKS5 proxy: status {r.status_code}")
        
    except ImportError:
        print("⚠ PySocks not installed, skipping SOCKS5 test")
    except Exception as e:
        print(f"✗ SOCKS5 proxy failed: {e}")
    finally:
        socket.socket = socks.original_socket if hasattr(socks, 'original_socket') else socket.socket
    
    print("\n=== Summary ===")
    for test, passed in results.items():
        status = "✓" if passed else "✗"
        print(f"{status} {test}")

test_router_tunnel()
```

## Integrating Other Applications

### curl

```bash
curl -x http://127.0.0.1:25561 https://example.com
```

### wget

```bash
wget -e use_proxy=yes -e http_proxy=127.0.0.1:25561 https://example.com
```

### pip

```bash
pip install --proxy http://127.0.0.1:25561 some-package
```

### git

```bash
git config --global http.proxy http://127.0.0.1:25561
git clone https://github.com/example/repo.git
git config --global --unset http.proxy
```

### youtube-dl / yt-dlp

```bash
yt-dlp --proxy http://127.0.0.1:25561 "https://www.youtube.com/watch?v=..."
```

## Health Check And Refresh API

Use this if your app needs to detect whether RouterTunnel is running or refresh routing without restarting it.

**Request:**

- Open a TCP connection to `127.0.0.1:25560`
- Send a single byte

**Responses:**

- Health check byte `0x00` returns:

```json
{"status":"ok","interface":"en0","ip":"192.168.50.139"}
```

- Refresh byte `0x01` returns:

```json
{"status":"refreshed","interface":"en0","ip":"192.168.50.139","gateway":"192.168.50.1"}
```

The response format is:

- 1 byte length `N` (0–254) followed by `N` bytes of UTF-8 JSON
- If the length byte is `0xFF`, the next 2 bytes are a big-endian `uint16` length, followed by that many UTF-8 JSON bytes

**Python Status Check:**

```python
import socket
import json

def check_router_tunnel_status():
    try:
        with socket.create_connection(("127.0.0.1", 25560), timeout=2) as s:
            s.sendall(b"\x00")
            n = s.recv(1)[0]
            data = s.recv(n)
            status = json.loads(data.decode("utf-8"))
            
            print("RouterTunnel Status:")
            print(f"  Interface: {status.get('interface')}")
            print(f"  IP: {status.get('ip')}")
            print(f"  Status: {status.get('status')}")
            return status
    except Exception as e:
        print(f"RouterTunnel not running: {e}")
        return None

check_router_tunnel_status()
```

## HTTP/HTTPS Proxy Behavior

- The proxy reads up to 16 KB of headers and then forwards the request.
- For HTTP requests, it rewrites the request line to origin-form and preserves headers, omitting `Proxy-Connection`.
- For HTTPS, it requires `CONNECT` and then relays raw TCP.
- It supports IPv4 DNS resolution (`A` records) only.

Common HTTP error responses include `400 Bad Request`, `414 Request-URI Too Long`, `431 Request Header Fields Too Large`, and `502 Bad Gateway`.

## Troubleshooting

- If you see `Connectivity test FAILED`, the VPN likely removed the default route for the physical interface. Add a scoped route manually:

```bash
sudo route add default <gateway_ip> -ifscope <interface>
```

- To find the gateway for an interface:

```bash
route -n get default -ifscope en0
```

- If the HTTP proxy is disabled, start RouterTunnel without `--no-http-proxy`.

- If Python requests timeout, check that RouterTunnel is running and listening on the correct ports:

```bash
lsof -i :25560 -i :25561 -i :1080
```

- To verify RouterTunnel is working, test the health check:

```python
import socket
sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
sock.connect(("127.0.0.1", 25560))
sock.send(b"\x00")
n = sock.recv(1)[0]
print(sock.recv(n).decode())
sock.close()
```

## Limitations And Security Notes

- macOS only (uses `IP_BOUND_IF`).
- IPv4 only. No IPv6 support.
- TCP only. No UDP.
- No authentication, access control, or encryption on the local proxy.
- HTTP/2 and WebSocket upgrades are not explicitly handled.
- Idle connections time out after 5 minutes of inactivity.

## Advanced: Creating a Proxy Wrapper

For complex applications that need persistent proxy configuration:

```python
import requests
from contextlib import contextmanager

class RouterTunnelProxy:
    def __init__(self, http_port=25561, socks5_port=1080):
        self.http_proxy = f"http://127.0.0.1:{http_port}"
        self.socks5_proxy = f"socks5://127.0.0.1:{socks5_port}"
        self.proxies = {
            "http": self.http_proxy,
            "https": self.http_proxy,
        }
    
    def get(self, url, **kwargs):
        return requests.get(url, proxies=self.proxies, **kwargs)
    
    def post(self, url, **kwargs):
        return requests.post(url, proxies=self.proxies, **kwargs)
    
    def session(self):
        session = requests.Session()
        session.proxies.update(self.proxies)
        return session

@contextmanager
def router_tunnel_session():
    proxy = RouterTunnelProxy()
    session = proxy.session()
    try:
        yield session
    finally:
        session.close()

with router_tunnel_session() as session:
    r = session.get("https://api.github.com/users")
    print(f"Got {len(r.json())} users")
```

## Performance Considerations

1. **Connection Reuse**: Use sessions for multiple requests to reuse TCP connections
2. **Timeout Settings**: Set reasonable timeouts (10-30 seconds) to avoid hanging
3. **Error Handling**: Always implement retry logic for production applications
4. **Body Streaming**: For large downloads, use streaming (`stream=True`)
5. **Concurrent Requests**: For high throughput, use connection pooling or async libraries

```python
from requests.adapters import HTTPAdapter
from requests.packages.urllib3.util.retry import Retry
import requests

def create_optimized_session():
    session = requests.Session()
    
    retry_strategy = Retry(
        total=3,
        backoff_factor=0.5,
        status_forcelist=[500, 502, 503, 504],
    )
    
    adapter = HTTPAdapter(
        max_retries=retry_strategy,
        pool_connections=10,
        pool_maxsize=20
    )
    
    session.mount("http://", adapter)
    session.mount("https://", adapter)
    session.proxies = {
        "http": "http://127.0.0.1:25561",
        "https": "http://127.0.0.1:25561",
    }
    
    return session

session = create_optimized_session()
r = session.get("https://api.github.com/users/octocat")
print(r.json())
```

This manual provides comprehensive coverage of using Python 3 applications with RouterTunnel's proxy features, enabling you to bypass VPN restrictions for specific network requests.

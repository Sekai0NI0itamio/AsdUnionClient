"""
Downloads all images from https://prix.pictet.com/cycles/consumption/laurie-simmons
and saves a text file with all visible page text.

Requirements:
    pip3 install selenium requests
    Also needs ChromeDriver matching your Chrome version, OR use:
    pip3 install webdriver-manager
    (script uses webdriver-manager automatically)
"""

import re
import time
import requests
from pathlib import Path
from selenium import webdriver
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.chrome.service import Service
from selenium.webdriver.common.by import By

try:
    from webdriver_manager.chrome import ChromeDriverManager
    USE_WDM = True
except ImportError:
    USE_WDM = False

URL = "https://prix.pictet.com/cycles/consumption/laurie-simmons"
OUTPUT_DIR = Path("laurie_simmons_download")
OUTPUT_DIR.mkdir(exist_ok=True)


def sanitize_filename(name: str) -> str:
    """Remove characters that are invalid in filenames."""
    name = name.strip().rstrip(".")
    name = re.sub(r'[\\/*?:"<>|]', "", name)
    name = re.sub(r"\s+", " ", name)
    return name


def get_extension_from_url(url: str) -> str:
    """Guess image extension from URL, default to .jpg."""
    url_path = url.split("?")[0]
    ext = Path(url_path).suffix.lower()
    if ext in (".jpg", ".jpeg", ".png", ".gif", ".webp", ".avif"):
        return ext
    return ".jpg"


def launch_browser() -> webdriver.Chrome:
    options = Options()
    options.add_argument("--headless=new")
    options.add_argument("--no-sandbox")
    options.add_argument("--disable-dev-shm-usage")
    options.add_argument("--window-size=1440,900")
    # Disable blink features that block lazy-load triggering
    options.add_argument("--disable-blink-features=AutomationControlled")
    options.add_experimental_option("excludeSwitches", ["enable-automation"])

    if USE_WDM:
        service = Service(ChromeDriverManager().install())
        return webdriver.Chrome(service=service, options=options)
    else:
        return webdriver.Chrome(options=options)


def scroll_to_load_all(driver: webdriver.Chrome):
    """Scroll down incrementally to trigger lazy-loaded images."""
    last_height = driver.execute_script("return document.body.scrollHeight")
    scroll_step = 400
    current_pos = 0

    while True:
        current_pos += scroll_step
        driver.execute_script(f"window.scrollTo(0, {current_pos});")
        time.sleep(0.3)

        new_height = driver.execute_script("return document.body.scrollHeight")
        if current_pos >= new_height:
            break
        last_height = new_height

    # Scroll back to top then bottom once more to ensure everything loaded
    driver.execute_script("window.scrollTo(0, 0);")
    time.sleep(0.5)
    driver.execute_script("window.scrollTo(0, document.body.scrollHeight);")
    time.sleep(1)


def collect_images(driver: webdriver.Chrome) -> list[dict]:
    """Return list of {name, url} for all real (non-placeholder) images."""
    images = []
    seen_urls = set()

    img_elements = driver.find_elements(By.TAG_NAME, "img")
    for img in img_elements:
        src = img.get_attribute("src") or ""
        alt = img.get_attribute("alt") or ""

        # Skip base64 placeholders and tiny tracking pixels
        if src.startswith("data:") or not src.startswith("http"):
            continue
        if src in seen_urls:
            continue
        # Skip portrait/avatar images (optional — remove if you want them too)
        if "portrait" in alt.lower():
            continue

        seen_urls.add(src)
        images.append({"name": alt if alt else f"image_{len(images)+1}", "url": src})

    return images


def download_images(image_list: list[dict]):
    headers = {"User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
                              "AppleWebKit/537.36 (KHTML, like Gecko) "
                              "Chrome/124.0.0.0 Safari/537.36"}
    name_counts: dict[str, int] = {}

    for item in image_list:
        raw_name = sanitize_filename(item["name"])
        ext = get_extension_from_url(item["url"])

        # Handle duplicate names
        if raw_name in name_counts:
            name_counts[raw_name] += 1
            filename = f"{raw_name} ({name_counts[raw_name]}){ext}"
        else:
            name_counts[raw_name] = 0
            filename = f"{raw_name}{ext}"

        dest = OUTPUT_DIR / filename
        try:
            resp = requests.get(item["url"], headers=headers, timeout=15)
            resp.raise_for_status()
            dest.write_bytes(resp.content)
            print(f"  ✅  {filename}")
        except Exception as e:
            print(f"  ❌  {filename}: {e}")


def save_text(driver: webdriver.Chrome):
    """Extract all visible text from the page and save to a .txt file."""
    # Get text via JavaScript to avoid hidden elements
    text = driver.execute_script("""
        function getVisibleText(node) {
            if (node.nodeType === Node.TEXT_NODE) {
                return node.textContent;
            }
            const style = window.getComputedStyle(node);
            if (style && (style.display === 'none' || style.visibility === 'hidden')) {
                return '';
            }
            return Array.from(node.childNodes).map(getVisibleText).join('');
        }
        return getVisibleText(document.body);
    """)

    # Clean up excessive whitespace
    lines = [line.strip() for line in text.splitlines()]
    lines = [line for line in lines if line]
    cleaned = "\n".join(lines)

    txt_path = OUTPUT_DIR / "page_text.txt"
    txt_path.write_text(cleaned, encoding="utf-8")
    print(f"\n  📄  Page text saved to: {txt_path}")


def main():
    print(f"🌐  Loading {URL} ...")
    driver = launch_browser()

    try:
        driver.get(URL)
        time.sleep(3)  # Wait for initial JS render

        print("📜  Scrolling to trigger lazy-loaded images ...")
        scroll_to_load_all(driver)

        print("\n🔍  Collecting image URLs ...")
        image_list = collect_images(driver)
        print(f"    Found {len(image_list)} image(s)\n")

        print("⬇️   Downloading images ...")
        download_images(image_list)

        print("\n📝  Extracting page text ...")
        save_text(driver)

    finally:
        driver.quit()

    print(f"\n✅  Done! Everything saved to: {OUTPUT_DIR.resolve()}")


if __name__ == "__main__":
    main()

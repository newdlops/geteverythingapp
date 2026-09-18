"""Browser checks for the generated install page (requires Playwright + Chromium)."""
import argparse
import asyncio
import hashlib
import json
from pathlib import Path
from playwright.async_api import async_playwright

ROOT = Path(__file__).resolve().parents[1]


async def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--url", default="http://127.0.0.1:8766/install/")
    parser.add_argument("--output", type=Path, default=ROOT / "artifacts/install-qa")
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    release = json.loads((ROOT / "artifacts/install-site/install/release.json").read_text())
    async with async_playwright() as p:
        browser = await p.chromium.launch(executable_path="/opt/homebrew/bin/chromium", headless=True)
        context = await browser.new_context(accept_downloads=True)
        page = await context.new_page()
        errors = []
        page.on("pageerror", lambda error: errors.append(str(error)))
        sizes = {"mobile": (390, 844), "tablet": (768, 1024), "desktop": (1440, 900), "narrow": (320, 740)}
        for name, (width, height) in sizes.items():
            await page.set_viewport_size({"width": width, "height": height})
            response = await page.goto(args.url, wait_until="networkidle", timeout=20000)
            assert response.status == 200
            assert await page.title() == "핫딜모아 테스트 앱 설치"
            assert await page.locator(".qr").evaluate("el => el.complete && el.naturalWidth > 0")
            assert await page.evaluate("document.documentElement.scrollWidth <= innerWidth"), name
            await page.screenshot(path=str(args.output / f"{name}.png"), full_page=True)
        await page.set_viewport_size({"width": 1440, "height": 900})
        await page.goto(args.url, wait_until="networkidle")
        await page.keyboard.press("Tab")
        assert await page.locator(".skip-link").evaluate("el => el === document.activeElement")
        await page.keyboard.press("Enter")
        assert await page.locator("#main").evaluate("el => el === document.activeElement")
        await page.get_by_role("button", name="복사", exact=True).click()
        assert "복사했습니다" in await page.locator("#copy-status").inner_text()
        await page.get_by_text("버전과 파일 정보 확인", exact=True).click()
        assert await page.locator(".file-info").is_visible()
        assert release["sha256"] in await page.locator(".file-info").inner_text()
        await page.screenshot(path=str(args.output / "details-open.png"), full_page=True)
        async with page.expect_download(timeout=30000) as pending:
            await page.get_by_role("link", name="테스트 APK 다운로드").click()
        download = await pending.value
        downloaded = args.output / "downloaded.apk"
        await download.save_as(downloaded)
        assert hashlib.sha256(downloaded.read_bytes()).hexdigest() == release["sha256"]
        assert download.suggested_filename.endswith(".apk")
        assert "다운로드를 요청했습니다" in await page.locator("#download-status").inner_text()
        await page.route("**/releases/*.apk", lambda route: route.fulfill(status=503, body="unavailable"))
        await page.get_by_role("link", name="테스트 APK 다운로드").click()
        await page.locator("#download-status.error").wait_for(state="visible")
        assert await page.locator("#download").get_attribute("aria-busy") is None
        await page.screenshot(path=str(args.output / "download-error.png"), full_page=True)
        await page.unroute("**/releases/*.apk")
        async with page.expect_download(timeout=30000):
            await page.get_by_role("link", name="테스트 APK 다운로드").click()
        await page.add_init_script("Object.defineProperty(navigator, 'clipboard', {value: undefined}); document.execCommand = () => false;")
        await page.reload(wait_until="networkidle")
        await page.get_by_role("button", name="복사", exact=True).click()
        assert "자동 복사가 되지 않았습니다" in await page.locator("#copy-status").inner_text()
        assert await page.locator("#share-url").evaluate("el => el.selectionEnd - el.selectionStart === el.value.length")
        await page.set_viewport_size({"width": 390, "height": 844})
        await page.evaluate("document.documentElement.style.fontSize = '200%'")
        assert await page.evaluate("document.documentElement.scrollWidth <= innerWidth")
        await page.evaluate("window.scrollTo(0, 0)")
        await page.screenshot(path=str(args.output / "large-text.png"), full_page=True)
        nojs = await browser.new_context(java_script_enabled=False, accept_downloads=True, viewport={"width": 390, "height": 844})
        simple = await nojs.new_page()
        await simple.goto(args.url)
        assert not await simple.locator("#copy-link").is_visible()
        async with simple.expect_download(timeout=30000):
            await simple.get_by_role("link", name="테스트 APK 다운로드").click()
        await browser.close()
        assert not errors, errors
        result = {"url": args.url, "viewports": sizes, "sha256": release["sha256"], "checks": ["QR image loaded", "no horizontal overflow", "keyboard skip link", "copy success", "manual copy fallback", "file details", "APK download hash", "download failure and retry", "200% text", "JavaScript disabled download"], "pageErrors": errors}
        (args.output / "result.json").write_text(json.dumps(result, ensure_ascii=False, indent=2))
        print(json.dumps(result, ensure_ascii=False, indent=2))


asyncio.run(main())

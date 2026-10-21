#!/usr/bin/env python3
"""Probe sanitizer output in Chromium. Every HTTP request is intercepted; no traffic leaves the browser.

Requires Python Playwright and a local Chrome/Chromium installation. First run StoredHtmlSecurityAuditTests
to generate target/security-audit/html. This diagnostic writes evidence, not a clean security verdict.
"""
import argparse
import html
import json
from pathlib import Path

from playwright.sync_api import sync_playwright


PAGE_CSP = (
    "default-src 'none'; img-src https: data:; media-src https: data:; font-src data:; "
    "style-src 'unsafe-inline'; script-src 'none'; connect-src 'none'; form-action 'none'; base-uri 'none'"
)
INLINE_CSP = (
    "default-src 'none'; img-src https: data:; media-src https: data:; font-src data:; "
    "style-src 'unsafe-inline'; frame-src 'none'; object-src 'none'; connect-src 'none'; "
    "worker-src 'none'; form-action 'none'"
)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--browser", default="/usr/bin/google-chrome")
    parser.add_argument("--fixtures", type=Path, default=Path("target/security-audit/html"))
    parser.add_argument("--output", type=Path, default=Path("target/security-audit/browser-evidence.json"))
    parser.add_argument("--assert-inert", action="store_true", help="Fail on script execution, active SVG URL or audit pixel request")
    args = parser.parse_args()
    names = ("svg-set-url-create", "svg-animate-url-create", "css-escaped-function-create")
    results = []
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(
            executable_path=args.browser, headless=True,
            args=["--disable-background-networking", "--no-sandbox"],
        )
        version = browser.version
        for name in names:
            sanitized = (args.fixtures / (name + ".html")).read_text()
            for mode in ("sanitizer-alone", "page-csp", "inline-sandbox-and-csp"):
                outbound = []
                console = []
                context = browser.new_context()

                def intercept(route):
                    if route.request.url == "http://audit.local/fixture":
                        headers = {"Content-Type": "text/html; charset=utf-8"}
                        body = sanitized
                        if mode == "page-csp":
                            headers["Content-Security-Policy"] = PAGE_CSP
                        elif mode == "inline-sandbox-and-csp":
                            document = '<meta http-equiv="Content-Security-Policy" content="' + INLINE_CSP + '">' + sanitized
                            body = '<iframe sandbox="allow-same-origin allow-popups allow-popups-to-escape-sandbox" srcdoc="' + html.escape(document, quote=True) + '"></iframe>'
                        route.fulfill(status=200, body=body, headers=headers)
                    else:
                        outbound.append(route.request.url)
                        route.abort()

                context.route("**/*", intercept)
                page = context.new_page()
                page.on("console", lambda message: console.append(message.text))
                page.goto("http://audit.local/fixture", wait_until="load")
                frame = page.frames[1] if mode == "inline-sandbox-and-csp" else page.main_frame
                page.wait_for_timeout(250)
                state = frame.evaluate("""() => {
                    const a = document.querySelector('svg a');
                    const p = document.querySelector('p');
                    return {
                        animatedHref: a?.href?.animVal ?? null,
                        linkTarget: a?.target?.baseVal ?? null,
                        background: p ? getComputedStyle(p).backgroundImage : null
                    };
                }""")
                if frame.locator("svg a").count():
                    frame.locator("svg a").click(force=True, timeout=3000)
                page.wait_for_timeout(150)
                executed = []
                for opened in context.pages:
                    for candidate in opened.frames:
                        try:
                            executed.append(bool(candidate.evaluate("() => window.auditExecuted === 1")))
                        except Exception:
                            pass
                results.append({
                    "fixture": name, "mode": mode, **state,
                    "scriptExecuted": any(executed),
                    "interceptedOutboundRequests": outbound,
                    "browserMessages": console,
                })
                context.close()
        browser.close()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps({"browser": version, "network": "all HTTP requests intercepted", "results": results}, indent=2))
    for result in results:
        print(json.dumps({key: result[key] for key in (
            "fixture", "mode", "animatedHref", "scriptExecuted", "interceptedOutboundRequests"
        )}))
    if args.assert_inert:
        unsafe = [result for result in results if result["scriptExecuted"]
                  or str(result["animatedHref"]).lower().startswith(("javascript:", "vbscript:"))
                  or any("audit.invalid" in url for url in result["interceptedOutboundRequests"])]
        if unsafe:
            raise SystemExit(f"Failed: {len(unsafe)} unsafe browser scenarios; see {args.output}")


if __name__ == "__main__":
    main()

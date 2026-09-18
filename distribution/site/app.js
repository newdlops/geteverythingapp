"use strict";

const copyButton = document.querySelector("#copy-link");
const shareInput = document.querySelector("#share-url");
const copyStatus = document.querySelector("#copy-status");
copyButton.hidden = false;

copyButton.addEventListener("click", async () => {
  let copied = false;
  if (window.isSecureContext && navigator.clipboard) {
    try {
      await navigator.clipboard.writeText(shareInput.value);
      copied = true;
    } catch (_) {
      // Permission can be denied even in a secure context; try the manual path.
    }
  }
  if (!copied) {
    shareInput.focus();
    shareInput.select();
    shareInput.setSelectionRange(0, shareInput.value.length);
    try {
      // This deployment uses HTTP, where the asynchronous Clipboard API is unavailable.
      copied = document.execCommand("copy");
    } catch (_) {
      copied = false;
    }
  }
  copyStatus.classList.toggle("error", !copied);
  copyStatus.textContent = copied
    ? "설치 페이지 링크를 복사했습니다. 다른 기기에 보내주세요."
    : "자동 복사가 되지 않았습니다. 선택된 주소를 길게 누르거나 Ctrl/Cmd+C로 복사해 주세요.";
  if (copied) copyButton.focus();
});

const downloadLink = document.querySelector("#download");
const downloadLabel = document.querySelector("#download-label");
const downloadStatus = document.querySelector("#download-status");
let checking = false;

downloadLink.addEventListener("click", async (event) => {
  if (event.ctrlKey || event.metaKey || event.shiftKey || event.altKey || event.button !== 0) return;
  if (!window.fetch || !window.AbortController) return;
  event.preventDefault();
  if (checking) return;
  checking = true;
  downloadLink.setAttribute("aria-busy", "true");
  downloadLabel.textContent = "다운로드 준비 중…";
  downloadStatus.classList.remove("error");
  downloadStatus.textContent = "설치 파일을 확인하고 있습니다…";
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 10000);
  try {
    const response = await fetch(downloadLink.href, { method: "HEAD", cache: "no-store", signal: controller.signal });
    if (!response.ok || !response.headers.get("content-type")?.includes("application/vnd.android.package-archive")) {
      throw new Error("APK unavailable");
    }
    window.location.assign(downloadLink.href);
    downloadStatus.textContent = "다운로드를 요청했습니다. 브라우저의 다운로드 목록을 확인해 주세요.";
  } catch (_) {
    downloadStatus.classList.add("error");
    downloadStatus.textContent = "설치 파일을 가져오지 못했습니다. 인터넷 연결을 확인하고 다시 눌러주세요.";
  } finally {
    clearTimeout(timeout);
    checking = false;
    downloadLink.removeAttribute("aria-busy");
    downloadLabel.textContent = "테스트 APK 다운로드";
  }
});

import type { CompleteUploadResult, InitUploadResult, PartUrl } from "@lms/api-client";

import { api } from "./client-api";

/**
 * Browser → S3 upload with presigned URLs (docs/05). The bytes never pass through our servers:
 *   1. POST /v1/uploads             declare name/type/size → one PUT URL, or a multipart plan
 *   2. PUT  (S3)                    the file, or its parts (3 at a time, each retried twice)
 *   3. POST /v1/uploads/{id}/complete  the API checks the object before anything may use it
 */
export type UploadPurpose = "VIDEO_SOURCE" | "DOCUMENT";

export type UploadProgress = {
  phase: "uploading" | "verifying";
  /** 0–100 */
  percent: number;
  partsDone?: number;
  partsTotal?: number;
};

export type UploadHandle = {
  fileId: Promise<string>;
  done: Promise<CompleteUploadResult>;
  cancel: () => void;
};

const PART_CONCURRENCY = 3;
const PART_RETRIES = 2;
const PARTS_PER_REQUEST = 20;

export class UploadCanceled extends Error {
  constructor() {
    super("업로드를 취소했습니다.");
    this.name = "UploadCanceled";
  }
}

/** XHR rather than fetch: fetch has no upload progress events. */
function put(
  url: string,
  body: Blob,
  contentType: string | undefined,
  signal: AbortSignal,
  onProgress: (loaded: number) => void,
): Promise<string | null> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open("PUT", url);
    if (contentType) xhr.setRequestHeader("Content-Type", contentType);
    xhr.upload.onprogress = (e) => onProgress(e.loaded);
    xhr.onload = () =>
      xhr.status >= 200 && xhr.status < 300
        ? resolve(xhr.getResponseHeader("ETag"))
        : reject(new Error(`저장소가 업로드를 거부했습니다 (${xhr.status}).`));
    xhr.onerror = () => reject(new Error("네트워크 오류로 업로드가 끊겼습니다."));
    xhr.onabort = () => reject(new UploadCanceled());
    signal.addEventListener("abort", () => xhr.abort(), { once: true });
    xhr.send(body);
  });
}

/** Seconds of video, read locally from the file's metadata (a hint until transcoding reports). */
export function probeDuration(file: File): Promise<number | undefined> {
  return new Promise((resolve) => {
    const video = document.createElement("video");
    const url = URL.createObjectURL(file);
    const finish = (value?: number) => {
      URL.revokeObjectURL(url);
      resolve(value);
    };
    video.preload = "metadata";
    video.onloadedmetadata = () => finish(Number.isFinite(video.duration) ? Math.round(video.duration) : undefined);
    video.onerror = () => finish(undefined);
    window.setTimeout(() => finish(undefined), 5000);
    video.src = url;
  });
}

export function startUpload(
  file: File,
  purpose: UploadPurpose,
  onProgress: (p: UploadProgress) => void,
): UploadHandle {
  const controller = new AbortController();
  let resolveId!: (id: string) => void;
  let rejectId!: (e: unknown) => void;
  const fileId = new Promise<string>((res, rej) => {
    resolveId = res;
    rejectId = rej;
  });
  fileId.catch(() => undefined);

  const done = (async () => {
    const contentType = file.type || "application/octet-stream";
    const duration = purpose === "VIDEO_SOURCE" ? probeDuration(file) : Promise.resolve(undefined);
    let init: InitUploadResult;
    try {
      ({ data: init } = await api<InitUploadResult>("POST", "/v1/uploads", {
        body: { purpose, fileName: file.name, contentType, sizeBytes: file.size },
      }));
      resolveId(init.fileId);
    } catch (error) {
      rejectId(error);
      throw error;
    }

    try {
      let parts: { partNumber: number; etag: string }[] | undefined;
      if (init.mode === "SINGLE") {
        await put(init.url!, file, contentType, controller.signal, (loaded) =>
          onProgress({ phase: "uploading", percent: Math.round((loaded / file.size) * 100) }),
        );
      } else {
        parts = await uploadParts(file, init, controller.signal, onProgress);
      }
      onProgress({ phase: "verifying", percent: 100 });
      const { data } = await api<CompleteUploadResult>("POST", `/v1/uploads/${init.fileId}/complete`, {
        body: { parts, durationSeconds: await duration },
      });
      return data;
    } catch (error) {
      // Free the half-written object / open multipart upload on S3 (best effort).
      void api("DELETE", `/v1/uploads/${init.fileId}`).catch(() => undefined);
      throw controller.signal.aborted ? new UploadCanceled() : error;
    }
  })();

  return { fileId, done, cancel: () => controller.abort() };
}

async function uploadParts(
  file: File,
  init: InitUploadResult,
  signal: AbortSignal,
  onProgress: (p: UploadProgress) => void,
): Promise<{ partNumber: number; etag: string }[]> {
  const partSize = init.partSizeBytes!;
  const total = init.partCount!;
  const loaded = new Map<number, number>();
  const etags: { partNumber: number; etag: string }[] = [];
  const report = () =>
    onProgress({
      phase: "uploading",
      percent: Math.round(([...loaded.values()].reduce((a, b) => a + b, 0) / file.size) * 100),
      partsDone: etags.length,
      partsTotal: total,
    });

  for (let first = 1; first <= total; first += PARTS_PER_REQUEST) {
    const numbers = Array.from({ length: Math.min(PARTS_PER_REQUEST, total - first + 1) }, (_, i) => first + i);
    // URLs are asked for in batches as the upload progresses, so none expires while waiting.
    const { data } = await api<{ parts: PartUrl[] }>("POST", `/v1/uploads/${init.fileId}/parts`, {
      body: { partNumbers: numbers },
    });
    const queue = [...data.parts];
    const worker = async () => {
      for (let part = queue.shift(); part; part = queue.shift()) {
        const start = (part.partNumber - 1) * partSize;
        const chunk = file.slice(start, Math.min(file.size, start + partSize));
        for (let attempt = 0; ; attempt++) {
          try {
            const etag = await put(part.url, chunk, undefined, signal, (n) => {
              loaded.set(part!.partNumber, n);
              report();
            });
            if (!etag) throw new Error("저장소가 파트 확인값(ETag)을 돌려주지 않았습니다.");
            etags.push({ partNumber: part.partNumber, etag });
            report();
            break;
          } catch (error) {
            if (signal.aborted || attempt >= PART_RETRIES) throw error;
          }
        }
      }
    };
    await Promise.all(Array.from({ length: PART_CONCURRENCY }, worker));
  }
  return etags.sort((a, b) => a.partNumber - b.partNumber);
}

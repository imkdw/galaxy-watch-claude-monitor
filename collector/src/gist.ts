const TIMEOUT_MS = 10_000;

export class GistError extends Error {
  override name = "GistError";
  readonly status: number;
  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

export async function patchGistFile(
  gistId: string,
  filename: string,
  content: string,
  token: string,
  fetch: typeof globalThis.fetch,
): Promise<void> {
  let res: Response;
  try {
    res = await fetch(`https://api.github.com/gists/${encodeURIComponent(gistId)}`, {
      method: "PATCH",
      headers: {
        Authorization: `Bearer ${token}`,
        Accept: "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ files: { [filename]: { content } } }),
      signal: AbortSignal.timeout(TIMEOUT_MS),
    });
  } catch (e) {
    throw new GistError(0, `Gist 업로드 네트워크 오류: ${(e as Error).message}`);
  }
  if (!res.ok) {
    const body = await res.text().catch(() => "");
    throw new GistError(res.status, `Gist 업로드 실패 HTTP ${res.status}: ${body.slice(0, 200)}`);
  }
}

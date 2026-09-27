export type RecordedRequest = { method: string; url: string; headers: Record<string, string>; body: string | null; signal: AbortSignal | null };

export type Route = {
  method?: string;
  url: string | RegExp;
  respond: (req: RecordedRequest) => Response | Promise<Response>;
};

export function fakeFetch(routes: Route[]) {
  const calls: RecordedRequest[] = [];
  const fetch = (async (input: string | URL | Request, init: RequestInit = {}) => {
    const url = String(input instanceof Request ? input.url : input);
    const req: RecordedRequest = {
      method: (init.method ?? "GET").toUpperCase(),
      url,
      headers: Object.fromEntries(new Headers(init.headers).entries()),
      body: typeof init.body === "string" ? init.body : null,
      signal: init.signal ?? null,
    };
    calls.push(req);
    const route = routes.find(
      (r) => (r.method ?? "GET").toUpperCase() === req.method && (typeof r.url === "string" ? r.url === url : r.url.test(url)),
    );
    if (!route) throw new Error(`fakeFetch: 라우트 없음 ${req.method} ${url}`);
    return route.respond(req);
  }) as typeof globalThis.fetch;
  return { fetch, calls };
}

export const json = (status: number, body: unknown, headers: Record<string, string> = {}) =>
  new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json", ...headers } });

export const networkDown = (): never => {
  throw new TypeError("fetch failed");
};

export function fakeExec(byService: Record<string, string | Error>) {
  const calls: { cmd: string; args: string[] }[] = [];
  const exec = async (cmd: string, args: string[]) => {
    calls.push({ cmd, args });
    const service = args[args.indexOf("-s") + 1] ?? "";
    const out = byService[service];
    if (out === undefined) throw new Error(`security: The specified item could not be found in the keychain. (${service})`);
    if (out instanceof Error) throw out;
    return out;
  };
  return { exec, calls };
}

export function memFs(initial: Record<string, string> = {}) {
  const files = new Map(Object.entries(initial));
  const writes: string[] = [];
  return {
    files,
    writes,
    readFile: async (path: string) => files.get(path) ?? null,
    writeFile: async (path: string, data: string) => {
      writes.push(path);
      files.set(path, data);
    },
  };
}

export const fixedClock = (iso: string) => () => new Date(iso);

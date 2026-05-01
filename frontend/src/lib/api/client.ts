import type {
  ApiEnvelope,
  ApiErrorBody,
  ApiRequestOptions
} from "@/lib/api/types";

type HttpMethod = "GET" | "POST" | "PATCH";

export type ApiTokenProvider = () =>
  | Promise<string | null | undefined>
  | string
  | null
  | undefined;

type ApiErrorParams = {
  status: number;
  statusText: string;
  body?: ApiErrorBody;
};

const JSON_CONTENT_TYPE = "application/json";

let tokenProvider: ApiTokenProvider | null = null;

export class ApiError extends Error {
  readonly code?: string;
  readonly details?: ApiErrorBody["details"];
  readonly status: number;
  readonly statusText: string;
  readonly traceId?: string;

  constructor({ status, statusText, body }: ApiErrorParams) {
    super(body?.message ?? `API request failed with status ${status}`);
    this.name = "ApiError";
    this.status = status;
    this.statusText = statusText;
    this.code = body?.code;
    this.details = body?.details;
    this.traceId = body?.details?.traceId;
  }
}

export function setApiTokenProvider(provider: ApiTokenProvider | null) {
  tokenProvider = provider;
}

export function getApiBaseUrl() {
  return (process.env.NEXT_PUBLIC_API_BASE_URL ?? "").replace(/\/+$/, "");
}

export function resolveApiUrl(path: string) {
  if (/^https?:\/\//i.test(path)) {
    return path;
  }

  const normalizedPath = path.startsWith("/") ? path : `/${path}`;
  const baseUrl = getApiBaseUrl();

  return baseUrl ? `${baseUrl}${normalizedPath}` : normalizedPath;
}

async function request<TData>(
  method: HttpMethod,
  path: string,
  options: ApiRequestOptions = {}
) {
  const { body, token, headers, credentials = "include", ...requestInit } = options;
  const resolvedToken = token ?? (await tokenProvider?.()) ?? undefined;
  const response = await fetch(resolveApiUrl(path), {
    ...requestInit,
    body: serializeBody(body),
    credentials,
    headers: buildHeaders(headers, body, resolvedToken),
    method
  });
  const payload = await parseJson(response);

  if (!response.ok) {
    throw new ApiError({
      body: toApiErrorBody(payload),
      status: response.status,
      statusText: response.statusText
    });
  }

  return unwrapApiResponse<TData>(payload);
}

function buildHeaders(
  initHeaders: HeadersInit | undefined,
  body: unknown,
  token: string | undefined
) {
  const headers = new Headers(initHeaders);

  if (!headers.has("Accept")) {
    headers.set("Accept", JSON_CONTENT_TYPE);
  }

  if (body !== undefined && !isNativeBody(body) && !headers.has("Content-Type")) {
    headers.set("Content-Type", JSON_CONTENT_TYPE);
  }

  if (token && !headers.has("Authorization")) {
    headers.set("Authorization", `Bearer ${token}`);
  }

  return headers;
}

function serializeBody(body: unknown): BodyInit | undefined {
  if (body === undefined) {
    return undefined;
  }

  if (isNativeBody(body)) {
    return body;
  }

  return JSON.stringify(body);
}

function isNativeBody(body: unknown): body is BodyInit {
  return (
    typeof body === "string" ||
    (typeof FormData !== "undefined" && body instanceof FormData) ||
    (typeof Blob !== "undefined" && body instanceof Blob) ||
    (typeof URLSearchParams !== "undefined" && body instanceof URLSearchParams) ||
    (typeof ArrayBuffer !== "undefined" && body instanceof ArrayBuffer)
  );
}

async function parseJson(response: Response) {
  if (response.status === 204) {
    return undefined;
  }

  const contentType = response.headers.get("content-type") ?? "";

  if (!contentType.includes(JSON_CONTENT_TYPE)) {
    return undefined;
  }

  return response.json();
}

function unwrapApiResponse<TData>(payload: unknown) {
  if (isApiEnvelope<TData>(payload)) {
    return payload.data;
  }

  return payload as TData;
}

function isApiEnvelope<TData>(payload: unknown): payload is ApiEnvelope<TData> {
  return (
    typeof payload === "object" &&
    payload !== null &&
    "data" in payload &&
    "meta" in payload
  );
}

function toApiErrorBody(payload: unknown): ApiErrorBody | undefined {
  if (typeof payload !== "object" || payload === null) {
    return undefined;
  }

  return payload as ApiErrorBody;
}

export const apiClient = {
  get<TData>(path: string, options?: ApiRequestOptions) {
    return request<TData>("GET", path, options);
  },
  patch<TData>(path: string, body?: unknown, options?: ApiRequestOptions) {
    return request<TData>("PATCH", path, { ...options, body });
  },
  post<TData>(path: string, body?: unknown, options?: ApiRequestOptions) {
    return request<TData>("POST", path, { ...options, body });
  }
};

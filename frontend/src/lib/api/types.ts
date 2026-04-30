export type ApiMeta = Record<string, unknown>;

export type ApiEnvelope<TData> = {
  data: TData;
  meta: ApiMeta;
};

export type ApiErrorField = {
  field: string;
  reason: string;
};

export type ApiErrorDetails = Record<string, unknown> & {
  traceId?: string;
  fieldErrors?: ApiErrorField[];
};

export type ApiErrorBody = {
  code?: string;
  message?: string;
  details?: ApiErrorDetails | null;
};

export type ApiRequestOptions = Omit<RequestInit, "body" | "method"> & {
  body?: unknown;
  token?: string;
};

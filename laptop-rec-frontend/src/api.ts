import type {
  ApiResult,
  ChatMessage,
  LaptopDetail,
  LaptopFilters,
  LaptopListItem,
  LaptopOptions,
  PageResult,
  RecommendResponse
} from "./types";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "";

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    headers: {
      "Content-Type": "application/json",
      ...(init?.headers ?? {})
    },
    ...init
  });
  const payload = (await response.json()) as ApiResult<T>;
  if (!response.ok || !payload.success) {
    throw new Error(payload.message || `请求失败：${response.status}`);
  }
  return payload.data;
}

export function getLaptopOptions(): Promise<LaptopOptions> {
  return request<LaptopOptions>("/api/laptops/options");
}

export function getLaptopDetail(id: number): Promise<LaptopDetail> {
  return request<LaptopDetail>(`/api/laptops/${id}`);
}

export function getLaptops(filters: LaptopFilters, page: number, size: number): Promise<PageResult<LaptopListItem>> {
  const params = new URLSearchParams();
  Object.entries(filters).forEach(([key, value]) => {
    if (value.trim()) {
      params.set(key, value.trim());
    }
  });
  params.set("page", String(page));
  params.set("size", String(size));
  return request<PageResult<LaptopListItem>>(`/api/laptops?${params.toString()}`);
}

export function chatRecommend(messages: ChatMessage[]): Promise<RecommendResponse> {
  return request<RecommendResponse>("/api/recommend/chat", {
    method: "POST",
    body: JSON.stringify({ messages })
  });
}

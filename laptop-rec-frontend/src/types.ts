export interface ApiResult<T> {
  success: boolean;
  code: number;
  message: string;
  data: T;
}

export interface PageResult<T> {
  total: number;
  page: number;
  size: number;
  records: T[];
}

export interface RangeValue {
  min: number | null;
  max: number | null;
}

export interface LaptopOptions {
  brands: string[];
  productTypes: string[];
  usagePositionings: string[];
  gpuTypes: string[];
  memoryCapacitiesGb: number[];
  storageCapacitiesGb: number[];
  screenSizesInch: number[];
  priceRange: RangeValue | null;
  weightRange: RangeValue | null;
}

export interface LaptopListItem {
  id: number;
  brandName: string;
  model: string;
  productType?: string;
  usagePositioning?: string;
  weightKg?: number;
  imageUrl?: string;
  sourceUrl?: string;
  releaseDate?: string;
  latestPrice?: number;
  cpuModel?: string;
  gpuModel?: string;
  gpuType?: string;
  memoryCapacityGb?: number;
  storageCapacityGb?: number;
  screenSizeInch?: number;
  screenResolution?: string;
  refreshRateHz?: number;
}

export interface LaptopDetail extends LaptopListItem {
  thicknessMm?: number;
  os?: string;
  color?: string;
  sourceName?: string;
  rawTitle?: string;
  cpuBrand?: string;
  cpuCoreCount?: number;
  cpuThreadCount?: number;
  cpuBasePowerW?: number;
  gpuBrand?: string;
  gpuVramGb?: number;
  memoryType?: string;
  memoryFrequencyMhz?: number;
  storageType?: string;
  storageInterfaceType?: string;
  screenRefreshRateHz?: number;
  screenPanelType?: string;
  screenColorGamutPercent?: number;
  screenBrightnessNit?: number;
  screenTouchSupport?: number;
  batteryCapacityWh?: number;
  batteryChargePower?: string;
  wifiVersion?: string;
  bluetoothVersion?: string;
  portSummary?: string;
}

export interface LaptopFilters {
  keyword: string;
  brand: string;
  productType: string;
  usageKeyword: string;
  cpuKeyword: string;
  gpuKeyword: string;
  gpuType: string;
  minPrice: string;
  maxPrice: string;
  minMemoryGb: string;
  minStorageGb: string;
  minScreenSize: string;
  maxWeightKg: string;
  sort: string;
}

export interface ChatMessage {
  role: "user" | "assistant";
  content: string;
}

export interface Recommendation {
  laptopId: number;
  reason: string;
  detail: LaptopDetail | null;
}

export interface RecommendResponse {
  reply: string;
  recommendations: Recommendation[];
  followUpQuestions: string[];
}

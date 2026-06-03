import {
  ArrowLeft,
  Bot,
  Cpu,
  Database,
  HardDrive,
  Monitor,
  Plus,
  Search,
  Send,
  SlidersHorizontal,
  Sparkles,
  Trash2,
  Weight,
  X
} from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { FormEvent, KeyboardEvent } from "react";
import { chatRecommend, getLaptopDetail, getLaptopOptions, getLaptops } from "./api";
import type {
  ChatMessage,
  LaptopDetail,
  LaptopFilters,
  LaptopListItem,
  LaptopOptions,
  PageResult,
  RecommendResponse
} from "./types";

const emptyFilters: LaptopFilters = {
  keyword: "",
  brand: "",
  productType: "",
  usageKeyword: "",
  cpuKeyword: "",
  gpuKeyword: "",
  gpuType: "",
  minPrice: "",
  maxPrice: "",
  minMemoryGb: "",
  minStorageGb: "",
  minScreenSize: "",
  maxWeightKg: "",
  sort: "latest"
};

const sortOptions = [
  { value: "latest", label: "最近更新" },
  { value: "priceAsc", label: "价格从低到高" },
  { value: "priceDesc", label: "价格从高到低" },
  { value: "weightAsc", label: "重量从轻到重" },
  { value: "screenDesc", label: "屏幕尺寸从大到小" }
];

const recommendStorageKey = "laptop-rec:recommend:sessions:v1";
const initialRecommendMessages: ChatMessage[] = [
  { role: "assistant", content: "请说出 预算/用途/偏好 ，我会从数据库里找合适机型。" }
];

interface RecommendSession {
  id: string;
  title: string;
  messages: ChatMessage[];
  result: RecommendResponse | null;
  selectedRecommendationKey: string;
  createdAt: number;
  updatedAt: number;
}

interface RecommendSessionState {
  activeSessionId: string;
  sessions: RecommendSession[];
}

export default function App() {
  const [path, setPath] = useState(window.location.pathname);

  useEffect(() => {
    const onPopState = () => setPath(window.location.pathname);
    window.addEventListener("popstate", onPopState);
    return () => window.removeEventListener("popstate", onPopState);
  }, []);

  const navigate = useCallback((nextPath: string) => {
    window.history.pushState({}, "", nextPath);
    setPath(nextPath);
  }, []);

  if (path === "/filter") {
    return <FilterPage navigate={navigate} />;
  }
  if (path === "/recommend") {
    return <RecommendPage navigate={navigate} />;
  }
  return <HomePage navigate={navigate} />;
}

function HomePage({ navigate }: { navigate: (path: string) => void }) {
  return (
    <main className="homeShell">
      <div className="ambientGrid" />
      <section className="hero">
        <div className="heroMark">
          <Sparkles size={22} />
          <span>智能选型</span>
        </div>
        <h1>笔记本电脑推荐系统</h1>
        <p>从数据库规格筛选到自然语言推荐，围绕预算、性能、便携和用途给出可比较的机型结果。</p>
        <div className="heroActions">
          <button className="primaryAction" type="button" onClick={() => navigate("/filter")}>
            <SlidersHorizontal size={20} />
            按条件筛选
          </button>
          <button className="secondaryAction" type="button" onClick={() => navigate("/recommend")}>
            <Bot size={20} />
            DeepSeek推荐
          </button>
        </div>
      </section>
    </main>
  );
}

function FilterPage({ navigate }: { navigate: (path: string) => void }) {
  const [options, setOptions] = useState<LaptopOptions | null>(null);
  const [filters, setFilters] = useState<LaptopFilters>(emptyFilters);
  const [page, setPage] = useState(1);
  const [data, setData] = useState<PageResult<LaptopListItem> | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [selected, setSelected] = useState<LaptopDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const pageSize = 12;

  useEffect(() => {
    getLaptopOptions()
      .then(setOptions)
      .catch((exception: Error) => setError(exception.message));
  }, []);

  const loadLaptops = useCallback(() => {
    setLoading(true);
    setError("");
    getLaptops(filters, page, pageSize)
      .then(setData)
      .catch((exception: Error) => setError(exception.message))
      .finally(() => setLoading(false));
  }, [filters, page]);

  useEffect(() => {
    loadLaptops();
  }, [loadLaptops]);

  const totalPages = useMemo(() => {
    if (!data || data.total === 0) {
      return 1;
    }
    return Math.ceil(data.total / data.size);
  }, [data]);

  const updateFilter = (field: keyof LaptopFilters, value: string) => {
    setFilters((current) => ({ ...current, [field]: value }));
    setPage(1);
  };

  const resetFilters = () => {
    setFilters(emptyFilters);
    setPage(1);
  };

  const openDetail = (id: number) => {
    setDetailLoading(true);
    getLaptopDetail(id)
      .then(setSelected)
      .catch((exception: Error) => setError(exception.message))
      .finally(() => setDetailLoading(false));
  };

  return (
    <main className="appShell filterShell">
      <header className="recommendHeader filterHeader">
        <button className="recommendBackButton" type="button" onClick={() => navigate("/")}>
          <ArrowLeft size={18} />
          返回
        </button>
        <span className="recommendHeaderIcon">
          <SlidersHorizontal size={22} />
        </span>
        <div className="recommendHeaderText">
          <h1>按条件筛选</h1>
          <p>筛选项来自数据库，结果实时组合。</p>
        </div>
        <div className="recommendHeaderMeta">
          <span>本地数据库</span>
          <strong>{data?.total ?? 0} 个结果</strong>
        </div>
      </header>

      <section className="filterLayout">
        <form className="filterPanel" onSubmit={(event) => event.preventDefault()}>
          <label className="field wideField">
            <span>关键词</span>
            <input
              value={filters.keyword}
              onChange={(event) => updateFilter("keyword", event.target.value)}
              placeholder="型号、标题、品牌"
            />
          </label>

          <SelectField
            label="品牌"
            value={filters.brand}
            options={options?.brands ?? []}
            onChange={(value) => updateFilter("brand", value)}
          />
          <SelectField
            label="产品类型"
            value={filters.productType}
            options={options?.productTypes ?? []}
            onChange={(value) => updateFilter("productType", value)}
          />
          <SelectField
            label="用途定位"
            value={filters.usageKeyword}
            options={options?.usagePositionings ?? []}
            onChange={(value) => updateFilter("usageKeyword", value)}
          />
          <SelectField
            label="显卡类型"
            value={filters.gpuType}
            options={options?.gpuTypes ?? []}
            onChange={(value) => updateFilter("gpuType", value)}
          />

          <label className="field">
            <span>CPU 关键词</span>
            <input value={filters.cpuKeyword} onChange={(event) => updateFilter("cpuKeyword", event.target.value)} />
          </label>
          <label className="field">
            <span>GPU 关键词</span>
            <input value={filters.gpuKeyword} onChange={(event) => updateFilter("gpuKeyword", event.target.value)} />
          </label>
          <label className="field">
            <span>最低价格</span>
            <input
              value={filters.minPrice}
              inputMode="decimal"
              onChange={(event) => updateFilter("minPrice", event.target.value)}
              placeholder={formatRangeMin(options?.priceRange?.min)}
            />
          </label>
          <label className="field">
            <span>最高价格</span>
            <input
              value={filters.maxPrice}
              inputMode="decimal"
              onChange={(event) => updateFilter("maxPrice", event.target.value)}
              placeholder={formatRangeMax(options?.priceRange?.max)}
            />
          </label>

          <SelectField
            label="最低内存"
            value={filters.minMemoryGb}
            options={(options?.memoryCapacitiesGb ?? []).map(String)}
            suffix="GB"
            onChange={(value) => updateFilter("minMemoryGb", value)}
          />
          <SelectField
            label="最低硬盘"
            value={filters.minStorageGb}
            options={(options?.storageCapacitiesGb ?? []).map(String)}
            suffix="GB"
            onChange={(value) => updateFilter("minStorageGb", value)}
          />
          <SelectField
            label="最低屏幕"
            value={filters.minScreenSize}
            options={(options?.screenSizesInch ?? []).map(String)}
            suffix="英寸"
            onChange={(value) => updateFilter("minScreenSize", value)}
          />
          <label className="field">
            <span>最高重量</span>
            <input
              value={filters.maxWeightKg}
              inputMode="decimal"
              onChange={(event) => updateFilter("maxWeightKg", event.target.value)}
              placeholder={formatRangeMax(options?.weightRange?.max)}
            />
          </label>
          <label className="field wideField">
            <span>排序</span>
            <select value={filters.sort} onChange={(event) => updateFilter("sort", event.target.value)}>
              {sortOptions.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </label>

          <div className="filterActions">
            <button className="ghostButton" type="button" onClick={resetFilters}>
              <X size={18} />
              重置
            </button>
            <button className="primaryButton" type="button" onClick={loadLaptops}>
              <Search size={18} />
              查询
            </button>
          </div>
        </form>

        <section className="resultArea">

          <div className="resultScroller">
            {error && <div className="errorBox">{error}</div>}
            {loading && <div className="statusBox">正在加载筛选结果...</div>}
            {!loading && data?.records.length === 0 && <div className="statusBox">没有找到匹配机型。</div>}

            <div className="laptopGrid">
              {data?.records.map((item) => (
                <LaptopCard key={item.id} item={item} onDetail={() => openDetail(item.id)} />
              ))}
            </div>
          </div>

          <div className="pager">
            <label>
              第 {page} / {totalPages} 页
            </label>
            <button type="button" disabled={page <= 1} onClick={() => setPage((current) => current - 1)}>
              上一页
            </button>
            <button type="button" disabled={page >= totalPages} onClick={() => setPage((current) => current + 1)}>
              下一页
            </button>
          </div>
        </section>
      </section>

      {detailLoading && <div className="floatingStatus">正在读取详情...</div>}
      {selected && <DetailModal detail={selected} onClose={() => setSelected(null)} />}
    </main>
  );
}

function RecommendPage({ navigate }: { navigate: (path: string) => void }) {
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const [sessionState, setSessionState] = useState<RecommendSessionState>(() => loadRecommendSessionState());
  const [input, setInput] = useState("");
  const [pending, setPending] = useState(false);

  const activeSession = useMemo(() => {
    return (
      sessionState.sessions.find((session) => session.id === sessionState.activeSessionId) ??
      sessionState.sessions[0] ??
      createRecommendSession()
    );
  }, [sessionState]);
  const messages = activeSession.messages;
  const result = activeSession.result;
  const recommendations = result?.recommendations ?? [];

  useEffect(() => {
    saveRecommendSessionState(sessionState);
  }, [sessionState]);

  useEffect(() => {
    const inputEl = inputRef.current;
    if (!inputEl) {
      return;
    }
    inputEl.style.height = "auto";
    inputEl.style.height = `${Math.max(42, Math.min(inputEl.scrollHeight, 160))}px`;
  }, [input]);

  const updateSession = useCallback((sessionId: string, updater: (session: RecommendSession) => RecommendSession) => {
    setSessionState((current) => ({
      ...current,
      sessions: current.sessions.map((session) => (session.id === sessionId ? updater(session) : session))
    }));
  }, []);

  const createNewSession = () => {
    const session = createRecommendSession();
    setInput("");
    setSessionState((current) => ({
      activeSessionId: session.id,
      sessions: [session, ...current.sessions]
    }));
  };

  const deleteActiveSession = () => {
    if (pending) {
      return;
    }
    setInput("");
    setSessionState((current) => {
      const remaining = current.sessions.filter((session) => session.id !== current.activeSessionId);
      if (remaining.length === 0) {
        const session = createRecommendSession();
        return { activeSessionId: session.id, sessions: [session] };
      }
      return { activeSessionId: remaining[0].id, sessions: remaining };
    });
  };

  const switchSession = (sessionId: string) => {
    setInput("");
    setSessionState((current) => ({ ...current, activeSessionId: sessionId }));
  };

  const sendMessage = async (event: FormEvent) => {
    event.preventDefault();
    const content = input.trim();
    if (!content || pending) {
      return;
    }

    const userMessage: ChatMessage = { role: "user", content };
    const outgoing = [...messages, userMessage];
    const sessionId = activeSession.id;
    updateSession(sessionId, (session) => ({
      ...session,
      title: buildSessionTitle(outgoing),
      messages: outgoing,
      result: null,
      selectedRecommendationKey: "",
      updatedAt: Date.now()
    }));
    setInput("");
    setPending(true);

    try {
      const response = await chatRecommend(buildChatRequestMessages(outgoing));
      const reply = normalizeAssistantReply(response.reply, response.recommendations?.length > 0);
      const normalizedResult = { ...response, reply };
      updateSession(sessionId, (session) => ({
        ...session,
        messages: [...outgoing, { role: "assistant", content: reply }],
        result: normalizedResult,
        selectedRecommendationKey: getRecommendationKey(normalizedResult.recommendations[0], 0),
        updatedAt: Date.now()
      }));
    } catch (exception) {
      const message = exception instanceof Error ? exception.message : "推荐服务请求失败";
      updateSession(sessionId, (session) => ({
        ...session,
        messages: [...outgoing, { role: "assistant", content: message }],
        updatedAt: Date.now()
      }));
    } finally {
      setPending(false);
    }
  };

  const answerFollowUp = (question: string) => {
    const text = `关于“${question}”，我的回答是：`;
    setInput(text);
    window.setTimeout(() => {
      const inputEl = inputRef.current;
      if (inputEl) {
        inputEl.focus();
        inputEl.setSelectionRange(text.length, text.length);
      }
    }, 0);
  };

  const toggleRecommendation = (key: string) => {
    updateSession(activeSession.id, (session) => ({
      ...session,
      selectedRecommendationKey: session.selectedRecommendationKey === key ? "" : key,
      updatedAt: Date.now()
    }));
  };

  const handleComposerKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === "Enter" && !event.shiftKey) {
      event.preventDefault();
      event.currentTarget.form?.requestSubmit();
    }
  };

  return (
    <main className="appShell recommendShell">
      <header className="recommendHeader">
        <button className="recommendBackButton" type="button" onClick={() => navigate("/")}>
          <ArrowLeft size={18} />
          返回
        </button>
        <span className="recommendHeaderIcon">
          <Bot size={22} />
        </span>
        <div className="recommendHeaderText">
          <h1>DeepSeek推荐</h1>
          <p>仅查询本地数据库，结果可核对。</p>
        </div>
        <div className="recommendHeaderMeta">
          <span>本地数据库</span>
          <strong>{recommendations.length} 个结果</strong>
        </div>
      </header>

      <aside className="recommendResults">
        {recommendations.length ? (
          <div className="recommendListScroller">
            {recommendations.map((recommendation, index) => {
              const key = getRecommendationKey(recommendation, index);
              const expanded = key === activeSession.selectedRecommendationKey;
              return expanded ? (
                <RecommendationCard key={key} recommendation={recommendation} onCollapse={() => toggleRecommendation(key)} />
              ) : (
                <button key={key} className="recommendRow" type="button" onClick={() => toggleRecommendation(key)}>
                  <span>{recommendationName(recommendation)}</span>
                  <button className="cardToggleButton" type="button">
                    详情
                  </button>
                </button>
              );
            })}
          </div>
        ) : (
          <div className="statusBox">推荐结果会显示在这里。</div>
        )}
      </aside>

      <div className="chatPanel">
        <div className="sessionToolbar">
          <select value={activeSession.id} onChange={(event) => switchSession(event.target.value)}>
            {sessionState.sessions.map((session) => (
              <option key={session.id} value={session.id}>
                {session.title}
              </option>
            ))}
          </select>
          <button className="ghostButton compactButton" type="button" onClick={createNewSession} disabled={pending}>
            <Plus size={17} />
            新建
          </button>
          <button className="ghostButton compactButton" type="button" onClick={deleteActiveSession} disabled={pending}>
            <Trash2 size={17} />
            删除
          </button>
        </div>

        <div className="messageList">
          {messages.map((message, index) => (
            <div key={`${message.role}-${index}`} className={`messageBubble ${message.role}`}>
              {message.content}
            </div>
          ))}
          {pending && <div className="messageBubble assistant">正在查询数据库并整理推荐...</div>}
        </div>

        {result?.followUpQuestions.length ? (
          <div className="followUpPanel">
            {result.followUpQuestions.map((question) => (
              <button key={question} className="followUpChip" type="button" onClick={() => answerFollowUp(question)}>
                {question}
              </button>
            ))}
          </div>
        ) : null}

        <form className="chatComposer" onSubmit={sendMessage}>
          <textarea
            ref={inputRef}
            value={input}
            onChange={(event) => setInput(event.target.value)}
            onKeyDown={handleComposerKeyDown}
            placeholder="请输入文本"
            rows={1}
          />
          <button className="primaryButton iconOnlyText" type="submit" disabled={pending}>
            <Send size={18} />
            发送
          </button>
        </form>
      </div>
    </main>
  );
}

function SelectField({
  label,
  value,
  options,
  suffix,
  onChange
}: {
  label: string;
  value: string;
  options: string[];
  suffix?: string;
  onChange: (value: string) => void;
}) {
  return (
    <label className="field">
      <span>{label}</span>
      <select value={value} onChange={(event) => onChange(event.target.value)}>
        <option value="">不限</option>
        {options.map((option) => (
          <option key={option} value={option}>
            {suffix ? `${option}${suffix}` : option}
          </option>
        ))}
      </select>
    </label>
  );
}

function LaptopCard({ item, onDetail }: { item: LaptopListItem; onDetail: () => void }) {
  return (
    <article className="laptopCard">
      <div className="cardBody">
        <div className="cardTop">
          <div className="thumbBox">
            {item.imageUrl ? <img src={item.imageUrl} alt={`${item.brandName}${item.model}`} /> : <Database size={28} />}
          </div>
          <div className="titleBlock">
            <div>
              <span className="brandText">{item.brandName}</span>
              <h2>{item.model}</h2>
            </div>
            <strong>{money(item.latestPrice)}</strong>
          </div>
        </div>
        <div className="specRow">
          <span>
            <Cpu size={16} />
            {text(item.cpuModel)}
          </span>
          <span>
            <HardDrive size={16} />
            {capacity(item.memoryCapacityGb)} / {capacity(item.storageCapacityGb)}
          </span>
          <span>
            <Monitor size={16} />
            {screen(item)}
          </span>
          <span>
            <Weight size={16} />
            {weight(item.weightKg)}
          </span>
        </div>
        <div className="tagRow">
          {[item.productType, item.usagePositioning, item.gpuType].filter(Boolean).map((tag) => (
            <span key={tag}>{tag}</span>
          ))}
        </div>
        <button className="detailButton" type="button" onClick={onDetail}>
          查看详情
        </button>
      </div>
    </article>
  );
}

function RecommendationCard({
  recommendation,
  onCollapse
}: {
  recommendation: RecommendResponse["recommendations"][number];
  onCollapse?: () => void;
}) {
  const detail = recommendation.detail;
  return (
    <article className="recommendCard">
      <div className="recommendCardHead">
        <h2>{detail ? `${detail.brandName} ${detail.model}` : `机型 #${recommendation.laptopId}`}</h2>
        {onCollapse && (
          <button className="cardToggleButton" type="button" onClick={onCollapse}>
            收起
          </button>
        )}
      </div>
      <div className="recommendCardBody">
        <p>{recommendation.reason}</p>
        {detail && (
          <div className="miniSpecs">
            <span>{money(detail.latestPrice)}</span>
            <span>{text(detail.cpuModel)}</span>
            <span>{text(detail.gpuModel)}</span>
            <span>{capacity(detail.memoryCapacityGb)} / {capacity(detail.storageCapacityGb)}</span>
            <span>{screen(detail)}</span>
            <span>{weight(detail.weightKg)}</span>
          </div>
        )}
      </div>
    </article>
  );
}

function DetailModal({ detail, onClose }: { detail: LaptopDetail; onClose: () => void }) {
  const rows = [
    ["价格", money(detail.latestPrice)],
    ["CPU", joinText(detail.cpuBrand, detail.cpuModel)],
    ["CPU 核心/线程", joinText(detail.cpuCoreCount, detail.cpuThreadCount, "/")],
    ["GPU", joinText(detail.gpuBrand, detail.gpuModel)],
    ["显存", detail.gpuVramGb ? `${detail.gpuVramGb}GB` : "未知"],
    ["内存", joinText(capacity(detail.memoryCapacityGb), detail.memoryType)],
    ["硬盘", joinText(capacity(detail.storageCapacityGb), detail.storageType, " ")],
    ["屏幕", screen(detail)],
    ["亮度", detail.screenBrightnessNit ? `${detail.screenBrightnessNit}nit` : "未知"],
    ["重量", weight(detail.weightKg)],
    ["厚度", detail.thicknessMm ? `${detail.thicknessMm}mm` : "未知"],
    ["电池", detail.batteryCapacityWh ? `${detail.batteryCapacityWh}Wh` : "未知"],
    ["无线", joinText(detail.wifiVersion, detail.bluetoothVersion)],
    ["接口", text(detail.portSummary)],
    ["系统", text(detail.os)],
    ["颜色", text(detail.color)]
  ];

  return (
    <div className="modalBackdrop" onClick={onClose}>
      <article className="detailModal" onClick={(event) => event.stopPropagation()}>
        <button className="modalClose" type="button" onClick={onClose}>
          <X size={18} />
        </button>
        <div className="detailHead">
          <div className="imageBox large">
            {detail.imageUrl ? <img src={detail.imageUrl} alt={`${detail.brandName}${detail.model}`} /> : <Database size={42} />}
          </div>
          <div>
            <span className="brandText">{detail.brandName}</span>
            <h2>{detail.model}</h2>
            <p>{detail.rawTitle}</p>
          </div>
        </div>
        <dl className="detailGrid">
          {rows.map(([label, value]) => (
            <div key={label}>
              <dt>{label}</dt>
              <dd>{value}</dd>
            </div>
          ))}
        </dl>
        {detail.sourceUrl && (
          <a className="sourceLink" href={detail.sourceUrl} target="_blank" rel="noreferrer">
            查看来源
          </a>
        )}
      </article>
    </div>
  );
}

function money(value?: number) {
  if (value == null) {
    return "价格未知";
  }
  return `￥${value.toLocaleString("zh-CN")}`;
}

function capacity(value?: number) {
  return value == null ? "未知" : `${value}GB`;
}

function weight(value?: number) {
  return value == null ? "重量未知" : `${value}kg`;
}

function screen(item: Pick<LaptopListItem, "screenSizeInch" | "screenResolution" | "refreshRateHz"> & { screenRefreshRateHz?: number }) {
  const size = item.screenSizeInch ? `${item.screenSizeInch}英寸` : "尺寸未知";
  const resolution = item.screenResolution ?? "分辨率未知";
  const refreshRate = item.refreshRateHz ?? item.screenRefreshRateHz;
  const refresh = refreshRate ? `${refreshRate}Hz` : "";
  return [size, resolution, refresh].filter(Boolean).join(" ");
}

function text(value?: string) {
  return value?.trim() || "未知";
}

function joinText(left?: string | number, right?: string | number, separator = " ") {
  const values = [left, right].filter((value) => value !== undefined && value !== null && String(value).trim());
  return values.length ? values.join(separator) : "未知";
}

function formatRangeMin(value?: number | null) {
  return value == null ? "最低" : `最低 ${value}`;
}

function formatRangeMax(value?: number | null) {
  return value == null ? "最高" : `最高 ${value}`;
}

function loadRecommendSessionState(): RecommendSessionState {
  const fallback = createDefaultRecommendSessionState();
  if (typeof window === "undefined") {
    return fallback;
  }
  try {
    const raw = window.localStorage.getItem(recommendStorageKey);
    if (!raw) {
      return fallback;
    }
    const parsed = JSON.parse(raw) as Partial<RecommendSessionState>;
    const sessions = Array.isArray(parsed.sessions)
      ? parsed.sessions.map(normalizeRecommendSession).filter((session): session is RecommendSession => session !== null)
      : [];
    if (!sessions.length) {
      return fallback;
    }
    const activeSessionId = sessions.some((session) => session.id === parsed.activeSessionId)
      ? String(parsed.activeSessionId)
      : sessions[0].id;
    return { activeSessionId, sessions };
  } catch {
    return fallback;
  }
}

function saveRecommendSessionState(state: RecommendSessionState) {
  if (typeof window === "undefined") {
    return;
  }
  try {
    window.localStorage.setItem(recommendStorageKey, JSON.stringify(state));
  } catch {
    // Ignore browser storage quota or privacy-mode failures.
  }
}

function createDefaultRecommendSessionState(): RecommendSessionState {
  const session = createRecommendSession();
  return { activeSessionId: session.id, sessions: [session] };
}

function createRecommendSession(): RecommendSession {
  const now = Date.now();
  return {
    id: createSessionId(),
    title: "新对话",
    messages: initialRecommendMessages,
    result: null,
    selectedRecommendationKey: "",
    createdAt: now,
    updatedAt: now
  };
}

function normalizeRecommendSession(value: unknown): RecommendSession | null {
  if (!isObject(value) || typeof value.id !== "string") {
    return null;
  }
  const messages = Array.isArray(value.messages)
    ? value.messages.map(normalizeStoredChatMessage).filter((message): message is ChatMessage => message !== null)
    : initialRecommendMessages;
  return {
    id: value.id,
    title: typeof value.title === "string" && value.title.trim() ? value.title.trim() : buildSessionTitle(messages),
    messages: messages.length ? messages : initialRecommendMessages,
    result: isRecommendResponse(value.result) ? value.result : null,
    selectedRecommendationKey: typeof value.selectedRecommendationKey === "string" ? value.selectedRecommendationKey : "",
    createdAt: typeof value.createdAt === "number" ? value.createdAt : Date.now(),
    updatedAt: typeof value.updatedAt === "number" ? value.updatedAt : Date.now()
  };
}

function createSessionId() {
  return typeof crypto !== "undefined" && "randomUUID" in crypto ? crypto.randomUUID() : `${Date.now()}-${Math.random()}`;
}

function buildSessionTitle(messages: ChatMessage[]) {
  const firstUserMessage = messages.find((message) => message.role === "user")?.content.trim();
  if (!firstUserMessage) {
    return "新对话";
  }
  return firstUserMessage.length > 22 ? `${firstUserMessage.slice(0, 22)}...` : firstUserMessage;
}

function buildChatRequestMessages(messages: ChatMessage[]) {
  return messages
    .map((message) => ({ role: message.role, content: message.content.trim() }))
    .filter(
      (message) =>
        (message.role === "user" || message.role === "assistant") &&
        message.content &&
        !isToolTraceMessage(message)
    );
}

function normalizeStoredChatMessage(value: unknown): ChatMessage | null {
  if (!isChatMessage(value) || isToolTraceMessage(value)) {
    return null;
  }
  if (value.role === "assistant") {
    const content = normalizeAssistantReply(value.content);
    return isToolTraceContent(content) ? null : { role: value.role, content };
  }
  return { role: value.role, content: value.content.trim() };
}

function isChatMessage(value: unknown): value is ChatMessage {
  return (
    isObject(value) &&
    (value.role === "user" || value.role === "assistant") &&
    typeof value.content === "string" &&
    value.content.trim().length > 0
  );
}

function isRecommendResponse(value: unknown): value is RecommendResponse {
  return (
    isObject(value) &&
    typeof value.reply === "string" &&
    Array.isArray(value.recommendations) &&
    Array.isArray(value.followUpQuestions)
  );
}

function getRecommendationKey(recommendation: RecommendResponse["recommendations"][number] | undefined, index: number) {
  if (!recommendation) {
    return "";
  }
  return `${recommendation.laptopId}-${index}`;
}

function recommendationName(recommendation: RecommendResponse["recommendations"][number]) {
  const detail = recommendation.detail;
  if (detail) {
    return joinText(detail.brandName, detail.model);
  }
  return `机型 #${recommendation.laptopId}`;
}

function normalizeAssistantReply(reply?: string, hasRecommendations = false) {
  const fallback = hasRecommendations
    ? "我已根据数据库结果整理推荐，具体机型请看右侧推荐卡片。"
    : "当前条件下没有查到合适的数据库候选，请放宽预算、品牌、显卡或年份要求后再试。";
  const raw = reply?.trim();
  if (!raw) {
    return fallback;
  }

  const fenced = raw.match(/```(?:json)?\s*([\s\S]*?)```/i);
  const normalized = fenced?.[1]?.trim() ?? raw;
  if (isToolTraceContent(normalized)) {
    return fallback;
  }
  const candidates = [normalized];
  const firstBrace = normalized.indexOf("{");
  const lastBrace = normalized.lastIndexOf("}");
  if (firstBrace >= 0 && lastBrace > firstBrace) {
    candidates.push(normalized.slice(firstBrace, lastBrace + 1));
  }

  for (const candidate of candidates) {
    try {
      const parsed = JSON.parse(candidate) as unknown;
      if (isObject(parsed) && typeof parsed.reply === "string" && parsed.reply.trim()) {
        return parsed.reply.trim();
      }
    } catch {
      // Ignore non-JSON model text.
    }
  }

  if (
    normalized.startsWith("{") ||
    normalized.startsWith("[") ||
    normalized.includes('"recommendations"') ||
    normalized.includes('"records"') ||
    normalized.includes('"total"')
  ) {
    return fallback;
  }
  return normalized;
}

function isToolTraceMessage(message: ChatMessage) {
  return message.role === "assistant" && isToolTraceContent(message.content);
}

function isToolTraceContent(content: string) {
  const normalized = content.trim();
  if (!normalized) {
    return false;
  }
  return [
    '"tool_calls"',
    '"reasoning_content"',
    '"finish_reason":"tool_calls"',
    '"finish_reason": "tool_calls"',
    '"role":"tool"',
    '"role": "tool"',
    '"function":{"name":"search_laptops"',
    '"function": {"name": "search_laptops"',
    '"function":{"name":"get_laptop_detail"',
    '"function": {"name": "get_laptop_detail"',
    '"records"',
    '"total"',
    '"laptopId"',
    '"detail"',
    'search_laptops',
    'get_laptop_detail'
  ].some((marker) => normalized.includes(marker));
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

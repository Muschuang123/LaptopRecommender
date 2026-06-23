import {
  ArrowLeft,
  Bot,
  ChevronRight,
  Cpu,
  Database,
  HardDrive,
  Laptop,
  Monitor,
  Plus,
  Search,
  Send,
  SlidersHorizontal,
  ShoppingCart,
  Sparkles,
  Trash2,
  X
} from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { FormEvent, KeyboardEvent, MouseEvent, ReactNode } from "react";
import {
  addCartItem,
  chatRecommend,
  clearCart,
  deleteCartItems,
  getCartItems,
  getLaptopDetail,
  getLaptopOptions,
  getLaptops
} from "./api";
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

const filterFields = Object.keys(emptyFilters) as Array<keyof LaptopFilters>;
const numberFormatter = new Intl.NumberFormat("zh-CN", { maximumFractionDigits: 2 });
const currencyFormatter = new Intl.NumberFormat("zh-CN", {
  style: "currency",
  currency: "CNY",
  maximumFractionDigits: 0
});

const recommendStorageKey = "laptop-rec:recommend:sessions:v1";
const initialRecommendMessages: ChatMessage[] = [
  { role: "assistant", content: "请说出 预算/用途/偏好 ，我会从数据库里找合适机型。" }
];

interface RecommendSession {
  id: string;
  title: string;
  messages: ChatMessage[];
  result: RecommendResponse | null;
  createdAt: number;
  updatedAt: number;
}

interface RecommendSessionState {
  activeSessionId: string;
  sessions: RecommendSession[];
}

export default function App() {
  const [location, setLocation] = useState(() => ({
    path: window.location.pathname,
    search: window.location.search
  }));

  useEffect(() => {
    const onPopState = () => setLocation({ path: window.location.pathname, search: window.location.search });
    window.addEventListener("popstate", onPopState);
    return () => window.removeEventListener("popstate", onPopState);
  }, []);

  const navigate = useCallback((nextPath: string) => {
    window.history.pushState({}, "", nextPath);
    setLocation({ path: window.location.pathname, search: window.location.search });
  }, []);

  if (location.path === "/filter") {
    return <FilterPage key={`${location.path}${location.search}`} navigate={navigate} />;
  }
  if (location.path === "/recommend") {
    return <RecommendPage navigate={navigate} />;
  }
  if (location.path === "/cart") {
    return <CartPage navigate={navigate} />;
  }
  return <HomePage navigate={navigate} />;
}

function HomePage({ navigate }: { navigate: (path: string) => void }) {
  return (
    <>
      <SkipLink />
      <main id="main-content" className="homeShell" tabIndex={-1}>
        <div className="ambientGrid" />
        <section className="hero">
          <div className="heroCopy">
            <div className="heroMark">
              <Sparkles size={18} aria-hidden="true" />
              <span>为真实需求匹配真实配置</span>
            </div>
            <h1>更快选到适合你的下一台笔记本</h1>
            <p>从数据库规格筛选到自然语言推荐，把预算、性能、便携与用途放在同一套决策框架里。</p>
            <div className="heroActions">
              <AppLink className="primaryAction" to="/filter" navigate={navigate}>
                <SlidersHorizontal size={20} aria-hidden="true" />
                开始筛选
              </AppLink>
              <AppLink className="secondaryAction" to="/recommend" navigate={navigate}>
                <Bot size={20} aria-hidden="true" />
                对话式推荐
              </AppLink>
              <AppLink className="secondaryAction homeCartAction" to="/cart" navigate={navigate}>
                <ShoppingCart size={20} aria-hidden="true" />
                购物车
              </AppLink>
            </div>
            <ul className="heroProof">
              <li>
                <strong>多维规格</strong>
                <span>统一对比配置</span>
              </li>
              <li>
                <strong>本地数据</strong>
                <span>结果可以核对</span>
              </li>
              <li>
                <strong>对话理解</strong>
                <span>把需求说清即可</span>
              </li>
            </ul>
          </div>
          <div className="heroVisual" aria-hidden="true">
            <div className="heroVisualGlow" />
            <div className="heroDashboard">
              <div className="heroDashboardTop">
                <span className="visualStatus"><i /> 规格库已就绪</span>
                <span>实时匹配</span>
              </div>
              <div className="heroDashboardTitle">
                <span>选型工作台</span>
                <strong>从需求到候选机型</strong>
              </div>
              <div className="heroMetricRow">
                <div>
                  <span>预算范围</span>
                  <strong>¥6,000–10,000</strong>
                </div>
                <div>
                  <span>优先场景</span>
                  <strong>开发 · 轻创作</strong>
                </div>
              </div>
              <div className="heroCandidateList">
                <div className="heroCandidate primaryCandidate">
                  <span className="candidateIcon"><Cpu size={18} /></span>
                  <div>
                    <strong>性能与续航平衡</strong>
                    <span>高性能处理器 · 轻量机身</span>
                  </div>
                  <b>匹配度 94%</b>
                </div>
                <div className="heroCandidate">
                  <span className="candidateIcon"><Monitor size={18} /></span>
                  <div>
                    <strong>清晰屏幕与创作空间</strong>
                    <span>高色域 · 大尺寸显示</span>
                  </div>
                  <b>匹配度 88%</b>
                </div>
              </div>
              <div className="heroDashboardBottom">
                <span><Database size={15} /> 数据可追溯</span>
                <span><Sparkles size={15} /> 智能解释推荐理由</span>
              </div>
            </div>
          </div>
        </section>
      </main>
    </>
  );
}

function FilterPage({ navigate }: { navigate: (path: string) => void }) {
  const [options, setOptions] = useState<LaptopOptions | null>(null);
  const [filters, setFilters] = useState<LaptopFilters>(() => readFiltersFromSearch(window.location.search));
  const [appliedFilters, setAppliedFilters] = useState<LaptopFilters>(() => readFiltersFromSearch(window.location.search));
  const [page, setPage] = useState(() => readPageFromSearch(window.location.search));
  const [data, setData] = useState<PageResult<LaptopListItem> | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [selected, setSelected] = useState<LaptopDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [cartItemIds, setCartItemIds] = useState<Set<number>>(() => new Set());
  const [addingCartItemId, setAddingCartItemId] = useState<number | null>(null);
  const [cartFeedback, setCartFeedback] = useState("");
  const pageSize = 12;

  useEffect(() => {
    getLaptopOptions()
      .then(setOptions)
      .catch((exception: Error) => setError(exception.message));
  }, []);

  useEffect(() => {
    getCartItems()
      .then((items) => setCartItemIds(new Set(items.map((item) => item.id))))
      .catch((exception: Error) => setError(exception.message));
  }, []);

  const loadLaptops = useCallback(() => {
    setLoading(true);
    setError("");
    getLaptops(appliedFilters, page, pageSize)
      .then(setData)
      .catch((exception: Error) => setError(exception.message))
      .finally(() => setLoading(false));
  }, [appliedFilters, page]);

  useEffect(() => {
    loadLaptops();
  }, [loadLaptops]);

  useEffect(() => {
    const search = buildFilterSearch(appliedFilters, page);
    if (window.location.search !== search) {
      window.history.replaceState({}, "", `${window.location.pathname}${search}`);
    }
  }, [appliedFilters, page]);

  const totalPages = useMemo(() => {
    if (!data || data.total === 0) {
      return 1;
    }
    return Math.ceil(data.total / data.size);
  }, [data]);

  const updateFilter = (field: keyof LaptopFilters, value: string) => {
    setFilters((current) => ({ ...current, [field]: value }));
  };

  const resetFilters = () => {
    setFilters(emptyFilters);
    setAppliedFilters(emptyFilters);
    setPage(1);
  };

  const submitFilters = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setAppliedFilters(filters);
    setPage(1);
  };

  const openDetail = (id: number) => {
    setDetailLoading(true);
    getLaptopDetail(id)
      .then(setSelected)
      .catch((exception: Error) => setError(exception.message))
      .finally(() => setDetailLoading(false));
  };

  const addToCart = async (item: LaptopListItem) => {
    if (addingCartItemId !== null || cartItemIds.has(item.id)) {
      return;
    }
    setAddingCartItemId(item.id);
    setCartFeedback("");
    try {
      await addCartItem(item.id);
      setCartItemIds((current) => new Set(current).add(item.id));
      setCartFeedback(`已将 ${item.brandName} ${item.model} 加入购物车。`);
    } catch (exception) {
      setCartFeedback(exception instanceof Error ? exception.message : "加入购物车失败");
    } finally {
      setAddingCartItemId(null);
    }
  };

  return (
    <>
      <SkipLink />
      <main id="main-content" className="appShell filterShell" tabIndex={-1}>
      <header className="recommendHeader filterHeader">
        <AppLink className="recommendBackButton" to="/" navigate={navigate}>
          <ArrowLeft size={18} aria-hidden="true" />
          返回首页
        </AppLink>
        <span className="recommendHeaderIcon">
          <SlidersHorizontal size={22} aria-hidden="true" />
        </span>
        <div className="recommendHeaderText">
          <h1>按条件筛选</h1>
          <p>筛选项来自数据库，结果实时组合。</p>
        </div>
        <AppLink className="cartNavButton" to="/cart" navigate={navigate}>
          <ShoppingCart size={18} aria-hidden="true" />
          购物车
        </AppLink>
      </header>

      <section className="filterLayout">
        <form className="filterPanel" onSubmit={submitFilters}>
          <label className="field wideField">
            <span>关键词</span>
            <input
              name="keyword"
              type="search"
              value={filters.keyword}
              autoComplete="off"
              onChange={(event) => updateFilter("keyword", event.target.value)}
              placeholder="例如：ThinkBook…"
            />
          </label>

          <SelectField
            label="品牌"
            name="brand"
            value={filters.brand}
            options={options?.brands ?? []}
            onChange={(value) => updateFilter("brand", value)}
          />
          <SelectField
            label="产品类型"
            name="productType"
            value={filters.productType}
            options={options?.productTypes ?? []}
            onChange={(value) => updateFilter("productType", value)}
          />
          <SelectField
            label="用途定位"
            name="usageKeyword"
            value={filters.usageKeyword}
            options={options?.usagePositionings ?? []}
            onChange={(value) => updateFilter("usageKeyword", value)}
          />
          <SelectField
            label="显卡类型"
            name="gpuType"
            value={filters.gpuType}
            options={options?.gpuTypes ?? []}
            onChange={(value) => updateFilter("gpuType", value)}
          />

          <label className="field">
            <span>CPU 关键词</span>
            <input
              name="cpuKeyword"
              value={filters.cpuKeyword}
              autoComplete="off"
              onChange={(event) => updateFilter("cpuKeyword", event.target.value)}
              placeholder="例如：Core Ultra 7…"
            />
          </label>
          <label className="field">
            <span>GPU 关键词</span>
            <input
              name="gpuKeyword"
              value={filters.gpuKeyword}
              autoComplete="off"
              onChange={(event) => updateFilter("gpuKeyword", event.target.value)}
              placeholder="例如：RTX 5060…"
            />
          </label>
          <label className="field">
            <span>最低价格</span>
            <input
              name="minPrice"
              type="number"
              min="0"
              step="1"
              value={filters.minPrice}
              autoComplete="off"
              inputMode="decimal"
              onChange={(event) => updateFilter("minPrice", event.target.value)}
              placeholder={formatRangeMin(options?.priceRange?.min)}
            />
          </label>
          <label className="field">
            <span>最高价格</span>
            <input
              name="maxPrice"
              type="number"
              min="0"
              step="1"
              value={filters.maxPrice}
              autoComplete="off"
              inputMode="decimal"
              onChange={(event) => updateFilter("maxPrice", event.target.value)}
              placeholder={formatRangeMax(options?.priceRange?.max, "12000")}
            />
          </label>

          <SelectField
            label="最低内存"
            name="minMemoryGb"
            value={filters.minMemoryGb}
            options={(options?.memoryCapacitiesGb ?? []).map(String)}
            suffix="GB"
            onChange={(value) => updateFilter("minMemoryGb", value)}
          />
          <SelectField
            label="最低硬盘"
            name="minStorageGb"
            value={filters.minStorageGb}
            options={(options?.storageCapacitiesGb ?? []).map(String)}
            suffix="GB"
            onChange={(value) => updateFilter("minStorageGb", value)}
          />
          <SelectField
            label="最低屏幕"
            name="minScreenSize"
            value={filters.minScreenSize}
            options={(options?.screenSizesInch ?? []).map(String)}
            suffix="英寸"
            onChange={(value) => updateFilter("minScreenSize", value)}
          />
          <label className="field">
            <span>最高重量</span>
            <input
              name="maxWeightKg"
              type="number"
              min="0"
              step="0.01"
              value={filters.maxWeightKg}
              autoComplete="off"
              inputMode="decimal"
              onChange={(event) => updateFilter("maxWeightKg", event.target.value)}
              placeholder={formatRangeMax(options?.weightRange?.max)}
            />
          </label>
          <label className="field wideField">
            <span>排序</span>
            <select name="sort" value={filters.sort} autoComplete="off" onChange={(event) => updateFilter("sort", event.target.value)}>
              {sortOptions.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </label>

          <div className="filterActions">
            <button className="ghostButton" type="button" onClick={resetFilters}>
              <X size={18} aria-hidden="true" />
              重置
            </button>
            <button className="primaryButton" type="submit" disabled={loading}>
              {loading ? (
                <>
                  <span className="buttonSpinner" aria-hidden="true" />
                  正在查询…
                </>
              ) : (
                <>
                  <Search size={18} aria-hidden="true" />
                  查询
                </>
              )}
            </button>
          </div>
        </form>

        <section className="resultArea" aria-busy={loading}>
          <div className="resultHeading">
            <h2>匹配结果</h2>
            <p>{loading ? "正在更新结果…" : `${formatNumber(data?.total ?? 0)} 台候选机型`}</p>
          </div>

          <div className="resultScroller">
            {error && <div className="errorBox" role="alert">{error}</div>}
            {loading && <div className="statusBox" role="status" aria-live="polite">正在加载筛选结果…</div>}
            {!loading && data?.records.length === 0 && <div className="statusBox" role="status" aria-live="polite">没有找到匹配机型。</div>}

            <div className="laptopGrid">
              {data?.records.map((item) => (
                <LaptopCard
                  key={item.id}
                  item={item}
                  onDetail={() => openDetail(item.id)}
                  onAddToCart={() => addToCart(item)}
                  isInCart={cartItemIds.has(item.id)}
                  addingToCart={addingCartItemId === item.id}
                />
              ))}
            </div>
          </div>

          <div className="pager">
            {cartFeedback && <p className="cartPagerStatus" role="status">{cartFeedback}</p>}
            <p className="pagerStatus" aria-live="polite">
              第 {formatNumber(page)} / {formatNumber(totalPages)} 页
            </p>
            <button type="button" disabled={page <= 1} onClick={() => setPage((current) => current - 1)}>
              上一页
            </button>
            <button type="button" disabled={page >= totalPages} onClick={() => setPage((current) => current + 1)}>
              下一页
            </button>
          </div>
        </section>
      </section>

      {detailLoading && <div className="floatingStatus" role="status" aria-live="polite">正在读取详情…</div>}
      {selected && <DetailModal detail={selected} onClose={() => setSelected(null)} />}
      </main>
    </>
  );
}

function RecommendPage({ navigate }: { navigate: (path: string) => void }) {
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const [sessionState, setSessionState] = useState<RecommendSessionState>(() => loadRecommendSessionState());
  const [input, setInput] = useState("");
  const [pending, setPending] = useState(false);
  const [selectedRecommendation, setSelectedRecommendation] = useState<{
    detail: LaptopDetail;
    reason: string;
  } | null>(null);
  const [cartItemIds, setCartItemIds] = useState<Set<number>>(() => new Set());
  const [addingCartItemId, setAddingCartItemId] = useState<number | null>(null);
  const [cartFeedback, setCartFeedback] = useState("");

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
  const recommendationCards = useMemo(
    () => recommendations.flatMap((recommendation, index) => {
      if (!recommendation.detail) {
        return [];
      }
      return [{
        key: `${recommendation.laptopId}-${index}`,
        detail: recommendation.detail,
        reason: recommendation.reason
      }];
    }),
    [recommendations]
  );

  useEffect(() => {
    saveRecommendSessionState(sessionState);
  }, [sessionState]);

  useEffect(() => {
    getCartItems()
      .then((items) => setCartItemIds(new Set(items.map((item) => item.id))))
      .catch((exception: Error) => setCartFeedback(exception.message));
  }, []);

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
    if (!window.confirm(`确认删除“${activeSession.title}”吗？此操作无法恢复。`)) {
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

  const addToCart = async (item: LaptopListItem) => {
    if (addingCartItemId !== null || cartItemIds.has(item.id)) {
      return;
    }
    setAddingCartItemId(item.id);
    setCartFeedback("");
    try {
      await addCartItem(item.id);
      setCartItemIds((current) => new Set(current).add(item.id));
      setCartFeedback(`已将 ${item.brandName} ${item.model} 加入购物车。`);
    } catch (exception) {
      setCartFeedback(exception instanceof Error ? exception.message : "加入购物车失败");
    } finally {
      setAddingCartItemId(null);
    }
  };

  const handleComposerKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === "Enter" && !event.shiftKey) {
      event.preventDefault();
      event.currentTarget.form?.requestSubmit();
    }
  };

  return (
    <>
      <SkipLink />
      <main id="main-content" className="appShell recommendShell" tabIndex={-1}>
      <header className="recommendHeader">
        <AppLink className="recommendBackButton" to="/" navigate={navigate}>
          <ArrowLeft size={18} aria-hidden="true" />
          返回首页
        </AppLink>
        <span className="recommendHeaderIcon">
          <Bot size={22} aria-hidden="true" />
        </span>
        <div className="recommendHeaderText">
          <h1>DeepSeek推荐</h1>
          <p>仅查询本地数据库，结果可核对。</p>
        </div>
        <AppLink className="cartNavButton" to="/cart" navigate={navigate}>
          <ShoppingCart size={18} aria-hidden="true" />
          购物车
        </AppLink>
      </header>

       <aside className="recommendResults">
        <div className="recommendResultsHeading">
          <div>
            <span>候选机型</span>
            <h2>推荐清单</h2>
          </div>
          <strong>{formatNumber(recommendationCards.length)}</strong>
        </div>
        {cartFeedback && <p className="recommendCartStatus" role="status">{cartFeedback}</p>}
        {recommendationCards.length ? (
          <div className="recommendListScroller laptopGrid">
            {recommendationCards.map(({ key, detail, reason }) => (
              <LaptopCard
                key={key}
                item={detail}
                onDetail={() => setSelectedRecommendation({ detail, reason })}
                onAddToCart={() => addToCart(detail)}
                isInCart={cartItemIds.has(detail.id)}
                addingToCart={addingCartItemId === detail.id}
              />
            ))}
          </div>
        ) : (
          <div className="statusBox">
            {recommendations.length ? "推荐结果缺少可展示的机型详情。" : "推荐结果会显示在这里。"}
          </div>
        )}
      </aside>

        <section className="chatPanel" aria-label="推荐对话">
          <div className="sessionToolbar">
          <select
            name="session"
            aria-label="选择会话"
            value={activeSession.id}
            autoComplete="off"
            onChange={(event) => switchSession(event.target.value)}
          >
            {sessionState.sessions.map((session) => (
              <option key={session.id} value={session.id}>
                {session.title}
              </option>
            ))}
          </select>
          <button className="ghostButton compactButton" type="button" onClick={createNewSession} disabled={pending}>
            <Plus size={17} aria-hidden="true" />
            新建
          </button>
          <button className="ghostButton compactButton" type="button" onClick={deleteActiveSession} disabled={pending}>
            <Trash2 size={17} aria-hidden="true" />
            删除
          </button>
        </div>

        <div className="messageList" aria-live="polite" aria-relevant="additions">
          {messages.map((message, index) => (
            <div key={`${message.role}-${index}`} className={`messageBubble ${message.role}`}>
              {message.content}
            </div>
          ))}
          {pending && <div className="messageBubble assistant">正在查询数据库并整理推荐…</div>}
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
            className={input ? "composerInput composerInputFilled" : "composerInput composerInputEmpty"}
            name="message"
            aria-label="输入推荐需求"
            value={input}
            autoComplete="off"
            onChange={(event) => setInput(event.target.value)}
            onKeyDown={handleComposerKeyDown}
            rows={1}
          />
          <button className="primaryButton iconOnlyText" type="submit" disabled={pending}>
            {pending ? (
              <>
                <span className="buttonSpinner" aria-hidden="true" />
                正在发送…
              </>
            ) : (
              <>
                <Send size={18} aria-hidden="true" />
                发送
              </>
            )}
          </button>
        </form>
        </section>
      {selectedRecommendation && (
        <DetailModal
          detail={selectedRecommendation.detail}
          recommendationReason={selectedRecommendation.reason}
          onClose={() => setSelectedRecommendation(null)}
        />
      )}
      </main>
    </>
  );
}

function CartPage({ navigate }: { navigate: (path: string) => void }) {
  const [items, setItems] = useState<LaptopListItem[]>([]);
  const [selectedIds, setSelectedIds] = useState<Set<number>>(() => new Set());
  const [loading, setLoading] = useState(true);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  const [selected, setSelected] = useState<LaptopDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);

  const loadCart = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const nextItems = await getCartItems();
      setItems(nextItems);
      setSelectedIds((current) => new Set([...current].filter((id) => nextItems.some((item) => item.id === id))));
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "购物车读取失败");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadCart();
  }, [loadCart]);

  const allSelected = items.length > 0 && items.every((item) => selectedIds.has(item.id));
  const selectedCount = selectedIds.size;

  const toggleItem = (id: number) => {
    setSelectedIds((current) => {
      const next = new Set(current);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  };

  const toggleAll = () => {
    setSelectedIds(allSelected ? new Set() : new Set(items.map((item) => item.id)));
  };

  const openDetail = (id: number) => {
    setDetailLoading(true);
    getLaptopDetail(id)
      .then(setSelected)
      .catch((exception: Error) => setError(exception.message))
      .finally(() => setDetailLoading(false));
  };

  const removeSelected = async () => {
    const laptopIds = [...selectedIds];
    if (!laptopIds.length || pending) {
      return;
    }
    setPending(true);
    setError("");
    try {
      await deleteCartItems(laptopIds);
      await loadCart();
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "删除购物车项目失败");
    } finally {
      setPending(false);
    }
  };

  const removeAll = async () => {
    if (!items.length || pending || !window.confirm("确认清空购物车中的全部笔记本吗？")) {
      return;
    }
    setPending(true);
    setError("");
    try {
      await clearCart();
      await loadCart();
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "清空购物车失败");
    } finally {
      setPending(false);
    }
  };

  return (
    <>
      <SkipLink />
      <main id="main-content" className="appShell cartShell" tabIndex={-1}>
        <header className="recommendHeader cartHeader">
          <AppLink className="recommendBackButton" to="/" navigate={navigate}>
            <ArrowLeft size={18} aria-hidden="true" />
            返回首页
          </AppLink>
          <span className="recommendHeaderIcon">
            <ShoppingCart size={22} aria-hidden="true" />
          </span>
          <div className="recommendHeaderText">
            <h1>购物车</h1>
            <p>已选机型保存在数据库中，重启页面后仍会保留。</p>
          </div>
        </header>

        <section className="cartPanel" aria-busy={loading || pending}>
          {error && <div className="errorBox" role="alert">{error}</div>}
          {loading && <div className="statusBox" role="status" aria-live="polite">正在读取购物车…</div>}
          {!loading && !error && !items.length && (
            <div className="statusBox cartEmptyState" role="status">
              <p>购物车还是空的。请从筛选结果中加入候选机型。</p>
              <AppLink className="primaryAction emptyCartAction" to="/filter" navigate={navigate}>
                <SlidersHorizontal size={18} aria-hidden="true" />
                前往筛选
              </AppLink>
            </div>
          )}
          {!loading && items.length > 0 && (
            <div className="cartWorkspace">
              <div className="cartCardScroller">
                <div className="cartCardGrid laptopGrid">
                  {items.map((item) => (
                    <LaptopCard
                      key={item.id}
                      item={item}
                      onDetail={() => openDetail(item.id)}
                      isSelected={selectedIds.has(item.id)}
                      selectionControl={
                        <label className="cartCardSelect">
                          <input
                            type="checkbox"
                            checked={selectedIds.has(item.id)}
                            onChange={() => toggleItem(item.id)}
                            disabled={pending}
                          />
                          <span>{selectedIds.has(item.id) ? "已选择" : "选择"}</span>
                        </label>
                      }
                    />
                  ))}
                </div>
              </div>
            </div>
          )}
          {!loading && items.length > 0 && (
            <div className="cartToolbar">
              <button className="ghostButton compactButton" type="button" onClick={toggleAll} disabled={pending}>
                {allSelected ? "取消全选" : "全选"}
              </button>
              <button className="ghostButton compactButton cartDeleteButton" type="button" onClick={removeSelected} disabled={!selectedCount || pending}>
                <Trash2 size={17} aria-hidden="true" />
                删除已选{selectedCount ? ` (${formatNumber(selectedCount)})` : ""}
              </button>
              <button className="ghostButton compactButton cartClearButton" type="button" onClick={removeAll} disabled={pending}>
                <Trash2 size={17} aria-hidden="true" />
                一键清空
              </button>
            </div>
          )}
        </section>
        {detailLoading && <div className="floatingStatus" role="status" aria-live="polite">正在读取详情…</div>}
        {selected && <DetailModal detail={selected} onClose={() => setSelected(null)} />}
      </main>
    </>
  );
}

function SelectField({
  label,
  name,
  value,
  options,
  suffix,
  onChange
}: {
  label: string;
  name: string;
  value: string;
  options: string[];
  suffix?: string;
  onChange: (value: string) => void;
}) {
  return (
    <label className="field">
      <span>{label}</span>
      <select name={name} value={value} autoComplete="off" onChange={(event) => onChange(event.target.value)}>
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

function SkipLink() {
  return (
    <a className="skipLink" href="#main-content">
      跳至主要内容
    </a>
  );
}

function AppLink({
  className,
  to,
  navigate,
  children
}: {
  className: string;
  to: string;
  navigate: (path: string) => void;
  children: ReactNode;
}) {
  const handleClick = (event: MouseEvent<HTMLAnchorElement>) => {
    if (event.button !== 0 || event.metaKey || event.altKey || event.ctrlKey || event.shiftKey) {
      return;
    }
    event.preventDefault();
    navigate(to);
  };

  return (
    <a className={className} href={to} onClick={handleClick}>
      {children}
    </a>
  );
}

function LaptopCard({
  item,
  onDetail,
  onAddToCart,
  selectionControl,
  isInCart = false,
  isSelected = false,
  addingToCart = false
}: {
  item: LaptopListItem;
  onDetail: () => void;
  onAddToCart?: () => void;
  selectionControl?: ReactNode;
  isInCart?: boolean;
  isSelected?: boolean;
  addingToCart?: boolean;
}) {
  return (
    <article className={`laptopCard${onAddToCart || selectionControl ? " withSecondaryAction" : ""}${isSelected ? " isSelected" : ""}`}>
      <div className="cardBody">
      <div className="cardTop">
        <div className="thumbBox">
          {item.imageUrl ? (
            <img src={item.imageUrl} alt={`${item.brandName} ${item.model} 笔记本电脑`} width={72} height={72} loading="lazy" />
          ) : (
            <Laptop size={28} aria-hidden="true" />
          )}
          </div>
          <div className="titleBlock">
            <div>
              <span className="brandText">{item.brandName}</span>
              <h2>{item.model}</h2>
            </div>
          </div>
        </div>
        <div className="cardPriceLine">
          <span>参考价</span>
          <strong>{money(item.latestPrice)}</strong>
        </div>
        <div className="specRow">
          <span>
            <Cpu size={16} aria-hidden="true" />
            {text(item.cpuModel)}
          </span>
          <span>
            <HardDrive size={16} aria-hidden="true" />
            {capacity(item.memoryCapacityGb)} / {capacity(item.storageCapacityGb)}
          </span>
          <span>
            <Monitor size={16} aria-hidden="true" />
            {screen(item)}
          </span>
        </div>
        <div className="cardActions">
          {selectionControl}
          <button className="detailButton" type="button" onClick={onDetail}>
            查看详情
            <ChevronRight size={16} aria-hidden="true" />
          </button>
          {onAddToCart && (
            <button className="cartAddButton" type="button" onClick={onAddToCart} disabled={isInCart || addingToCart}>
              <ShoppingCart size={16} aria-hidden="true" />
              {addingToCart ? "正在加入…" : isInCart ? "已加入" : "加入购物车"}
            </button>
          )}
        </div>
      </div>
    </article>
  );
}

function DetailModal({
  detail,
  recommendationReason,
  onClose
}: {
  detail: LaptopDetail;
  recommendationReason?: string;
  onClose: () => void;
}) {
  const closeButtonRef = useRef<HTMLButtonElement>(null);
  const rows = [
    ["价格", money(detail.latestPrice)],
    ["产品类型", text(detail.productType)],
    ["用途定位", text(detail.usagePositioning)],
    ["CPU", joinText(detail.cpuBrand, detail.cpuModel)],
    ["CPU 核心/线程", joinText(detail.cpuCoreCount, detail.cpuThreadCount, "/")],
    ["GPU", joinText(detail.gpuBrand, detail.gpuModel)],
    ["显卡类型", text(detail.gpuType)],
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

  useEffect(() => {
    const previouslyFocusedElement = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    const focusCloseButton = window.setTimeout(() => closeButtonRef.current?.focus(), 0);
    const onKeyDown = (event: globalThis.KeyboardEvent) => {
      if (event.key === "Escape") {
        onClose();
      }
    };
    window.addEventListener("keydown", onKeyDown);
    return () => {
      window.clearTimeout(focusCloseButton);
      window.removeEventListener("keydown", onKeyDown);
      previouslyFocusedElement?.focus();
    };
  }, [onClose]);

  return (
    <div className="modalBackdrop">
      <button className="modalDismissArea" type="button" aria-label="关闭笔记本详情" onClick={onClose} />
      <article className="detailModal" role="dialog" aria-modal="true" aria-labelledby="detail-modal-title">
        <button ref={closeButtonRef} className="modalClose" type="button" aria-label="关闭笔记本详情" onClick={onClose}>
          <X size={18} aria-hidden="true" />
        </button>
        <div className="detailHead">
          <div className="imageBox large">
            {detail.imageUrl ? (
              <img src={detail.imageUrl} alt={`${detail.brandName} ${detail.model} 笔记本电脑`} width={180} height={150} />
            ) : (
              <Laptop size={42} aria-hidden="true" />
            )}
          </div>
          <div>
            <span className="brandText">{detail.brandName}</span>
            <h2 id="detail-modal-title">{detail.model}</h2>
            <p>{detail.rawTitle}</p>
          </div>
        </div>
        {recommendationReason && (
          <p className="recommendationReason">
            <strong>推荐理由</strong>
            {recommendationReason}
          </p>
        )}
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
  return currencyFormatter.format(value);
}

function capacity(value?: number) {
  return value == null ? "未知" : `${value}\u00a0GB`;
}

function weight(value?: number) {
  return value == null ? "重量未知" : `${value}\u00a0kg`;
}

function screen(item: Pick<LaptopListItem, "screenSizeInch" | "screenResolution" | "refreshRateHz"> & { screenRefreshRateHz?: number }) {
  const size = item.screenSizeInch ? `${item.screenSizeInch}\u00a0英寸` : "尺寸未知";
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
  return value == null ? "例如：3000…" : `例如：${formatNumber(value)}…`;
}

function formatRangeMax(value?: number | null, fallback = "1.5") {
  return value == null ? `例如：${fallback}…` : `例如：${formatNumber(value)}…`;
}

function formatNumber(value: number) {
  return numberFormatter.format(value);
}

function readFiltersFromSearch(search: string): LaptopFilters {
  const params = new URLSearchParams(search);
  const filters = { ...emptyFilters };
  filterFields.forEach((field) => {
    const value = params.get(field);
    if (value !== null) {
      filters[field] = value;
    }
  });
  if (!sortOptions.some((option) => option.value === filters.sort)) {
    filters.sort = emptyFilters.sort;
  }
  return filters;
}

function readPageFromSearch(search: string) {
  const page = Number(new URLSearchParams(search).get("page"));
  return Number.isInteger(page) && page > 0 ? page : 1;
}

function buildFilterSearch(filters: LaptopFilters, page: number) {
  const params = new URLSearchParams();
  filterFields.forEach((field) => {
    const value = filters[field].trim();
    if (value && (field !== "sort" || value !== emptyFilters.sort)) {
      params.set(field, value);
    }
  });
  if (page > 1) {
    params.set("page", String(page));
  }
  const query = params.toString();
  return query ? `?${query}` : "";
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
  return firstUserMessage.length > 22 ? `${firstUserMessage.slice(0, 22)}…` : firstUserMessage;
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

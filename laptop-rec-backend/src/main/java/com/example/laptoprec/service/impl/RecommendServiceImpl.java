package com.example.laptoprec.service.impl;

import com.example.laptoprec.common.PageResult;
import com.example.laptoprec.config.DeepSeekProperties;
import com.example.laptoprec.dto.LaptopQueryDTO;
import com.example.laptoprec.dto.RecommendChatRequest;
import com.example.laptoprec.dto.RecommendMessageDTO;
import com.example.laptoprec.service.LaptopService;
import com.example.laptoprec.service.RecommendService;
import com.example.laptoprec.vo.LaptopDetailVO;
import com.example.laptoprec.vo.LaptopListItemVO;
import com.example.laptoprec.vo.RecommendChatVO;
import com.example.laptoprec.vo.RecommendationVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class RecommendServiceImpl implements RecommendService {
    private static final int MAX_HISTORY_MESSAGES = 20;
    private static final int MAX_TOOL_ROUNDS = 4;
    private static final int MAX_TOOL_RESULTS = 10;
    private static final int MAX_RECOMMENDATIONS = 10;
    private static final int MAX_REASON_LENGTH = 600;
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final DeepSeekProperties deepSeekProperties;
    private final LaptopService laptopService;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    public RecommendServiceImpl(
            DeepSeekProperties deepSeekProperties,
            LaptopService laptopService,
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper
    ) {
        this.deepSeekProperties = deepSeekProperties;
        this.laptopService = laptopService;
        this.webClientBuilder = webClientBuilder;
        this.objectMapper = objectMapper;
    }

    @Override
    public RecommendChatVO chat(RecommendChatRequest request) {
        validateConfig();
        List<Map<String, Object>> messages = buildMessages(request);
        List<LaptopListItemVO> lastSearchResults = new ArrayList<>();
        Map<Long, LaptopDetailVO> detailById = new LinkedHashMap<>();

        for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
            JsonNode response = callDeepSeek(messages);
            JsonNode message = response.path("choices").path(0).path("message");
            JsonNode toolCalls = message.path("tool_calls");
            if (toolCalls.isArray() && toolCalls.size() > 0) {
                messages.add(objectMapper.convertValue(message, MAP_TYPE));
                for (JsonNode toolCall : toolCalls) {
                    messages.add(executeToolCall(toolCall, lastSearchResults, detailById));
                }
                continue;
            }
            return buildFinalResponse(message.path("content").asText(""), lastSearchResults, detailById);
        }

        RecommendChatVO response = new RecommendChatVO();
        response.setRecommendations(buildFallbackRecommendations(lastSearchResults, detailById));
        response.setFollowUpQuestions(defaultFollowUpQuestions());
        response.setReply(buildDatabaseReply(response.getRecommendations(), response.getFollowUpQuestions(), null));
        return response;
    }

    private void validateConfig() {
        if (isBlank(deepSeekProperties.getApiKey())) {
            throw new IllegalArgumentException("未配置 DeepSeek API Key，请在 laptop-rec-backend/.env.local 或 application-local.yml 中配置");
        }
    }

    private List<Map<String, Object>> buildMessages(RecommendChatRequest request) {
        if (request == null || request.getMessages() == null || request.getMessages().isEmpty()) {
            throw new IllegalArgumentException("推荐对话消息不能为空");
        }
        List<RecommendMessageDTO> sourceMessages = request.getMessages();
        List<Map<String, Object>> messages = new ArrayList<>();
        if (isNewConversation(sourceMessages)) {
            messages.add(Map.of("role", "system", "content", systemPrompt()));
        }

        int start = Math.max(0, sourceMessages.size() - MAX_HISTORY_MESSAGES);
        for (int i = start; i < sourceMessages.size(); i++) {
            RecommendMessageDTO message = sourceMessages.get(i);
            String role = normalizeRole(message.getRole());
            String content = normalizeText(message.getContent());
            if (content == null) {
                continue;
            }
            messages.add(Map.of("role", role, "content", content));
        }
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("推荐对话消息不能为空");
        }
        return messages;
    }

    private boolean isNewConversation(List<RecommendMessageDTO> messages) {
        int userMessageCount = 0;
        for (RecommendMessageDTO message : messages) {
            if ("user".equals(normalizeText(message.getRole())) && normalizeText(message.getContent()) != null) {
                userMessageCount++;
            }
        }
        return userMessageCount <= 1;
    }

    private String systemPrompt() {
        return """
                你是笔记本电脑推荐系统的中文导购 Agent。
                你只能通过工具查询本系统数据库，不能编造数据库中不存在的机型、价格或参数。
                不要引用外部评测、实时价格或本系统数据库之外的机型信息。
                可以基于你对 CPU、GPU、内存、屏幕、重量、接口等硬件的一般知识，对工具返回的数据库机型做具体评价；但机型、价格和参数必须以工具返回数据为准。
                recommendations 只能使用本轮工具返回过的 laptop id，不得输出工具结果中没有的型号或 id。
                recommendations 中每一条 reason 必须评价同一个 laptopId 对应的机型，不要把 A 机型的参数或结论写到 B 机型。
                reason 要具体说明为什么适合或不适合用户需求，尽量覆盖性能、显卡/游戏或创作能力、便携性、屏幕、内存/硬盘、价格取舍中的相关项。
                如果用户信息不足，先追问预算、用途、便携性、游戏/显卡需求和品牌偏好。
                如果信息足够，先调用 search_laptops 查询候选，再调用 get_laptop_detail 获取最终推荐机型详情。
                最终回答必须是严格 JSON 对象，不要使用 Markdown 代码块，格式如下：
                {
                  "reply": "面向用户的中文说明；如果需要追问，就说明还缺什么信息。",
                  "recommendations": [
                    {"laptopId": 1, "reason": "针对该 laptopId 的具体推荐理由"}
                  ],
                  "followUpQuestions": ["需要继续追问的问题"]
                }
                推荐数量最多 10 台，超出 10 台将被截断，不会被纳入答案中。
                """;
    }

    private String normalizeRole(String role) {
        String text = normalizeText(role);
        if ("user".equals(text) || "assistant".equals(text)) {
            return text;
        }
        throw new IllegalArgumentException("只支持 user 或 assistant 角色消息");
    }

    private JsonNode callDeepSeek(List<Map<String, Object>> messages) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", deepSeekProperties.getModel());
        body.put("messages", messages);
        body.put("tools", buildTools());
        body.put("tool_choice", "auto");
        body.put("temperature", 0.3);
        body.put("max_tokens", 3500);

        WebClient webClient = webClientBuilder
                .baseUrl(normalizeBaseUrl(deepSeekProperties.getBaseUrl()))
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + deepSeekProperties.getApiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        JsonNode response = webClient.post()
                .uri("/chat/completions")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block(Duration.ofSeconds(60));
        if (response == null || response.path("choices").isEmpty()) {
            throw new IllegalStateException("DeepSeek 未返回有效推荐结果");
        }
        return response;
    }

    private List<Map<String, Object>> buildTools() {
        return List.of(
                buildSearchTool(),
                buildDetailTool()
        );
    }

    private Map<String, Object> buildSearchTool() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("keyword", stringSchema("在型号、原始标题、品牌中模糊搜索"));
        properties.put("brand", stringSchema("品牌精确筛选，例如 联想、惠普、华为"));
        properties.put("productType", stringSchema("产品类型精确筛选，例如 家用、商用"));
        properties.put("usageKeyword", stringSchema("用途定位关键词，例如 轻薄、商务、游戏"));
        properties.put("cpuKeyword", stringSchema("CPU 型号关键词，例如 Ultra、i7、锐龙"));
        properties.put("gpuKeyword", stringSchema("GPU 型号关键词，例如 RTX、Arc、Radeon"));
        properties.put("gpuType", stringSchema("显卡类型，例如 integrated、discrete"));
        properties.put("minPrice", numberSchema("最低价格"));
        properties.put("maxPrice", numberSchema("最高价格"));
        properties.put("minMemoryGb", integerSchema("最低内存容量 GB"));
        properties.put("minStorageGb", integerSchema("最低硬盘容量 GB"));
        properties.put("minScreenSize", numberSchema("最低屏幕尺寸，单位英寸"));
        properties.put("maxWeightKg", numberSchema("最高重量，单位 kg"));
        properties.put("sort", enumSchema("排序方式", List.of("latest", "priceAsc", "priceDesc", "weightAsc", "screenDesc")));
        properties.put("size", integerSchema("返回候选数量，最大 10"));

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("additionalProperties", false);

        return tool("search_laptops", "按安全白名单条件查询数据库中的候选笔记本，最多返回 10 条。", parameters);
    }

    private Map<String, Object> buildDetailTool() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("id", integerSchema("笔记本 id"));

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", List.of("id"));
        parameters.put("additionalProperties", false);

        return tool("get_laptop_detail", "根据笔记本 id 查询完整配置详情。", parameters);
    }

    private Map<String, Object> tool(String name, String description, Map<String, Object> parameters) {
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", name);
        function.put("description", description);
        function.put("parameters", parameters);

        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("type", "function");
        tool.put("function", function);
        return tool;
    }

    private Map<String, Object> stringSchema(String description) {
        return Map.of("type", "string", "description", description);
    }

    private Map<String, Object> numberSchema(String description) {
        return Map.of("type", "number", "description", description);
    }

    private Map<String, Object> integerSchema(String description) {
        return Map.of("type", "integer", "description", description);
    }

    private Map<String, Object> enumSchema(String description, List<String> values) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "string");
        schema.put("description", description);
        schema.put("enum", values);
        return schema;
    }

    private Map<String, Object> executeToolCall(
            JsonNode toolCall,
            List<LaptopListItemVO> lastSearchResults,
            Map<Long, LaptopDetailVO> detailById
    ) {
        String toolCallId = toolCall.path("id").asText();
        String toolName = toolCall.path("function").path("name").asText();
        JsonNode arguments = parseArguments(toolCall.path("function").path("arguments").asText("{}"));

        Object content;
        try {
            content = switch (toolName) {
                case "search_laptops" -> searchLaptops(arguments, lastSearchResults);
                case "get_laptop_detail" -> getLaptopDetail(arguments, detailById);
                default -> Map.of("error", "不支持的工具：" + toolName);
            };
        } catch (Exception exception) {
            content = Map.of("error", exception.getMessage());
        }

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "tool");
        message.put("tool_call_id", toolCallId);
        message.put("content", toJson(content));
        return message;
    }

    private JsonNode parseArguments(String arguments) {
        try {
            return objectMapper.readTree(arguments);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("工具参数不是合法 JSON");
        }
    }

    private Map<String, Object> searchLaptops(JsonNode arguments, List<LaptopListItemVO> lastSearchResults) {
        LaptopQueryDTO query = new LaptopQueryDTO();
        query.setKeyword(textArg(arguments, "keyword"));
        query.setBrand(textArg(arguments, "brand"));
        query.setProductType(textArg(arguments, "productType"));
        query.setUsageKeyword(textArg(arguments, "usageKeyword"));
        query.setCpuKeyword(textArg(arguments, "cpuKeyword"));
        query.setGpuKeyword(textArg(arguments, "gpuKeyword"));
        query.setGpuType(textArg(arguments, "gpuType"));
        query.setMinPrice(decimalArg(arguments, "minPrice"));
        query.setMaxPrice(decimalArg(arguments, "maxPrice"));
        query.setMinMemoryGb(intArg(arguments, "minMemoryGb"));
        query.setMinStorageGb(intArg(arguments, "minStorageGb"));
        query.setMinScreenSize(decimalArg(arguments, "minScreenSize"));
        query.setMaxWeightKg(decimalArg(arguments, "maxWeightKg"));
        query.setSort(textArg(arguments, "sort"));
        query.setPage(1);
        query.setSize(clamp(intArg(arguments, "size"), 1, MAX_TOOL_RESULTS, MAX_TOOL_RESULTS));

        PageResult<LaptopListItemVO> page = laptopService.queryLaptops(query);
        lastSearchResults.clear();
        lastSearchResults.addAll(page.getRecords());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", page.getTotal());
        result.put("records", page.getRecords());
        return result;
    }

    private LaptopDetailVO getLaptopDetail(JsonNode arguments, Map<Long, LaptopDetailVO> detailById) {
        Long id = longArg(arguments, "id");
        if (id == null) {
            throw new IllegalArgumentException("get_laptop_detail 需要 id");
        }
        LaptopDetailVO detail = laptopService.getLaptopDetail(id);
        detailById.put(id, detail);
        return detail;
    }

    private RecommendChatVO buildFinalResponse(
            String content,
            List<LaptopListItemVO> lastSearchResults,
            Map<Long, LaptopDetailVO> detailById
    ) {
        RecommendChatVO response = new RecommendChatVO();
        JsonNode json = parseJsonObjectContent(content);
        List<RecommendationVO> recommendations = json == null
                ? buildFallbackRecommendations(lastSearchResults, detailById)
                : readRecommendations(json.path("recommendations"), lastSearchResults, detailById);
        List<String> followUpQuestions = json == null
                ? Collections.emptyList()
                : readStringArray(json.path("followUpQuestions"));
        if (recommendations.isEmpty() && followUpQuestions.isEmpty()) {
            followUpQuestions = defaultFollowUpQuestions();
        }
        String modelReply = json == null ? null : json.path("reply").asText(null);
        response.setRecommendations(recommendations);
        response.setFollowUpQuestions(followUpQuestions);
        response.setReply(buildDatabaseReply(recommendations, followUpQuestions, modelReply));
        return response;
    }

    private String buildDatabaseReply(
            List<RecommendationVO> recommendations,
            List<String> followUpQuestions,
            String modelReply
    ) {
        if (recommendations != null && !recommendations.isEmpty()) {
            StringBuilder builder = new StringBuilder("我只根据数据库中的机型给出推荐：");
            for (int i = 0; i < recommendations.size(); i++) {
                RecommendationVO recommendation = recommendations.get(i);
                LaptopDetailVO detail = recommendation.getDetail();
                if (detail == null) {
                    continue;
                }
                builder.append("\n")
                        .append(i + 1)
                        .append(". ")
                        .append(laptopName(detail))
                        .append("：")
                        .append("价格 ")
                        .append(formatMoney(detail.getLatestPrice()))
                        .append("，CPU ")
                        .append(text(detail.getCpuModel()))
                        .append("，GPU ")
                        .append(text(detail.getGpuModel()))
                        .append("，内存 ")
                        .append(formatGb(detail.getMemoryCapacityGb()))
                        .append("，硬盘 ")
                        .append(formatGb(detail.getStorageCapacityGb()))
                        .append("，屏幕 ")
                        .append(formatScreen(detail))
                        .append("，重量 ")
                        .append(formatWeight(detail.getWeightKg()))
                        .append("。推荐原因：")
                        .append(recommendation.getReason());
            }
            return builder.toString();
        }

        String naturalReply = normalizeNaturalReply(modelReply);
        if (!isBlank(naturalReply)) {
            return appendFollowUpQuestions(naturalReply, followUpQuestions);
        }
        if (followUpQuestions != null && !followUpQuestions.isEmpty()) {
            return appendFollowUpQuestions("我还需要更多信息才能从数据库里给出可靠推荐。", followUpQuestions);
        }
        return "当前条件下没有查到足够的数据库候选，请补充预算、用途、便携性或品牌偏好后再试。";
    }

    private String appendFollowUpQuestions(String reply, List<String> followUpQuestions) {
        if (followUpQuestions == null || followUpQuestions.isEmpty()) {
            return reply;
        }
        return reply + "\n\n还需要确认：" + String.join("；", followUpQuestions);
    }

    private String normalizeNaturalReply(String reply) {
        String text = normalizeText(reply);
        if (text == null || looksLikeJson(text)) {
            return null;
        }
        return text;
    }

    private boolean looksLikeJson(String text) {
        String value = normalizeText(text);
        return value != null && (value.startsWith("{") || value.startsWith("[") || value.contains("\"recommendations\""));
    }

    private List<String> defaultFollowUpQuestions() {
        return List.of("你的预算上限是多少？", "主要用途是办公、编程、游戏还是设计？", "是否有便携性或品牌偏好？");
    }

    private String laptopName(LaptopDetailVO detail) {
        return joinText(detail.getBrandName(), detail.getModel(), " ");
    }

    private String formatMoney(BigDecimal value) {
        if (value == null) {
            return "未知";
        }
        return "￥" + value.stripTrailingZeros().toPlainString();
    }

    private String formatGb(Integer value) {
        return value == null ? "未知" : value + "GB";
    }

    private String formatWeight(BigDecimal value) {
        if (value == null) {
            return "未知";
        }
        return value.stripTrailingZeros().toPlainString() + "kg";
    }

    private String formatScreen(LaptopDetailVO detail) {
        String size = detail.getScreenSizeInch() == null
                ? "尺寸未知"
                : detail.getScreenSizeInch().stripTrailingZeros().toPlainString() + "英寸";
        String resolution = text(detail.getScreenResolution());
        String refreshRate = detail.getScreenRefreshRateHz() == null ? null : detail.getScreenRefreshRateHz() + "Hz";
        return joinText(joinText(size, resolution, " "), refreshRate, " ");
    }

    private String text(String value) {
        return isBlank(value) ? "未知" : value.trim();
    }

    private String joinText(String left, String right, String separator) {
        String normalizedLeft = normalizeText(left);
        String normalizedRight = normalizeText(right);
        if (normalizedLeft == null && normalizedRight == null) {
            return "未知";
        }
        if (normalizedLeft == null) {
            return normalizedRight;
        }
        if (normalizedRight == null) {
            return normalizedLeft;
        }
        return normalizedLeft + separator + normalizedRight;
    }

    private JsonNode parseJsonObjectContent(String content) {
        String text = normalizeText(content);
        if (text == null) {
            return null;
        }
        List<String> candidates = new ArrayList<>();
        candidates.add(text);

        String fenced = extractJsonFence(text);
        if (fenced != null) {
            candidates.add(fenced);
        }

        String objectText = extractJsonObject(text);
        if (objectText != null) {
            candidates.add(objectText);
        }

        for (String candidate : candidates) {
            try {
                JsonNode json = objectMapper.readTree(candidate);
                if (json.isObject()) {
                    return json;
                }
            } catch (JsonProcessingException exception) {
                // Try the next candidate shape.
            }
        }
        return null;
    }

    private String extractJsonFence(String text) {
        int fenceStart = text.indexOf("```");
        if (fenceStart < 0) {
            return null;
        }
        int contentStart = text.indexOf('\n', fenceStart + 3);
        if (contentStart < 0) {
            return null;
        }
        int fenceEnd = text.indexOf("```", contentStart + 1);
        if (fenceEnd < 0) {
            return null;
        }
        return text.substring(contentStart + 1, fenceEnd).trim();
    }

    private String extractJsonObject(String text) {
        int firstBrace = text.indexOf('{');
        int lastBrace = text.lastIndexOf('}');
        if (firstBrace < 0 || lastBrace <= firstBrace) {
            return null;
        }
        return text.substring(firstBrace, lastBrace + 1).trim();
    }

    private List<String> readStringArray(JsonNode node) {
        if (!node.isArray()) {
            return Collections.emptyList();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            String value = normalizeText(item.asText());
            if (value != null) {
                values.add(value);
            }
        }
        return values;
    }

    private List<RecommendationVO> readRecommendations(
            JsonNode node,
            List<LaptopListItemVO> lastSearchResults,
            Map<Long, LaptopDetailVO> detailById
    ) {
        List<RecommendationVO> recommendations = new ArrayList<>();
        Set<Long> usedIds = new LinkedHashSet<>();
        Set<Long> allowedIds = allowedRecommendationIds(lastSearchResults, detailById);

        if (node.isArray()) {
            for (JsonNode item : node) {
                Long laptopId = longArg(item, "laptopId");
                if (laptopId == null
                        || !allowedIds.contains(laptopId)
                        || usedIds.contains(laptopId)
                        || recommendations.size() >= MAX_RECOMMENDATIONS) {
                    continue;
                }
                LaptopDetailVO detail = loadDetail(laptopId, detailById);
                if (detail == null) {
                    continue;
                }
                RecommendationVO recommendation = new RecommendationVO();
                recommendation.setLaptopId(laptopId);
                recommendation.setReason(readRecommendationReason(item));
                recommendation.setDetail(detail);
                recommendations.add(recommendation);
                usedIds.add(laptopId);
            }
        }
        if (recommendations.isEmpty()) {
            return buildFallbackRecommendations(lastSearchResults, detailById);
        }
        return recommendations;
    }

    private Set<Long> allowedRecommendationIds(List<LaptopListItemVO> lastSearchResults, Map<Long, LaptopDetailVO> detailById) {
        Set<Long> ids = new LinkedHashSet<>(detailById.keySet());
        for (LaptopListItemVO item : lastSearchResults) {
            if (item.getId() != null) {
                ids.add(item.getId());
            }
        }
        return ids;
    }

    private List<RecommendationVO> buildFallbackRecommendations(
            List<LaptopListItemVO> lastSearchResults,
            Map<Long, LaptopDetailVO> detailById
    ) {
        List<RecommendationVO> recommendations = new ArrayList<>();
        Set<Long> ids = new LinkedHashSet<>(detailById.keySet());
        for (LaptopListItemVO item : lastSearchResults) {
            if (ids.size() >= MAX_RECOMMENDATIONS) {
                break;
            }
            if (item.getId() != null) {
                ids.add(item.getId());
            }
        }
        for (Long id : ids) {
            if (recommendations.size() >= MAX_RECOMMENDATIONS) {
                break;
            }
            LaptopDetailVO detail = loadDetail(id, detailById);
            if (detail == null) {
                continue;
            }
            RecommendationVO recommendation = new RecommendationVO();
            recommendation.setLaptopId(id);
            recommendation.setReason("模型未返回该机型的有效推荐理由，请结合数据库详情继续比较。");
            recommendation.setDetail(detail);
            recommendations.add(recommendation);
        }
        return recommendations;
    }

    private String readRecommendationReason(JsonNode item) {
        String reason = normalizeText(item.path("reason").asText(null));
        if (reason == null || looksLikeJson(reason)) {
            return "模型未返回该机型的有效推荐理由，请结合数据库详情继续比较。";
        }
        if (reason.length() > MAX_REASON_LENGTH) {
            return reason.substring(0, MAX_REASON_LENGTH).trim();
        }
        return reason;
    }

    private LaptopDetailVO loadDetail(Long id, Map<Long, LaptopDetailVO> detailById) {
        if (id == null) {
            return null;
        }
        LaptopDetailVO existing = detailById.get(id);
        if (existing != null) {
            return existing;
        }
        try {
            LaptopDetailVO detail = laptopService.getLaptopDetail(id);
            detailById.put(id, detail);
            return detail;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String textArg(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        return normalizeText(value.asText());
    }

    private Integer intArg(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        if (value.isNumber()) {
            return value.asInt();
        }
        String text = normalizeText(value.asText());
        return text == null ? null : Integer.valueOf(text);
    }

    private Long longArg(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        if (value.isNumber()) {
            return value.asLong();
        }
        String text = normalizeText(value.asText());
        return text == null ? null : Long.valueOf(text);
    }

    private BigDecimal decimalArg(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        if (value.isNumber()) {
            return value.decimalValue();
        }
        String text = normalizeText(value.asText());
        return text == null ? null : new BigDecimal(text);
    }

    private Integer clamp(Integer value, int min, int max, int fallback) {
        if (value == null) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }

    private String normalizeBaseUrl(String baseUrl) {
        String value = normalizeText(baseUrl);
        if (value == null) {
            return "https://api.deepseek.com";
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }

    private boolean isBlank(String value) {
        return normalizeText(value) == null;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("工具结果无法序列化为 JSON", exception);
        }
    }
}

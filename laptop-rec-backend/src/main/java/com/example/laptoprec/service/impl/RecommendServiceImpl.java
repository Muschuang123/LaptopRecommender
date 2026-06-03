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
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
            JsonNode response = callDeepSeek(messages, true);
            JsonNode message = response.path("choices").path(0).path("message");
            JsonNode toolCalls = message.path("tool_calls");
            String finishReason = response.path("choices").path(0).path("finish_reason").asText("");
            if (toolCalls.isArray() && toolCalls.size() > 0) {
                messages.add(objectMapper.convertValue(message, MAP_TYPE));
                for (JsonNode toolCall : toolCalls) {
                    messages.add(executeToolCall(toolCall, lastSearchResults, detailById));
                }
                continue;
            }
            if (!"stop".equals(finishReason)) {
                messages.add(Map.of(
                        "role", "user",
                        "content", "请等待推理完成后，只输出最终严格 JSON。不要输出 reasoning_content。"
                ));
                continue;
            }
            return buildFinalResponse(message.path("content").asText(""), lastSearchResults, detailById);
        }

        messages.add(Map.of(
                "role", "user",
                "content", "工具调用轮数已达上限。请基于已有工具结果输出最终严格 JSON；如果没有完全满足条件的数据库机型，请在 reply 中说明，并用 followUpQuestions 询问是否放宽条件。recommendations 只能使用工具返回过的 laptopId。不要再调用工具。"
        ));
        JsonNode response = callDeepSeek(messages, false);
        JsonNode message = response.path("choices").path(0).path("message");
        return buildFinalResponse(message.path("content").asText(""), lastSearchResults, detailById);
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
        messages.add(Map.of("role", "system", "content", systemPrompt()));

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

    private String systemPrompt() {
        return """
                你是中文笔记本推荐 Agent。只能推荐工具返回过的数据库机型；机型、价格、参数以工具结果为准，不编造。
                信息不足时追问预算、用途、便携性、显卡/游戏需求、品牌偏好。信息足够时先 search_laptops，再对最终推荐调用 get_laptop_detail。
                连续搜索仍无结果时停止调用工具，说明数据库没有完全满足条件的机型，并询问是否放宽预算、品牌、年份或显卡要求，你最多使用 4 轮工具调用来查询数据库。
                注意数据库中含有最新机型的相关数据，可以试探性地搜索新型号配件。
                最终只输出严格 JSON，不要 Markdown，不要 reasoning_content，不要在 reply 里写“还需要确认：”列表。
                recommendations 最多 10 条，laptopId 必须来自本轮工具结果；每条必须有非空字符串 reason，且只评价同一 laptopId。
                reason 可基于通用硬件知识评价性能、显卡/游戏或创作、便携、屏幕、内存/硬盘、价格取舍，但不得引用外部评测或数据库外机型。
                JSON 格式：
                {
                  "reply": "简短中文说明，只概括推荐方向，不逐项复述每台机器参数。",
                  "recommendations": [
                    {"laptopId": 1, "reason": "针对该 laptopId 的具体推荐理由"}
                  ],
                  "followUpQuestions": ["需要继续追问的问题"]
                }
                """;
    }

    private String normalizeRole(String role) {
        String text = normalizeText(role);
        if ("user".equals(text) || "assistant".equals(text)) {
            return text;
        }
        throw new IllegalArgumentException("只支持 user 或 assistant 角色消息");
    }

    private JsonNode callDeepSeek(List<Map<String, Object>> messages, boolean enableTools) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", deepSeekProperties.getModel());
        body.put("messages", messages);
        if (enableTools) {
            body.put("tools", buildTools());
            body.put("tool_choice", "auto");
        }
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
        appendDeepSeekResponseLog(response);
        return response;
    }

    private void appendDeepSeekResponseLog(JsonNode response) {
        try {
            Files.createDirectories(Path.of("logs"));
            Path logPath = Path.of("logs", "deepseek-chat-" + LocalDate.now() + ".jsonl");
            ObjectNode record = objectMapper.createObjectNode();
            record.put("createdAt", LocalDateTime.now().toString());
            record.put("model", deepSeekProperties.getModel());
            record.set("response", response);
            Files.writeString(
                    logPath,
                    objectMapper.writeValueAsString(record) + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (Exception exception) {
            // DeepSeek debug logs are best-effort and must not break recommendations.
        }
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
        mergeSearchResults(lastSearchResults, page.getRecords());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", page.getTotal());
        result.put("records", page.getRecords());
        return result;
    }

    private void mergeSearchResults(List<LaptopListItemVO> target, List<LaptopListItemVO> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        Set<Long> seenIds = new LinkedHashSet<>();
        for (LaptopListItemVO item : target) {
            if (item.getId() != null) {
                seenIds.add(item.getId());
            }
        }
        for (LaptopListItemVO item : records) {
            if (item.getId() == null || seenIds.contains(item.getId())) {
                continue;
            }
            target.add(item);
            seenIds.add(item.getId());
        }
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
                ? Collections.emptyList()
                : readRecommendations(json.path("recommendations"), lastSearchResults, detailById, json.path("reply").asText(null));
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
            String naturalReply = normalizeNaturalReply(modelReply);
            if (!isBlank(naturalReply)) {
                return appendFollowUpQuestions(naturalReply, followUpQuestions);
            }
            return buildCompactRecommendationReply(recommendations);
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
        return removeInlineFollowUpQuestions(reply);
    }

    private String removeInlineFollowUpQuestions(String reply) {
        String text = normalizeText(reply);
        if (text == null) {
            return "";
        }
        return text
                .replaceAll("(?s)\\n+\\s*还需要确认[:：].*$", "")
                .replaceAll("(?s)还需要确认[:：].*$", "")
                .trim();
    }

    private String normalizeNaturalReply(String reply) {
        String text = normalizeText(reply);
        if (text == null || looksLikeJson(text)) {
            return null;
        }
        return removeInlineFollowUpQuestions(text);
    }

    private boolean looksLikeJson(String text) {
        String value = normalizeText(text);
        return value != null && (value.startsWith("{") || value.startsWith("[") || value.contains("\"recommendations\""));
    }

    private List<String> defaultFollowUpQuestions() {
        return List.of("你的预算上限是多少？", "主要用途是办公、编程、游戏还是设计？", "是否有便携性或品牌偏好？");
    }

    private String buildCompactRecommendationReply(List<RecommendationVO> recommendations) {
        StringBuilder builder = new StringBuilder("推荐这几款：");
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
                    .append(recommendation.getReason());
        }
        return builder.toString();
    }

    private String laptopName(LaptopDetailVO detail) {
        return joinText(detail.getBrandName(), detail.getModel(), " ");
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
            Map<Long, LaptopDetailVO> detailById,
            String modelReply
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
                recommendation.setReason(readRecommendationReason(item, detail, modelReply));
                recommendation.setDetail(detail);
                recommendations.add(recommendation);
                usedIds.add(laptopId);
            }
        }
        if (node.isArray()) {
            return recommendations;
        }
        return Collections.emptyList();
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
            recommendation.setReason(buildFallbackReason(detail));
            recommendation.setDetail(detail);
            recommendations.add(recommendation);
        }
        return recommendations;
    }

    private String buildFallbackReason(LaptopDetailVO detail) {
        if (detail == null) {
            return "可作为当前需求下的候选机型，建议结合右侧详情继续比较。";
        }
        String usage = normalizeText(detail.getUsagePositioning());
        if (usage != null) {
            return "数据库将它标记为“" + usage + "”定位，可作为当前需求下的候选机型继续比较。";
        }
        String productType = normalizeText(detail.getProductType());
        if (productType != null) {
            return "这是一款“" + productType + "”机型，可结合预算、性能和便携性继续比较。";
        }
        return "可作为当前需求下的候选机型，建议结合右侧详情继续比较。";
    }

    private String readRecommendationReason(JsonNode item, LaptopDetailVO detail, String modelReply) {
        String reason = firstReasonText(
                item.path("reason"),
                item.path("recommendReason"),
                item.path("recommendationReason"),
                item.path("evaluation"),
                item.path("comment"),
                item.path("rationale"),
                item.path("why")
        );
        if (reason == null) {
            reason = extractReasonFromReply(modelReply, detail);
        }
        if (reason == null || looksLikeJson(reason)) {
            return buildFallbackReason(detail);
        }
        if (reason.length() > MAX_REASON_LENGTH) {
            return reason.substring(0, MAX_REASON_LENGTH).trim();
        }
        return reason;
    }

    private String firstReasonText(JsonNode... nodes) {
        for (JsonNode node : nodes) {
            if (node == null || node.isMissingNode() || node.isNull()) {
                continue;
            }
            String value = normalizeReasonNode(node);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String normalizeReasonNode(JsonNode node) {
        if (node.isTextual() || node.isNumber() || node.isBoolean()) {
            return normalizeText(node.asText(null));
        }
        if (node.isArray()) {
            List<String> parts = new ArrayList<>();
            for (JsonNode item : node) {
                String value = normalizeReasonNode(item);
                if (value != null) {
                    parts.add(value);
                }
            }
            return parts.isEmpty() ? null : String.join("；", parts);
        }
        if (node.isObject()) {
            List<String> parts = new ArrayList<>();
            for (JsonNode value : node) {
                String text = normalizeReasonNode(value);
                if (text != null) {
                    parts.add(text);
                }
            }
            return parts.isEmpty() ? null : String.join("；", parts);
        }
        return null;
    }

    private String extractReasonFromReply(String modelReply, LaptopDetailVO detail) {
        String reply = normalizeText(modelReply);
        if (reply == null || detail == null) {
            return null;
        }
        String model = normalizeText(detail.getModel());
        String brand = normalizeText(detail.getBrandName());
        List<String> anchors = new ArrayList<>();
        if (model != null) {
            anchors.add(model);
        }
        if (brand != null && model != null) {
            anchors.add(brand + " " + model);
        }
        for (String anchor : anchors) {
            int index = reply.indexOf(anchor);
            if (index < 0) {
                continue;
            }
            int start = reply.indexOf('：', index);
            if (start < 0) {
                start = reply.indexOf(':', index);
            }
            if (start < 0) {
                start = index + anchor.length();
            } else {
                start++;
            }
            int end = nextRecommendationBoundary(reply, start);
            return normalizeText(reply.substring(start, end));
        }
        return null;
    }

    private int nextRecommendationBoundary(String text, int start) {
        int end = text.length();
        for (String marker : List.of("\n1.", "\n2.", "\n3.", "\n4.", "\n5.", "\n6.", "\n7.", "\n8.", "\n9.", "\n10.")) {
            int index = text.indexOf(marker, start);
            if (index >= 0) {
                end = Math.min(end, index);
            }
        }
        return end;
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

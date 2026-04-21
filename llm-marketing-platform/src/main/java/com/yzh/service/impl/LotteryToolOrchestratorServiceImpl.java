package com.yzh.service.impl;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.yzh.mapper.MarketingActivityMapper;
import com.yzh.model.entity.MarketingActivity;
import com.yzh.model.vo.CreateMarketingActivityRequest;
import com.yzh.model.vo.CreateMarketingActivityResponse;
import com.yzh.model.vo.PrizeConfigRequest;
import com.yzh.service.LotteryPreheatService;
import com.yzh.service.LotteryToolOrchestratorService;
import com.yzh.service.MarketingActivityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
@RequiredArgsConstructor
public class LotteryToolOrchestratorServiceImpl implements LotteryToolOrchestratorService {

    private static final String DEFAULT_STYLE = "dark_neon";

    private final MarketingActivityService marketingActivityService;
    private final MarketingActivityMapper marketingActivityMapper;
    private final LotteryPreheatService lotteryPreheatService;

    @Override
    public Object execute(String merchantId, String toolName, JSONObject arguments) {
        if (toolName == null || toolName.isBlank()) {
            throw new IllegalArgumentException("toolName 不能为空");
        }
        if (arguments == null) {
            arguments = new JSONObject();
        }

        switch (toolName) {
            case "create_marketing_activity":
                return handleCreate(merchantId, arguments);
            case "publish_marketing_activity":
                return handlePublish(merchantId, arguments);
            default:
                throw new IllegalArgumentException("不支持的工具调用: " + toolName);
        }
    }

    private CreateMarketingActivityRequest normalizeCreateRequest(JSONObject arguments) {
        CreateMarketingActivityRequest req = new CreateMarketingActivityRequest();
        String rawPrompt = firstNonBlank(arguments, "_rawPrompt", "rawPrompt", "prompt", "userPrompt", "text");

        req.setActivityName(firstNonBlank(arguments, "activityName", "name", "title", "activity_title", "活动名称", "活动名"));
        req.setDeductPoints(firstBigDecimal(arguments, "deductPoints", "costPerDraw", "consumePoints", "pointCost", "每次抽奖消耗积分", "每次消耗积分", "消耗积分"));
        req.setInitialUserPoints(firstBigDecimal(arguments, "initialUserPoints", "initialPoints", "defaultPoints", "初始积分", "用户初始积分"));
        req.setPageStyle(firstNonBlank(arguments, "pageStyle", "style", "theme", "活动风格", "页面风格", "风格"));
        req.setStartTime(firstDateTime(arguments, "startTime", "startAt", "开始时间", "活动开始时间"));
        req.setEndTime(firstDateTime(arguments, "endTime", "endAt", "结束时间", "活动结束时间"));

        List<PrizeConfigRequest> prizes = parsePrizesFromArguments(arguments);
        if (prizes.isEmpty()) {
            prizes = parsePrizesFromRawPrompt(rawPrompt);
        }
        req.setPrizes(prizes);

        fillCoreFieldsFromRawPrompt(req, rawPrompt);
        req.setPageStyle(resolvePageStyle(req.getPageStyle()));
        return req;
    }

    private void fillCoreFieldsFromRawPrompt(CreateMarketingActivityRequest req, String rawPrompt) {
        if (rawPrompt == null || rawPrompt.isBlank()) {
            return;
        }

        if (isBlank(req.getActivityName())) {
            Matcher m = Pattern.compile("活动名称\\s*[:：]\\s*([^\\n]+)").matcher(rawPrompt);
            if (m.find()) {
                req.setActivityName(m.group(1).trim());
            }
        }

        if (req.getDeductPoints() == null) {
            Matcher m = Pattern.compile("(每次抽奖消耗积分|每次消耗积分|抽奖消耗积分|消耗积分)\\s*[:：]?\\s*([0-9]+(?:\\.[0-9]+)?)")
                    .matcher(rawPrompt);
            if (m.find()) {
                req.setDeductPoints(new BigDecimal(m.group(2)));
            }
        }

        if (isBlank(req.getPageStyle())) {
            Matcher m = Pattern.compile("(活动风格|页面风格|风格)\\s*[:：]?\\s*([a-z_]+)", Pattern.CASE_INSENSITIVE)
                    .matcher(rawPrompt);
            if (m.find()) {
                req.setPageStyle(m.group(2));
            }
        }

        if (req.getInitialUserPoints() == null) {
            Matcher m = Pattern.compile("(初始积分|用户初始积分|默认积分)\\s*[:：]?\\s*([0-9]+(?:\\.[0-9]+)?)").matcher(rawPrompt);
            if (m.find()) {
                req.setInitialUserPoints(new BigDecimal(m.group(2)));
            }
        }
    }
    private Object handleCreate(String merchantId, JSONObject arguments) {
        CreateMarketingActivityRequest createReq = normalizeCreateRequest(arguments);
        int prizeCount = createReq.getPrizes() == null ? 0 : createReq.getPrizes().size();
        log.info("[LotteryTool] normalized create request merchantId={}, activityName={}, prizeCount={}",
                merchantId, createReq.getActivityName(), prizeCount);

        CreateMarketingActivityResponse createRes = marketingActivityService.createActivity(merchantId, createReq);
        Map<String, Object> payload = new HashMap<>();
        payload.put("activityId", createRes.getActivityId());
        payload.put("activityName", createRes.getActivityName());
        payload.put("playLink", createRes.getPlayLink());
        payload.put("pageStyle", createRes.getPageStyle());
        payload.put("publishHint", createRes.getPublishHint());
        return payload;
    }
    private Object handlePublish(String merchantId, JSONObject arguments) {
        Long activityId = arguments.getLong("activityId");
        if (activityId == null || activityId <= 0) {
            throw new IllegalArgumentException("publish_marketing_activity 缺少 activityId");
        }

        MarketingActivity activity = marketingActivityMapper.selectById(activityId);
        if (activity == null) {
            throw new IllegalArgumentException("活动不存在");
        }
        if (!merchantId.equals(activity.getMerchantId())) {
            throw new IllegalArgumentException("该活动不属于当前商家");
        }

        lotteryPreheatService.preheat(activityId);
        activity.setStatus(1);
        activity.setUpdateTime(LocalDateTime.now());
        marketingActivityMapper.updateById(activity);

        Map<String, Object> payload = new HashMap<>();
        payload.put("activityId", activityId);
        payload.put("activityName", activity.getActivityName());
        payload.put("pageStyle", resolvePageStyle(activity.getPageStyle()));
        payload.put("playLink", "/lottery.html?activityId=" + activityId + "&style=" + resolvePageStyle(activity.getPageStyle()));
        payload.put("message", "活动已发布并预热完成");
        return payload;
    }

    private List<PrizeConfigRequest> parsePrizesFromArguments(JSONObject arguments) {
        List<PrizeConfigRequest> result = new ArrayList<>();
        JSONArray array = firstArray(arguments, "prizes", "awards", "prizeList", "奖品", "奖品列表");
        if (array == null) {
            return result;
        }

        for (Object item : array) {
            if (!(item instanceof JSONObject)) {
                continue;
            }
            JSONObject p = (JSONObject) item;
            PrizeConfigRequest prize = new PrizeConfigRequest();
            prize.setPrizeName(firstNonBlank(p, "prizeName", "prize", "name", "title", "label", "奖品名称", "奖品"));
            prize.setTotalStock(firstInteger(p, "totalStock", "stock", "count", "库存"));
            prize.setWeight(firstInteger(p, "weight", "awardRate", "rateWeight", "中奖权重", "权重"));
            prize.setProbability(firstDouble(p, "probability", "rate", "prob", "中奖概率", "概率"));
            prize.setPrizeType(firstInteger(p, "prizeType", "type", "奖品类型"));
            prize.setPrizeImage(firstNonBlank(p, "prizeImage", "icon", "image", "emoji", "奖品图标"));

            if (isBlank(prize.getPrizeName())) {
                continue;
            }
            if (prize.getTotalStock() == null) {
                prize.setTotalStock(0);
            }
            result.add(prize);
        }
        return result;
    }

    private List<PrizeConfigRequest> parsePrizesFromRawPrompt(String rawPrompt) {
        List<PrizeConfigRequest> parsed = new ArrayList<>();
        if (rawPrompt == null || rawPrompt.isBlank()) {
            return parsed;
        }

        Pattern inlinePattern = Pattern.compile(
                "奖品\\s*[:：]\\s*([^，,\\n]+?)\\s*[，,]\\s*库存\\s*[:：]?\\s*(\\d+)\\s*[，,]\\s*(概率|权重)\\s*[:：]?\\s*([0-9]+(?:\\.[0-9]+)?)",
                Pattern.CASE_INSENSITIVE
        );
        Matcher inlineMatcher = inlinePattern.matcher(rawPrompt);
        while (inlineMatcher.find()) {
            String name = inlineMatcher.group(1) == null ? null : inlineMatcher.group(1).trim();
            Integer stock = tryParseInt(inlineMatcher.group(2));
            String mode = inlineMatcher.group(3);
            BigDecimal value = tryParseDecimal(inlineMatcher.group(4));

            PrizeConfigRequest prize = buildPrizeFromRaw(name, stock, mode, value);
            if (prize != null) {
                parsed.add(prize);
            }
        }

        if (!parsed.isEmpty()) {
            return parsed;
        }

        Pattern linePattern = Pattern.compile(
                "(?m)^\\s*奖品\\s*[:：]\\s*([^，,\\n]+?)\\s*[，,]\\s*库存\\s*[:：]?\\s*(\\d+)\\s*[，,]\\s*(概率|权重)\\s*[:：]?\\s*([0-9]+(?:\\.[0-9]+)?)\\s*$"
        );
        Matcher lineMatcher = linePattern.matcher(rawPrompt);
        while (lineMatcher.find()) {
            String name = lineMatcher.group(1) == null ? null : lineMatcher.group(1).trim();
            Integer stock = tryParseInt(lineMatcher.group(2));
            String mode = lineMatcher.group(3);
            BigDecimal value = tryParseDecimal(lineMatcher.group(4));

            PrizeConfigRequest prize = buildPrizeFromRaw(name, stock, mode, value);
            if (prize != null) {
                parsed.add(prize);
            }
        }
        return parsed;
    }

    private PrizeConfigRequest buildPrizeFromRaw(String name, Integer stock, String mode, BigDecimal value) {
        if (isBlank(name)) {
            return null;
        }
        PrizeConfigRequest prize = new PrizeConfigRequest();
        prize.setPrizeName(name);
        prize.setTotalStock(stock == null ? 0 : stock);
        if ("概率".equals(mode)) {
            prize.setProbability(value == null ? null : value.doubleValue());
        } else {
            prize.setWeight(value == null ? null : value.intValue());
        }
        return prize;
    }

    private String resolvePageStyle(String style) {
        if (style == null) {
            return DEFAULT_STYLE;
        }
        String normalized = style.trim().toLowerCase(Locale.ROOT);
        if ("dark_neon".equals(normalized) || "ins_minimal".equals(normalized) || "fresh_light".equals(normalized)) {
            return normalized;
        }
        return DEFAULT_STYLE;
    }

    private String firstNonBlank(JSONObject obj, String... keys) {
        if (obj == null || keys == null) return null;
        for (String key : keys) {
            Object raw = obj.get(key);
            if (raw == null) continue;
            String s = String.valueOf(raw).trim();
            if (!s.isBlank()) return s;
        }
        return null;
    }

    private Integer firstInteger(JSONObject obj, String... keys) {
        BigDecimal bd = firstBigDecimal(obj, keys);
        return bd == null ? null : bd.intValue();
    }

    private Double firstDouble(JSONObject obj, String... keys) {
        BigDecimal bd = firstBigDecimal(obj, keys);
        return bd == null ? null : bd.doubleValue();
    }

    private BigDecimal firstBigDecimal(JSONObject obj, String... keys) {
        if (obj == null || keys == null) return null;
        for (String key : keys) {
            Object raw = obj.get(key);
            if (raw == null) continue;
            try {
                return new BigDecimal(String.valueOf(raw).trim());
            } catch (Exception ignore) {
                // ignore and try next key
            }
        }
        return null;
    }

    private LocalDateTime firstDateTime(JSONObject obj, String... keys) {
        String s = firstNonBlank(obj, keys);
        if (s == null) return null;
        return tryParseDateTime(s);
    }

    private JSONArray firstArray(JSONObject obj, String... keys) {
        if (obj == null || keys == null) return null;
        for (String key : keys) {
            JSONArray arr = obj.getJSONArray(key);
            if (arr != null) return arr;
            Object raw = obj.get(key);
            if (raw instanceof String) {
                try {
                    JSONArray parsed = JSONArray.parseArray((String) raw);
                    if (parsed != null) return parsed;
                } catch (Exception ignore) {
                    // continue
                }
            }
        }
        return null;
    }

    private LocalDateTime tryParseDateTime(String text) {
        if (text == null || text.isBlank()) return null;
        String v = text.trim();
        List<DateTimeFormatter> formatters = List.of(
                DateTimeFormatter.ISO_LOCAL_DATE_TIME,
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
                DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")
        );
        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDateTime.parse(v, formatter);
            } catch (DateTimeParseException ignore) {
                // continue
            }
        }
        return null;
    }

    private Integer tryParseInt(String text) {
        if (text == null || text.isBlank()) return null;
        try {
            return Integer.parseInt(text.trim());
        } catch (Exception ignore) {
            return null;
        }
    }

    private BigDecimal tryParseDecimal(String text) {
        if (text == null || text.isBlank()) return null;
        try {
            return new BigDecimal(text.trim());
        } catch (Exception ignore) {
            return null;
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
